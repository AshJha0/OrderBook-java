package com.orderbook.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Simulated order execution — step-by-step fills without touching the book.
 *
 * <p>Mirrors the Rust {@code OrderSimulation} struct.</p>
 */
public record OrderSimulation(
        @JsonProperty("fills")              List<long[]> fills,
        @JsonProperty("avg_price")          double avgPrice,
        @JsonProperty("total_filled")       long totalFilled,
        @JsonProperty("remaining_quantity") long remainingQuantity
) {
    public static OrderSimulation empty() {
        return new OrderSimulation(List.of(), 0.0, 0L, 0L);
    }

    public boolean isFullyFilled() { return remainingQuantity == 0; }
}
