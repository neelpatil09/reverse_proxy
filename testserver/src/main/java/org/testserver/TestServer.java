package org.testserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;

public class TestServer {

    private static Channel start(int port, EventLoopGroup boss, EventLoopGroup worker) throws InterruptedException {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new HttpServerCodec());
                        p.addLast(new HttpObjectAggregator(65536));
                        p.addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
                                String content;
                                switch (req.uri()) {
                                    case "/slow":
                                        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                                        content = "slow hello from " + port;
                                        break;
                                    case "/close":
                                        content = "closing from " + port;
                                        break;
                                    case "/large":
                                        int size = 1024 * 1024;
                                        byte[] bytes = new byte[size];
                                        for (int i = 0; i < size; i++) bytes[i] = 'A';
                                        content = new String(bytes);
                                        break;
                                    default:
                                        content = "hello world from " + port;
                                }
                                boolean keepAlive = HttpUtil.isKeepAlive(req) && !"/close".equals(req.uri());
                                FullHttpResponse resp = new DefaultFullHttpResponse(
                                        req.protocolVersion(),
                                        HttpResponseStatus.OK,
                                        ctx.alloc().buffer().writeBytes(content.getBytes())
                                );

                                resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8");
                                resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, resp.content().readableBytes());

                                if (keepAlive) {
                                    resp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                                    resp.headers().set(HttpHeaderNames.KEEP_ALIVE, "timeout=3000, max=10000");
                                    ctx.writeAndFlush(resp);
                                } else {
                                    resp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                                    ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
                                }
                            }
                        });
                    }
                });

        Channel ch = bootstrap.bind(port).sync().channel();
        System.out.println("Netty test server running on port " + port);
        return ch;
    }

    public static void main(String[] args) throws Exception {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();

        Channel ch1 = start(3000, boss, worker);
        Channel ch2 = start(3001, boss, worker);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down test servers...");
            ch1.close();
            ch2.close();
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }));

        System.out.println("Both servers up (3000, 3001). Press Ctrl+C to stop.");

        // Block until servers close
        ch1.closeFuture().sync();
        ch2.closeFuture().sync();
    }
}
