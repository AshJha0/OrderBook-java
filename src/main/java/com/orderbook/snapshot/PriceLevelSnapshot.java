package com.orderbook.snapshot;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Snapshot of a single price level for serialization / market data. */
public record PriceLevelSnapshot(
        @JsonProperty("price") long price,
        @JsonProperty("visible_quantity") long visibleQuantity,
        @JsonProperty("total_quantity") long totalQuantity,
        @JsonProperty("order_count") int orderCount
) {
    public static PriceLevelSnapshot of(long price, long visibleQty, long totalQty, int count) {
        return new PriceLevelSnapshot(price, visibleQty, totalQty, count);
    }
}
