package com.orderbook.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Emitted whenever a price level changes (order added, cancelled, matched, or updated).
 *
 * <p>{@link #engineSeq} is shared with {@link TradeResult#engineSeq} so
 * the combined stream across both listeners is strictly monotonic per book.</p>
 */
public final class PriceLevelChangedEvent {

    @JsonProperty("side")
    public final Side side;

    /** Price in raw ticks (unsigned long). */
    @JsonProperty("price")
    public final long price;

    /** Current total visible quantity at this price level (0 = level removed). */
    @JsonProperty("quantity")
    public final long quantity;

    @JsonProperty("engine_seq")
    public final long engineSeq;

    public PriceLevelChangedEvent(Side side, long price, long quantity, long engineSeq) {
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.engineSeq = engineSeq;
    }

    @Override
    public String toString() {
        return "PriceLevelChangedEvent{side=" + side + ", price=" + Long.toUnsignedString(price)
                + ", qty=" + quantity + ", seq=" + engineSeq + "}";
    }
}
