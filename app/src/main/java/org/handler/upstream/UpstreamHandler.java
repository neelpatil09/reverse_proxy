package org.handler.upstream;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.handler.common.ErrorResponseUtil;
import org.handler.common.RequestUtil;

import java.util.ArrayDeque;
import java.util.Queue;

public class UpstreamHandler extends ChannelInboundHandlerAdapter {

    private final Channel clientChannel;

    private final boolean keepAlive;

    private static final AttributeKey<Queue<HttpContent>> PENDING_CONTENT =
            AttributeKey.valueOf("pendingContent");

    public UpstreamHandler(Channel clientChannel, boolean keepAlive) {
        this.clientChannel = clientChannel;
        this.keepAlive = keepAlive;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if(msg instanceof HttpResponse resp){
            handleHttpResponse(resp);
        } else if (msg instanceof HttpContent content) {
            handleHttpContent(content, clientChannel);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private void handleHttpResponse(HttpResponse resp) {
        if (resp.status().codeClass() == HttpStatusClass.INFORMATIONAL) {
            clientChannel.writeAndFlush(resp);
            return;
        }

        RequestUtil.removeHopByHopHeaders(resp.headers());
        if(resp.protocolVersion().equals(HttpVersion.HTTP_1_0)) RequestUtil.upgradeToHTTP1_1(resp);
        clientChannel.attr(PENDING_CONTENT).set(new ArrayDeque<>());
        clientChannel.writeAndFlush(resp)
                .addListener((ChannelFutureListener) f -> {
                    if (!keepAlive) clientChannel.close();
                });

    }

    private void handleHttpContent(HttpContent content, Channel clientChannel) {

        if(!clientChannel.isActive()){
            ReferenceCountUtil.release(content);
            ErrorResponseUtil.sendBadClient(clientChannel);
            return;
        }

        Queue<HttpContent> contentQueue = clientChannel.attr(PENDING_CONTENT).getAndSet(null);
        if(contentQueue != null && !contentQueue.isEmpty()){
            contentQueue.add(content.retain());
            return;
        }

        clientChannel.writeAndFlush(content.retain())
                .addListener((ChannelFutureListener) f -> {
                    if (!keepAlive && content instanceof LastHttpContent) {
                        clientChannel.close();
                    }
                });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}