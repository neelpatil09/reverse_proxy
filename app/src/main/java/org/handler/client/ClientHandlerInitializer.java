package org.handler.client;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import org.handler.pool.ConnectionPool;

public class ClientHandlerInitializer extends ChannelInitializer<SocketChannel> {

    private final ConnectionPool pool;

    public ClientHandlerInitializer(ConnectionPool pool){
        this.pool = pool;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline().addLast(new HttpServerCodec());
        ch.pipeline().addLast(new ClientHandler(pool));
    }
}
