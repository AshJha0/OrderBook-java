package com.orderbook.model;

/**
 * Order lifecycle states, mirroring the Rust {@code OrderStatus} enum.
 */
public sealed interface OrderStatus
        permits OrderStatus.Open, OrderStatus.PartiallyFilled,
                OrderStatus.Filled, OrderStatus.Cancelled, OrderStatus.Rejected {

    /** Order is resting on the book; no fills yet. */
    record Open(TimestampMs timestamp) implements OrderStatus {}

    /** Order has been partially filled and is still resting. */
    record PartiallyFilled(long filledQuantity, long remainingQuantity, TimestampMs timestamp)
            implements OrderStatus {}

    /** Order is fully filled. */
    record Filled(long filledQuantity, TimestampMs timestamp) implements OrderStatus {}

    /** Order was cancelled before full fill. */
    record Cancelled(CancelReason reason, TimestampMs timestamp) implements OrderStatus {}

    /** Order was rejected during validation; never entered the book. */
    record Rejected(RejectReason reason, TimestampMs timestamp) implements OrderStatus {}
}
