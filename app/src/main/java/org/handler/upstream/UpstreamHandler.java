package org.handler.upstream;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.handler.codec.http.HttpVersion;
import org.handler.common.RequestUtil;

public class UpstreamHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    private final Channel clientChannel;

    private final boolean keepAlive;

    public UpstreamHandler(Channel clientChannel, boolean keepAlive) {
        this.clientChannel = clientChannel;
        this.keepAlive = keepAlive;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {

        if (msg.status().codeClass() == HttpStatusClass.INFORMATIONAL) {
            clientChannel.writeAndFlush(msg.retain());
            return;
        }

        RequestUtil.removeHopByHopHeaders(msg.headers());
        if(msg.protocolVersion().equals(HttpVersion.HTTP_1_0)) RequestUtil.upgradeToHTTP1_1(msg);

        clientChannel.writeAndFlush(msg.retain())
                .addListener((ChannelFutureListener) f -> {
                    if (!keepAlive) clientChannel.close();
                });

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}