package org.handler.common;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;

import java.net.InetSocketAddress;

public class RequestHeaderUtil {
    private static final CharSequence[] HOP_BY_HOP = {
            HttpHeaderNames.CONNECTION,
            HttpHeaderNames.KEEP_ALIVE,
            HttpHeaderNames.PROXY_AUTHENTICATE,
            HttpHeaderNames.PROXY_AUTHORIZATION,
            HttpHeaderNames.TE,
            HttpHeaderNames.TRAILER,
            HttpHeaderNames.TRANSFER_ENCODING,
            HttpHeaderNames.UPGRADE,
            "Proxy-Connection"
    };

    public static void removeHopByHopHeaders(HttpHeaders headers) {

        String connection = headers.get(HttpHeaderNames.CONNECTION);
        if (connection != null) {
            for (String conn : connection.split(",")) {
                headers.remove(conn.trim());
            }
            headers.remove(connection);
        }

        for (CharSequence h : HOP_BY_HOP) {
            headers.remove(h);
        }
    }

    public static void setForwardingHeaders(ChannelHandlerContext ctx, HttpHeaders headers) {
        String clientIp = ((InetSocketAddress) ctx.channel().remoteAddress())
                .getAddress().getHostAddress();

        headers.add("X-Forwarded-For", clientIp);
        headers.set("X-Forwarded-Proto", "http");
        if (!headers.contains("X-Forwarded-Host")) {
            headers.set("X-Forwarded-Host", headers.get(HttpHeaderNames.HOST));
        }
    }

    public static void sanitizeAndForwardHeaders(FullHttpRequest req, ChannelHandlerContext ctx) {
        HttpHeaders headers = req.headers();
        removeHopByHopHeaders(headers);
        setForwardingHeaders(ctx, headers);
    }

}
