package com.orderbook.risk;

/**
 * Source for the reference price used by the price-band risk check.
 */
public sealed interface ReferencePriceSource
        permits ReferencePriceSource.LastTrade,
                ReferencePriceSource.Mid,
                ReferencePriceSource.FixedPrice {

    /** Last executed trade price. Check skipped if no trade has occurred. */
    record LastTrade() implements ReferencePriceSource {}

    /**
     * Integer midpoint of best bid and ask.
     * Falls back to LastTrade when one-sided; skipped if neither is available.
     */
    record Mid() implements ReferencePriceSource {}

    /** Operator-pinned fixed reference price (raw ticks). Check always runs. */
    record FixedPrice(long price) implements ReferencePriceSource {}
}
