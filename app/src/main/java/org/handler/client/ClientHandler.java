package org.handler.client;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.handler.common.ErrorResponseUtil;
import org.handler.common.RequestUtil;
import org.handler.upstream.UpstreamHandlerInitializer;


import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;

public class ClientHandler extends ChannelInboundHandlerAdapter {

    private final String upstreamHost;

    private final int upstreamPort;

    private static final AttributeKey<Channel> OUTBOUND_CHANNEL_KEY =
            AttributeKey.valueOf("outboundChannel");

    private static final AttributeKey<Queue<HttpContent>> PENDING_CONTENT =
            AttributeKey.valueOf("pendingContent");

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
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws URISyntaxException {

        Channel inbound = ctx.channel();

        if(msg instanceof HttpRequest req){
            System.out.println("request" + req);
            handleHttpRequest(ctx, req, inbound);
        } else if (msg instanceof HttpContent content) {
            System.out.println("request" + content);
            handleHttpContent(content, inbound);
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest req, Channel inbound) throws URISyntaxException {
        boolean keepAlive = HttpUtil.isKeepAlive(req);
        HttpMethod method = req.method();
        if(!ACCEPTED_METHODS.contains(method)){
            ErrorResponseUtil.sendNotImplemented(inbound, method.name());
            return;
        }

        generateOriginFormURI(req, inbound);
        if(req.protocolVersion().equals(HttpVersion.HTTP_1_0)) RequestUtil.upgradeToHTTP1_1(req);
        RequestUtil.sanitizeAndForwardHeaders(req, ctx);

        inbound.attr(PENDING_CONTENT).set(new ArrayDeque<>());

        Bootstrap be = new Bootstrap()
                .group(inbound.eventLoop())
                .channel(NioSocketChannel.class)
                .handler(new UpstreamHandlerInitializer(inbound, keepAlive));

        be.connect(new InetSocketAddress(upstreamHost,upstreamPort))
                .addListener((ChannelFutureListener) cf -> {
                    if(!cf.isSuccess()){
                        ErrorResponseUtil.sendBadGateway(inbound, " Request could not find Upstream for " + upstreamHost + ":" + upstreamPort);
                        return;
                    }
                    Channel outbound = cf.channel();
                    req.headers().set(HttpHeaderNames.HOST, upstreamHost + ":" + upstreamPort);

                    outbound.writeAndFlush(req);

                    Queue<HttpContent> pendingContent = inbound.attr(PENDING_CONTENT).getAndSet(null);
                    if (pendingContent != null) {
                        while(!pendingContent.isEmpty()){
                            outbound.writeAndFlush(pendingContent.poll());
                        }
                    }

                    inbound.attr(OUTBOUND_CHANNEL_KEY).set(outbound);
                    inbound.closeFuture().addListener((ChannelFutureListener) f -> {
                        if (outbound.isActive()) outbound.close();
                    });
                });
    }

    private void handleHttpContent(HttpContent content, Channel inbound) {
        Channel outbound = inbound.attr(OUTBOUND_CHANNEL_KEY).get();
        if (outbound == null || !outbound.isActive()) {
            Queue<HttpContent> pendingContent = inbound.attr(PENDING_CONTENT).get();
            if (pendingContent != null) {
                pendingContent.add(content.retain());
            } else {
                ReferenceCountUtil.release(content);
                ErrorResponseUtil.sendBadGateway(inbound, " Chunked content lost Upstream for " + upstreamHost + ":" + upstreamPort);
            }
            return;
        }
        outbound.writeAndFlush(content.retain());
    }

    private void generateOriginFormURI(HttpRequest req, Channel inbound) throws URISyntaxException {
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

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}