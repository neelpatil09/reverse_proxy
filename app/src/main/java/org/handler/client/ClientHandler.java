package org.handler.client;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import org.handler.common.*;
import org.handler.pool.ConnectionPool;
import org.handler.upstream.UpstreamHandlerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class ClientHandler extends ChannelInboundHandlerAdapter {

    private final ConnectionPool pool;

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private static final Set<HttpMethod> ACCEPTED_METHODS = Set.of(
            HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
            HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.OPTIONS,
            HttpMethod.PATCH, HttpMethod.TRACE
    );

    public ClientHandler(ConnectionPool pool) {
        this.pool = pool;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws URISyntaxException {
        Channel inbound = ctx.channel();

        inbound.attr(Attributes.START_TIME).set(System.nanoTime());

        if(msg instanceof HttpRequest req){
            handleHttpRequest(ctx, req, inbound);
        } else if (msg instanceof HttpContent content) {
            handleHttpContent(content, inbound);
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest req, Channel client) throws URISyntaxException {
        if(Boolean.TRUE.equals(client.attr(Attributes.CLIENT_BUSY).get())){
            Metrics.recordError(client);
            ErrorResponseUtil.sendBadClient(client);
            ReferenceCountUtil.release(req);
            return;
        }

        if (!ACCEPTED_METHODS.contains(req.method())) {
            Metrics.recordError(client);
            ErrorResponseUtil.sendNotImplemented(client, req.method().name());
            ReferenceCountUtil.release(req);
            return;
        }
        generateOriginFormURI(req, client);
        client.attr(Attributes.CLIENT_BUSY).set(Boolean.TRUE);
        client.attr(Attributes.ERROR_FLAG).set(false);
        RequestUtil.sanitizeAndForwardHeaders(req, ctx);
        RequestUtil.upgradeToHTTP1_1(req);

        HostPort hp = HostPort.from(req);
        ConnectionPool.UpAddr addr = new ConnectionPool.UpAddr(hp.host, hp.port);

        client.attr(Attributes.CLIENT_KEEPALIVE).set(HttpUtil.isKeepAlive(req));

        Future<Channel> fut = pool.acquire(ctx, addr, () -> new Bootstrap()
                .group(client.eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.SO_BACKLOG, 65535)
                .handler(new UpstreamHandlerInitializer(pool))
                .connect(addr.toInet())
        );

        q(client).add(req);

        fut.addListener((Future<Channel> f) -> {
            Deque<HttpObject> pending = client.attr(Attributes.PENDING_CONTENT).getAndSet(null);
            if(!f.isSuccess()){
                ErrorResponseUtil.sendBadGateway(client,addr.toString());
                client.attr(Attributes.CLIENT_BUSY).set(Boolean.FALSE);
                if(pending != null){
                    for(HttpObject o : pending) ReferenceCountUtil.release(o);
                }
                Metrics.recordError(client);
                return;
            }

            Channel upstream = f.getNow();
            client.attr(Attributes.OUTBOUND_CHANNEL).set(upstream);
            HttpHeaders h = req.headers();
            h.set(HttpHeaderNames.HOST, addr.host() + ":" + addr.port());

            RequestUtil.removeHopByHopHeaders(req.headers());
            req.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

            upstream.eventLoop().execute(() -> {
                upstream.attr(Attributes.CLIENT_CHANNEL).set(client);
                while(!pending.isEmpty()){
                    upstream.write(pending.pollFirst());
                }
                upstream.flush();
            });
        });
    }

    private void handleHttpContent(HttpContent content, Channel client) {
        Channel upstream = client.attr(Attributes.OUTBOUND_CHANNEL).get();
        if (upstream != null && upstream.isActive()) {
            upstream.writeAndFlush(ReferenceCountUtil.retain(content));
        } else {
            q(client).add(ReferenceCountUtil.retain((HttpObject) content));
        }
    }

    private void generateOriginFormURI(HttpRequest req, Channel inbound) throws URISyntaxException {
        String uri = req.uri();
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            try {
                URI parsed = new URI(uri);

                String path = parsed.getRawPath();
                if (parsed.getRawQuery() != null) {
                    path += "?" + parsed.getRawQuery();
                }
                req.setUri(path);

            } catch (URISyntaxException e) {
                Metrics.recordError(inbound);
                ErrorResponseUtil.sendBadRequest(inbound, uri);
                ReferenceCountUtil.release(req);
            }
        }
    }

    private static Queue<HttpObject> q(Channel c) {
        Deque<HttpObject> d = c.attr(Attributes.PENDING_CONTENT).get();
        if (d == null) { d = new ArrayDeque<>(); c.attr(Attributes.PENDING_CONTENT).set(d); }
        return d;
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        Channel client = ctx.channel();
        Channel upstream = client.attr(Attributes.OUTBOUND_CHANNEL).get();
        if (upstream != null) {
            upstream.eventLoop().execute(() -> {
                upstream.config().setAutoRead(client.isWritable());
            });
            ctx.fireChannelWritabilityChanged();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Channel client = ctx.channel();
        Deque<HttpObject> q = client.attr(Attributes.PENDING_CONTENT).getAndSet(null);
        if (q != null) q.forEach(ReferenceCountUtil::release);
        Channel upstream = client.attr(Attributes.OUTBOUND_CHANNEL).getAndSet(null);
        if (upstream != null && upstream.isActive() &&  upstream.attr(Attributes.IN_USE).get()) {
            pool.discard(upstream);
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        Channel client = ctx.channel();
        Deque<HttpObject> q = client.attr(Attributes.PENDING_CONTENT).getAndSet(null);
        if (q != null) q.forEach(ReferenceCountUtil::release);

        Channel upstream = client.attr(Attributes.OUTBOUND_CHANNEL).getAndSet(null);
        if (upstream != null) pool.discard(upstream);
        ctx.close();
    }
}