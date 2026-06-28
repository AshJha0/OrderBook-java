package com.orderbook.model;

/**
 * A single price-level fill: one maker resting order matched against a taker.
 */
public record Transaction(
        Id tradeId,
        Id makerOrderId,
        Id takerOrderId,
        Price price,
        Quantity quantity,
        Side takerSide
) {
    /** Notional value: price × quantity (unsigned multiply). */
    public long notional() {
        return price.value() * quantity.value();
    }
}
