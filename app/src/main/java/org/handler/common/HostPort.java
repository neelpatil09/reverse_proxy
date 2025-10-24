package org.handler.common;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;

import java.net.URI;

public final class HostPort {
    public final String host;
    public final int port;
    public HostPort(String host, int port) { this.host = host; this.port = port; }

    public static HostPort from(HttpRequest req) {
        if (req.method().equals(HttpMethod.CONNECT)) {
            return parseAuthority(req.uri(), 443);
        }

        try {
            URI u = new URI(req.uri());
            if (u.getHost() != null) {
                int p = u.getPort();
                if (p < 0) p = defaultPort(u.getScheme());
                return new HostPort(u.getHost(), p);
            }
        } catch (Exception ignore) {
        }

        String hostHdr = req.headers().get("Host");
        if (hostHdr != null && !hostHdr.isEmpty()) {
            return parseAuthority(hostHdr, defaultPort(null));
        }

        throw new IllegalArgumentException("Missing host/port in request");
    }

    private static HostPort parseAuthority(String authority, int defaultPort) {
        if (authority.startsWith("[")) {
            int rb = authority.indexOf(']');
            if (rb > 0) {
                String host = authority.substring(1, rb);
                int port = defaultPort;
                if (rb + 1 < authority.length() && authority.charAt(rb + 1) == ':') {
                    port = parsePort(authority.substring(rb + 2), defaultPort);
                }
                return new HostPort(host, port);
            }
        }
        int colon = authority.lastIndexOf(':');
        if (colon > 0 && authority.indexOf(':') == colon) {
            String host = authority.substring(0, colon);
            int port = parsePort(authority.substring(colon + 1), defaultPort);
            return new HostPort(host, port);
        }
        return new HostPort(authority, defaultPort);
    }

    private static int defaultPort(String scheme) {
        if ("https".equalsIgnoreCase(scheme)) return 443;
        return 80;
    }

    private static int parsePort(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
