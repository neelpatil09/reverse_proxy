package org.handler;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import org.handler.client.ClientHandlerInitializer;
import org.handler.common.Metrics;
import org.handler.pool.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReverseProxy {

    private final int port;

    private final long maxIdleMs;

    private final long maxLifeMs;

    private static final Logger log = LoggerFactory.getLogger(ReverseProxy.class);

    public ReverseProxy(int port, long maxIdleMs, long maxLifeMs) {
        this.port = port;
        this.maxIdleMs = maxIdleMs;
        this.maxLifeMs = maxLifeMs;
    }

    public void run() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        DefaultEventLoopGroup poolGroup = new DefaultEventLoopGroup(1);
        EventExecutor poolExec = poolGroup.next();
        ConnectionPool pool = new ConnectionPool(poolExec, maxIdleMs, maxLifeMs);
        poolExec.scheduleAtFixedRate(pool::cleanup, 60, 60, TimeUnit.SECONDS);

        ScheduledExecutorService metricsLogger = Executors.newSingleThreadScheduledExecutor();
        metricsLogger.scheduleAtFixedRate(Metrics::dump, 15, 15, TimeUnit.SECONDS);

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ClientHandlerInitializer(pool))
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = b.bind(port).sync();
            log.info("Proxy started on port: {}", port);
            f.channel().closeFuture().sync();

        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            poolGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        new ReverseProxy(port, 300_000L, 600_000L).run();
    }
}
