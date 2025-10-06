package org.handler.pool;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.handler.common.Attributes;
import org.handler.common.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.function.Supplier;

public class ConnectionPool {

    /*
     * Private classes
     */
    private static class Bucket {
        final ArrayDeque<Channel> idle = new ArrayDeque<>();
        final ArrayDeque<PendingRequest> pending = new ArrayDeque<>();

        int active;
        int inFlight;

        public Bucket() {
            this.active = 0;
            this.inFlight = 0;
        }
    }

    private static class PendingRequest{
        final ChannelHandlerContext ctx;
        final Promise<Channel> ready;

        public PendingRequest(ChannelHandlerContext ctx, Promise<Channel> ready) {
            this.ctx = ctx;
            this.ready = ready;
        }
    }

    public record UpAddr(String host, int port) {
        public String key() {
            return host + ":" + port;
        }

        public InetSocketAddress toInet() {
            return new InetSocketAddress(host, port);
        }

        @Override
        public String toString() {
            return key();
        }
    }


    /*
     * ConnectionPool
     */
    private final HashMap<String, Bucket> buckets = new HashMap<>();
    private final EventExecutor poolExecutor;
    private final int maxConnections = 1000;
    private final Long maxIdle, maxLife;
    private static final Logger log = LoggerFactory.getLogger(ConnectionPool.class);

    public ConnectionPool(EventExecutor poolExec, Long maxIdle, Long maxLife) {
        this.poolExecutor = poolExec;
        this.maxIdle = maxIdle;
        this.maxLife = maxLife;
        log.info("Connection pool created. Max idle: {} ms, Max life: {} ms", maxIdle, maxLife);
    }

    public Future<Channel> acquire(ChannelHandlerContext ctx, UpAddr addr, Supplier<ChannelFuture> connector){
        final EventExecutor clientEL = ctx.executor();
        final Promise<Channel> promise = clientEL.newPromise();
        final String key = addr.key();

        poolExecutor.execute(() -> {
            Bucket b = buckets.computeIfAbsent(key, k -> new Bucket());

            /*
             * Check for idle connections
             */
            while(!b.idle.isEmpty()){
                Channel up = b.idle.pollFirst();
                if(up.isActive()){
                    up.eventLoop().execute(() -> {
                        checkoutChannel(up, ctx.channel());
                        clientEL.execute(() -> {
                            ctx.channel().attr(Attributes.REUSED_CONNECTION).set(true);
                            promise.setSuccess(up);
                        });
                    });
                    return;
                }
            }

            /*
             * Create new connection if possible
             */
            if(b.active + b.inFlight < maxConnections){
                b.active++; b.inFlight++;
                ChannelFuture cf = connector.get();
                cf.addListener((ChannelFutureListener) f -> {
                    poolExecutor.execute(() -> {
                        b.inFlight--;
                        if (!f.isSuccess()){
                            b.active--;
                            clientEL.execute(() -> promise.setFailure(f.cause()));
                            Metrics.recordError(ctx.channel());
                            return;
                        }
                        Channel up = f.channel();
                        registerCloseListener(up);

                        // oldest pending waiter, enqueue
                        while (!b.pending.isEmpty()) {
                            PendingRequest pend = b.pending.pollFirst();
                            Channel waiter = pend.ctx.channel();
                            if (waiter != null && waiter.isActive()) {
                                enqueuePending(key, ctx, promise);
                                up.eventLoop().execute(() ->
                                {
                                    checkoutNewChannel(up, key, waiter);
                                });
                                waiter.eventLoop().execute(() -> pend.ready.setSuccess(up));
                                Metrics.connectionCreated();
                                Metrics.pendingAdded();
                                Metrics.pendingRemoved();
                                return;
                            } else {
                                pend.ready.tryFailure(new ClosedChannelException());
                            }
                        }

                        // no pending waiters, match with caller
                        up.eventLoop().execute(() -> checkoutNewChannel(up, key, ctx.channel()));
                        clientEL.execute(() -> promise.setSuccess(up));
                        Metrics.connectionCreated();
                    });
                });
                return;
            }

            /*
             * Enqueue request
             */
            enqueuePending(key, ctx, promise);
            Metrics.pendingAdded();
        });

        return promise;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        poolExecutor.execute(() -> {
            for (var entry : buckets.entrySet()) {
                Bucket b = entry.getValue();

                Iterator<Channel> it = b.idle.iterator();
                while (it.hasNext()) {
                    Channel up = it.next();

                    Long lastUsed = up.attr(Attributes.LAST_USED).get();
                    Long firstCreated = up.attr(Attributes.FIRST_CREATED).get();
                    if(lastUsed == null || firstCreated == null) {
                        continue;
                    }

                    boolean expiredIdle = (now - lastUsed) > maxIdle;
                    boolean expiredLife = (now - firstCreated) > maxLife;

                    boolean kill = !up.isActive() || expiredIdle || expiredLife;

                    if (kill) {
                        it.remove();
                        up.eventLoop().execute(() -> {
                            if (up.isOpen()) {
                                up.close();
                            }
                        });
                    }
                }
            }
        });
    }

    public void release (Channel up){
        up.eventLoop().execute(() -> {
            long now = System.currentTimeMillis();
            Long created = up.attr(Attributes.FIRST_CREATED).get();
            if (!up.isActive()
                    || created == null
                    || now - created > maxLife
                    || Boolean.FALSE.equals(up.attr(Attributes.UPSTREAM_KEEPALIVE).get())
            ) {
                discard(up); return;
            }
            final String key = up.attr(Attributes.UPSTREAM_KEY).get();
            poolExecutor.execute(() -> {
                Bucket b = buckets.get(key);
                if (b == null) { up.eventLoop().execute(up::close); return; }
                while(!b.pending.isEmpty()){
                    PendingRequest pr = b.pending.poll();
                    Channel client = pr.ctx.channel();
                    if(client != null && client.isActive()){
                        up.eventLoop().execute(() -> {
                            checkoutChannel(up, client);
                            client.eventLoop().execute(() -> pr.ready.setSuccess(up));
                        });
                        Metrics.pendingRemoved();
                        return;
                    }
                    else{
                        pr.ready.tryFailure(new ClosedChannelException());
                        Metrics.recordError(client);
                    }
                }
                up.eventLoop().execute(() -> sanitizeForReuse(up));
                b.idle.addLast(up);
            });
        });
    }

    public void discard(Channel up) {
        up.eventLoop().execute(() -> {
            if (up.isOpen()) up.close();
        });
    }


    private void removePendingIfPresent(String key, Promise<Channel> p) {
        Bucket b = buckets.get(key);
        if (b == null) return;
        Iterator<PendingRequest> it = b.pending.iterator();
        while (it.hasNext()) {
            PendingRequest pd = it.next();
            if (pd.ready == p) { it.remove(); p.tryFailure(new ClosedChannelException()); break; }
        }
    }

    public void checkoutNewChannel(Channel up, String upstreamKey, Channel client) {
        Long now = System.currentTimeMillis();
        up.attr(Attributes.FIRST_CREATED).set(now);
        up.attr(Attributes.UPSTREAM_KEEPALIVE).set(true);
        up.attr(Attributes.UPSTREAM_KEY).set(upstreamKey);
        checkoutChannel(up, client);
    }

    public void checkoutChannel(Channel up, Channel client) {
        client.eventLoop().execute(() -> {
            boolean clientKA = Boolean.TRUE.equals(client.attr(Attributes.KEEP_ALIVE).get());
            up.eventLoop().execute(() -> {
                up.attr(Attributes.CLIENT_CHANNEL).set(client);
                up.attr(Attributes.CLIENT_KEEPALIVE).set(clientKA);
                up.attr(Attributes.LAST_USED).set(System.currentTimeMillis());
                up.attr(Attributes.IN_USE).set(true);
            });
        });
    }

    private void sanitizeForReuse(Channel up){
        up.attr(Attributes.CLIENT_CHANNEL).set(null);
        up.attr(Attributes.CLIENT_KEEPALIVE).set(null);
        up.attr(Attributes.LAST_USED).set(System.currentTimeMillis());
        up.attr(Attributes.IN_USE).set(false);
    }

    private void enqueuePending(String key, ChannelHandlerContext ctx, Promise<Channel> promise) {
        Bucket b = buckets.get(key);
        if (b == null) {
            promise.setFailure(new ClosedChannelException());
            return;
        }
        b.pending.addLast(new PendingRequest(ctx, promise));
        ctx.channel().closeFuture().addListener(f ->
                poolExecutor.execute(() -> removePendingIfPresent(key, promise))
        );
    }

    private void registerCloseListener(Channel up) {
        up.closeFuture().addListener(f -> poolExecutor.execute(() -> {
            String key = up.attr(Attributes.UPSTREAM_KEY).get();
            Bucket b = buckets.get(key);
            if (b == null){
                return;
            }
            b.active = Math.max(0, b.active - 1);
            Metrics.connectionClosed();
        }));
    }


}
