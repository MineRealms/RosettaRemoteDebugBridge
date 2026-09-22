package com.rosetta.remotedebugbridge.debug;

/**
 * Simple per-second rate limiter shared by both sides of a CRD session.
 */
public final class CrdRateLimiter {
    private final int limit;
    private long windowStart;
    private int count;

    public CrdRateLimiter(int messagesPerSecond) {
        this.limit = Math.max(1, messagesPerSecond);
        this.windowStart = System.currentTimeMillis();
    }

    public synchronized boolean allow() {
        long now = System.currentTimeMillis();
        if (now - this.windowStart >= 1000L) {
            this.windowStart = now;
            this.count = 0;
        }
        return ++this.count <= this.limit;
    }
}
