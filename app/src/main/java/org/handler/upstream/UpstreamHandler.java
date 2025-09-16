package org.handler.upstream;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpUtil;

public class UpstreamHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    private final Channel clientChannel;

    private final boolean keepAlive;

    public UpstreamHandler(Channel clientChannel, boolean keepAlive) {
        this.clientChannel = clientChannel;
        this.keepAlive = keepAlive;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
        if (keepAlive) HttpUtil.setKeepAlive(msg, true);
        clientChannel.writeAndFlush(msg.retain())
                .addListener((ChannelFutureListener) f -> {
                    if (!keepAlive) clientChannel.close();
                });

    }
}