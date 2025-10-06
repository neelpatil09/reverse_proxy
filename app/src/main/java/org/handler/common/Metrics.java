package org.handler.common;

import io.netty.channel.Channel;

import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class Metrics {

    private static final LongAdder totalRequests = new LongAdder();
    private static final LongAdder reusedConnections = new LongAdder();
    private static final LongAdder createdConnections = new LongAdder();
    private static final LongAdder closedConnections = new LongAdder();
    private static final LongAdder totalLatencyNanos = new LongAdder();
    private static final AtomicInteger activeConnections = new AtomicInteger();
    private static final LongAdder errorCount = new LongAdder();
    private static final LongAdder pendingAdded = new LongAdder();
    private static final LongAdder pendingRemoved = new LongAdder();

    // Timestamps for rate calculation
    private static final AtomicLong lastDumpTime = new AtomicLong(System.nanoTime());
    private static final AtomicLong lastDumpTotalRequests = new AtomicLong();

    private Metrics() {}

    // === Connection lifecycle ===
    public static void connectionCreated() {
        createdConnections.increment();
        activeConnections.incrementAndGet();
    }

    public static void connectionClosed() {
        closedConnections.increment();
        activeConnections.decrementAndGet();
    }

    public static void connectionReused() {
        reusedConnections.increment();
    }

    // === Requests ===
    public static void recordRequest(long latencyNanos, boolean reused) {
        totalRequests.increment();
        totalLatencyNanos.add(latencyNanos);
        if (reused) reusedConnections.increment();
    }

    public static void recordError(Channel client) {
        Boolean flagged = client.attr(Attributes.ERROR_FLAG).get();
        if (flagged != null && !flagged) {
            client.attr(Attributes.ERROR_FLAG).set(true);
            errorCount.increment();
        }
    }

    // === Pending connections ===
    public static void pendingAdded() {
        pendingAdded.increment();
    }

    public static void pendingRemoved() {
        pendingRemoved.increment();
    }

    // === Periodic dump ===
    public static void dump() {
        long totalReq = totalRequests.sum();
        long reused = reusedConnections.sum();
        long created = createdConnections.sum();
        long closed = closedConnections.sum();
        long active = activeConnections.get();
        long err = errorCount.sum();
        long pendingAppended = pendingAdded.sum();
        long pendingPopped = pendingRemoved.sum();


        double avgLatencyMs = totalReq > 0
                ? totalLatencyNanos.sum() / (double) totalReq / 1_000_000
                : 0.0;

        long now = System.nanoTime();
        long lastTime = lastDumpTime.getAndSet(now);
        long prevReq = lastDumpTotalRequests.getAndSet(totalReq);
        double seconds = (now - lastTime) / 1_000_000_000.0;
        double rps = (totalReq - prevReq) / seconds;

        System.out.printf("""
        [Metrics]
          RPS: %.2f
          Active: %d | Created: %d | Closed: %d | Reused: %d
          TotalReq: %d | Errors: %d | AvgLatency: %.2f ms | Pending(Appended: %d, Popped: %d)
        """, rps, active, created, closed, reused, totalReq, err, avgLatencyMs, pendingAppended, pendingPopped);
    }
}
