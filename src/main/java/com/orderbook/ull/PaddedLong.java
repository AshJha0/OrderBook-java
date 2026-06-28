package com.orderbook.ull;

/**
 * A mutable long padded to exactly one cache line (64 bytes) to prevent
 * false sharing between the sequence counter and any adjacent field.
 *
 * Layout:  8 bytes value  +  56 bytes padding  = 64 bytes total.
 *
 * Usage: cache-line-isolate the Disruptor sequence, bestBid, bestAsk, and
 * the engine sequence counter so concurrent readers on the outbound path
 * never cause write-invalidations on the single writer's core.
 */
@SuppressWarnings("unused")
public final class PaddedLong {
    // 7 longs of pre-padding so the value field falls on its own cache line
    // regardless of object header alignment.
    long p1, p2, p3, p4, p5, p6, p7;
    public volatile long value;
    // 7 longs of post-padding
    long q1, q2, q3, q4, q5, q6, q7;

    public PaddedLong(long initial) { this.value = initial; }
    public PaddedLong()             { this.value = 0L; }
}
