package org.handler.client;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import org.handler.upstream.UpstreamHandlerInitializer;


import java.net.InetSocketAddress;

public class ClientHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final String upstreamHost;

    private final int upstreamPort;

    public ClientHandler(String upstreamHost, int upstreamPort) {
        this.upstreamHost = upstreamHost;
        this.upstreamPort = upstreamPort;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req){
        Channel inbound = ctx.channel();
        boolean keepAlive = HttpUtil.isKeepAlive(req);

        Bootstrap be = new Bootstrap()
                .group(inbound.eventLoop())
                .channel(NioSocketChannel.class)
                .handler(new UpstreamHandlerInitializer(inbound, keepAlive));

        FullHttpRequest copy = req.retain();
        be.connect(new InetSocketAddress(upstreamHost,upstreamPort))
                .addListener((ChannelFutureListener) cf -> {
                    if(!cf.isSuccess()){
                        sendBadGateway(inbound, "Connection to upstream failed");
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

    private void sendBadGateway(Channel ch, String msg) {
        if (!ch.isActive()) return;
        FullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.BAD_GATEWAY,
                Unpooled.wrappedBuffer(("Bad Gateway: " + msg).getBytes())
        );
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8");
        resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, resp.content().readableBytes());
        ch.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}