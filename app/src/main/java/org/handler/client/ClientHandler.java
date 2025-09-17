package org.handler.client;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import org.handler.common.ErrorResponseUtil;
import org.handler.common.RequestUtil;
import org.handler.upstream.UpstreamHandlerInitializer;


import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

public class ClientHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final String upstreamHost;

    private final int upstreamPort;

    private static final Set<HttpMethod> ACCEPTED_METHODS = Set.of(
            HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
            HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.OPTIONS,
            HttpMethod.PATCH, HttpMethod.TRACE
    );

    public ClientHandler(String upstreamHost, int upstreamPort) {
        this.upstreamHost = upstreamHost;
        this.upstreamPort = upstreamPort;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws URISyntaxException {
        Channel inbound = ctx.channel();
        boolean keepAlive = HttpUtil.isKeepAlive(req);
        HttpMethod method = req.method();

        if(!ACCEPTED_METHODS.contains(method)){
            ErrorResponseUtil.sendNotImplemented(inbound, method.name());
            return;
        }

        if(HttpUtil.is100ContinueExpected(req)){
            ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
        }

        generateOriginFormURI(req, inbound);
        if(req.protocolVersion().equals(HttpVersion.HTTP_1_0)) RequestUtil.upgradeToHTTP1_1(req);
        RequestUtil.sanitizeAndForwardHeaders(req, ctx);

        Bootstrap be = new Bootstrap()
                .group(inbound.eventLoop())
                .channel(NioSocketChannel.class)
                .handler(new UpstreamHandlerInitializer(inbound, keepAlive));

        FullHttpRequest copy = req.retain();
        be.connect(new InetSocketAddress(upstreamHost,upstreamPort))
                .addListener((ChannelFutureListener) cf -> {
                    if(!cf.isSuccess()){
                        ErrorResponseUtil.sendBadGateway(inbound, upstreamHost + ":" + upstreamPort);
                        return;
                    }
                    Channel outbound = cf.channel();
                    copy.headers().set(HttpHeaderNames.HOST, upstreamHost + ":" + upstreamPort);
                    outbound.writeAndFlush(copy);
                    inbound.closeFuture().addListener((ChannelFutureListener) f -> {
                        if (outbound.isActive()) outbound.close();
                    });
                });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    private void generateOriginFormURI(FullHttpRequest req, Channel inbound) throws URISyntaxException {
        String uri = req.uri();
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            try {
                URI parsed = new URI(uri);

                String path = parsed.getRawPath();
                if (parsed.getRawQuery() != null) {
                    path += "?" + parsed.getRawQuery();
                }
                req.setUri(path);

                String hostHeader = parsed.getHost() +
                        (parsed.getPort() != -1 ? ":" + parsed.getPort() : "");
                req.headers().set(HttpHeaderNames.HOST, hostHeader);

            } catch (URISyntaxException e) {
                ErrorResponseUtil.sendBadRequest(inbound, uri);
            }
        }

    }
}