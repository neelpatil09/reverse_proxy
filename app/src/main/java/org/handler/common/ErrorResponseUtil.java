package org.handler.common;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.*;

public final class ErrorResponseUtil {

    public static final String BAD_GATEWAY_PREFIX = "Bad Gateway: ";
    public static final String BAD_REQUEST_PREFIX = "Bad Request: ";
    public static final String CONTENT_TYPE_TEXT = "text/plain; charset=utf-8";
    public static final String NOT_IMPLEMENTED_PREFIX = "Not Implemented: ";


    public ErrorResponseUtil() {}

    public static void sendBadGateway(Channel ch, String gateway) {
        sendError(ch, HttpResponseStatus.BAD_GATEWAY, BAD_GATEWAY_PREFIX + gateway);
    }

    public static void sendBadRequest(Channel ch, String uri) {
        sendError(ch, HttpResponseStatus.BAD_REQUEST, BAD_REQUEST_PREFIX + uri);
    }

    public static void sendNotImplemented(Channel ch, String method) {
        sendError(ch, HttpResponseStatus.NOT_IMPLEMENTED, NOT_IMPLEMENTED_PREFIX + method);
    }

    private static void sendError(Channel ch, HttpResponseStatus status, String msg) {
        if (!ch.isActive()) return;
        FullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(msg.getBytes())
        );
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, CONTENT_TYPE_TEXT);
        resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, resp.content().readableBytes());
        ch.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
    }

}