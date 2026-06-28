package com.orderbook.model;

/**
 * Pluggable timestamp source. Inject a {@link StubClock} for deterministic tests.
 */
public interface Clock {
    /** Current time in milliseconds. */
    long nowMs();

    /** Wall-clock implementation. */
    class MonotonicClock implements Clock {
        public static final MonotonicClock INSTANCE = new MonotonicClock();
        @Override public long nowMs() { return System.currentTimeMillis(); }
    }

    /** Fixed-time stub for reproducible tests. */
    class StubClock implements Clock {
        private volatile long time;
        public StubClock(long initialMs) { this.time = initialMs; }
        public void setTime(long ms) { this.time = ms; }
        public void advance(long ms) { this.time += ms; }
        @Override public long nowMs() { return time; }
    }
}
