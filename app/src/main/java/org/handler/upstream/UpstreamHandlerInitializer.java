package org.handler.upstream;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;

public class UpstreamHandlerInitializer extends ChannelInitializer<SocketChannel> {

    private final Channel clientChannel;

    private final boolean keepAlive;

    public  UpstreamHandlerInitializer(Channel clientChannel, boolean keepAlive) {
        this.clientChannel = clientChannel;
        this.keepAlive = keepAlive;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline().addLast(new HttpClientCodec());
        ch.pipeline().addLast(new UpstreamHandler(clientChannel, keepAlive));
    }
}
