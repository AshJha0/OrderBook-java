package com.orderbook.ull;

/**
 * A single maker-taker fill stored as flat primitives.
 * Pre-allocated in a pool; reset and reused on every order.
 *
 * No objects, no boxing — the hot path never touches the heap for fills.
 */
public final class MutableFill {

    public long makerIdHigh;
    public long makerIdLow;
    public long takerIdHigh;
    public long takerIdLow;
    public long filledQty;
    public long price;
    public long makerFee;
    public long takerFee;

    // maker user (for STP record-keeping / audit)
    public long makerU0, makerU1, makerU2, makerU3;

    public void reset() {
        makerIdHigh = makerIdLow = takerIdHigh = takerIdLow = 0L;
        filledQty   = price = makerFee = takerFee = 0L;
        makerU0 = makerU1 = makerU2 = makerU3 = 0L;
    }
}
