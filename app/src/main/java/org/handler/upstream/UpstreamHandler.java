package org.handler.upstream;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.handler.common.Attributes;
import org.handler.common.Metrics;
import org.handler.common.RequestUtil;
import org.handler.pool.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpstreamHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(UpstreamHandler.class);

    private final ConnectionPool pool;

    public UpstreamHandler(ConnectionPool p) {
        this.pool = p;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Channel up = ctx.channel();
        Channel clientChannel = up.attr(Attributes.CLIENT_CHANNEL).get();

        if(clientChannel == null || !clientChannel.isActive()){
            ReferenceCountUtil.release(msg);
            pool.discard(up);
            return;
        }

        if (msg instanceof HttpResponse resp) {
            RequestUtil.upgradeToHTTP1_1(resp);
            RequestUtil.removeHopByHopHeaders(resp.headers());
            boolean upKA = HttpUtil.isKeepAlive(resp);
            up.attr(Attributes.UPSTREAM_KEEPALIVE).set(upKA);
        }

        clientChannel.writeAndFlush(ReferenceCountUtil.retain(msg))
                .addListener((ChannelFutureListener) f -> {
                    if (!f.isSuccess()) {
                        if (up.isActive()) pool.discard(up);
                        if (clientChannel.isActive()) clientChannel.close();
                        Metrics.recordError(clientChannel);
                        return;
                    }
                    Long startTime = clientChannel.attr(Attributes.START_TIME).getAndSet(null);
                    Boolean reused = clientChannel.attr(Attributes.REUSED_CONNECTION).getAndSet(null);
                    if (startTime != null) {
                        long latency = System.nanoTime() - startTime;
                        Metrics.recordRequest(latency, reused != null && reused);
                    }

                    if (!(msg instanceof LastHttpContent)) {
                        return;
                    }

                    clientChannel.attr(Attributes.CLIENT_BUSY).set(Boolean.FALSE);

                    boolean upstreamKA = Boolean.TRUE.equals(up.attr(Attributes.UPSTREAM_KEEPALIVE).get());
                    if (upstreamKA) pool.release(up); else pool.discard(up);

                    clientChannel.attr(Attributes.OUTBOUND_CHANNEL).set(null);

                    boolean clientKA = Boolean.TRUE.equals(clientChannel.attr(Attributes.CLIENT_KEEPALIVE).get());
                    if (!(clientKA && upstreamKA)) {
                        clientChannel.close();
                    }
                });

        ReferenceCountUtil.release(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Channel upstream = ctx.channel();
        Channel client = upstream.attr(Attributes.CLIENT_CHANNEL).get();
        pool.discard(upstream);
        if (client != null && client.isActive()) {
            client.eventLoop().execute(client::close);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        channelInactive(ctx);
    }
}