package org.handler.common;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpObject;
import io.netty.util.AttributeKey;

import java.util.Deque;

public class Attributes {

    /*
        * Attribute Keys for Client Channels
     */
    public static final AttributeKey<Channel> OUTBOUND_CHANNEL =
            AttributeKey.valueOf("outboundChannel");

    public static final AttributeKey<Deque<HttpObject>> PENDING_CONTENT =
            AttributeKey.valueOf("pendingContent");

    public static final AttributeKey<Boolean> KEEP_ALIVE =
            AttributeKey.valueOf("keepAlive");

    public static final AttributeKey<Boolean> CLIENT_BUSY =
            AttributeKey.valueOf("clientBusy");

    public static final AttributeKey<Boolean> REUSED_CONNECTION =
            AttributeKey.valueOf("reusedConnection");

    public static final AttributeKey<Long> START_TIME =
            AttributeKey.valueOf("startTime");

    public static final AttributeKey<Boolean> ERROR_FLAG = AttributeKey.valueOf("errorFlag");



    /*
        * Attribute Keys for Upstream Channels
     */

    // Set on first creation and when upstream wants to close connection
    public static final AttributeKey<Long> FIRST_CREATED =
            AttributeKey.valueOf("firstCreated");

    public static final AttributeKey<Boolean> UPSTREAM_KEEPALIVE =
            AttributeKey.valueOf("upstreamKA");

    public static final AttributeKey<String> UPSTREAM_KEY =
            AttributeKey.valueOf("upstreamKey");

    // Set when checking out channel to a client
    public static final AttributeKey<Channel> CLIENT_CHANNEL =
            AttributeKey.valueOf("clientChannel");

    public static final AttributeKey<Boolean> CLIENT_KEEPALIVE =
            AttributeKey.valueOf("clientKA");

    public static final AttributeKey<Long> LAST_USED =
            AttributeKey.valueOf("lastUsed");

    public static final AttributeKey<Boolean> IN_USE =
            AttributeKey.valueOf("inUse");

}
