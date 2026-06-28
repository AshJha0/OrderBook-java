package com.orderbook.statistics;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A price-range bucket for depth distribution analysis.
 *
 * <p>Mirrors the Rust {@code DistributionBin} struct.</p>
 */
public record DistributionBin(
        @JsonProperty("min_price")   long minPrice,
        @JsonProperty("max_price")   long maxPrice,
        @JsonProperty("volume")      long volume,
        @JsonProperty("level_count") int levelCount
) {
    /** Midpoint price of this bin (overflow-safe). */
    public long midpoint() {
        return minPrice + (maxPrice - minPrice) / 2;
    }

    /** Width of this bin in price units. */
    public long width() {
        return maxPrice - minPrice;
    }
}
