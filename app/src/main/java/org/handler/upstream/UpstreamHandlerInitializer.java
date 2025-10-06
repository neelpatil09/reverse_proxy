package org.handler.upstream;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import org.handler.pool.ConnectionPool;

public class UpstreamHandlerInitializer extends ChannelInitializer<SocketChannel> {

    private final ConnectionPool pool;

    public UpstreamHandlerInitializer(ConnectionPool pool) {
        this.pool = pool;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline().addLast(new HttpClientCodec());
        ch.pipeline().addLast(new UpstreamHandler(pool));
    }
}
