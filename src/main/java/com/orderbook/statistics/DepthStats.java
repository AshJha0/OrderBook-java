package com.orderbook.statistics;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.orderbook.model.Side;
import com.orderbook.snapshot.PriceLevelSnapshot;

import java.util.List;

/**
 * Aggregate depth statistics for one side of the order book.
 *
 * <p>Mirrors the Rust {@code DepthStats} struct.</p>
 */
public record DepthStats(
        @JsonProperty("total_volume")       long totalVolume,
        @JsonProperty("levels_count")       int levelsCount,
        @JsonProperty("avg_level_size")     double avgLevelSize,
        @JsonProperty("weighted_avg_price") double weightedAvgPrice,
        @JsonProperty("min_level_size")     long minLevelSize,
        @JsonProperty("max_level_size")     long maxLevelSize,
        @JsonProperty("std_dev_level_size") double stdDevLevelSize
) {
    public static DepthStats zero() {
        return new DepthStats(0L, 0, 0.0, 0.0, 0L, 0L, 0.0);
    }

    public boolean isEmpty() { return levelsCount == 0 || totalVolume == 0; }

    /** Compute statistics from a list of price-level snapshots. */
    public static DepthStats compute(List<PriceLevelSnapshot> levels) {
        if (levels == null || levels.isEmpty()) return zero();

        long total = 0L;
        long min = Long.MAX_VALUE, max = 0L;
        double wap = 0.0;

        for (PriceLevelSnapshot l : levels) {
            long qty = l.visibleQuantity();
            total += qty;
            if (qty < min) min = qty;
            if (qty > max) max = qty;
            wap += (double) l.price() * qty;
        }

        double avg = (double) total / levels.size();
        double wapFinal = total > 0 ? wap / total : 0.0;

        double variance = 0.0;
        for (PriceLevelSnapshot l : levels) {
            double diff = l.visibleQuantity() - avg;
            variance += diff * diff;
        }
        double stdDev = Math.sqrt(variance / levels.size());

        return new DepthStats(total, levels.size(), avg, wapFinal, min, max, stdDev);
    }
}
