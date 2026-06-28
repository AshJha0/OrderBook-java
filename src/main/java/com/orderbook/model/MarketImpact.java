package com.orderbook.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Market impact analysis — how a large order would affect the book.
 *
 * <p>Mirrors the Rust {@code MarketImpact} struct.</p>
 */
public record MarketImpact(
        @JsonProperty("avg_price")               double avgPrice,
        @JsonProperty("worst_price")             long worstPrice,
        @JsonProperty("slippage")                long slippage,
        @JsonProperty("slippage_bps")            double slippageBps,
        @JsonProperty("levels_consumed")         int levelsConsumed,
        @JsonProperty("total_quantity_available") long totalQuantityAvailable
) {
    public static MarketImpact empty() {
        return new MarketImpact(0.0, 0L, 0L, 0.0, 0, 0L);
    }

    /** True if the book has enough liquidity to fill the requested quantity. */
    public boolean canFill(long requestedQty) {
        return Long.compareUnsigned(totalQuantityAvailable, requestedQty) >= 0;
    }

    /** Ratio of available depth to requested quantity. */
    public double fillRatio(long requestedQty) {
        if (requestedQty == 0) return 0.0;
        return (double) totalQuantityAvailable / requestedQty;
    }
}
