package com.orderbook.ull;

/**
 * Disruptor ring-buffer event for outbound trade notifications.
 *
 * The handler thread copies essential fields from {@link MutableMatchResult}
 * into this event before publishing it; downstream consumers (NATS, risk,
 * logging) read from their own ring-buffer slots without contending with the
 * matching thread.
 *
 * Fill data is stored in a flat {@code long[]} array to avoid heap allocation.
 * Layout per fill (stride = 8 longs):
 *   [0] makerIdHigh  [1] makerIdLow  [2] takerIdHigh  [3] takerIdLow
 *   [4] filledQty    [5] price       [6] makerFee      [7] takerFee
 */
public final class TradeOutEvent {

    public static final int MAX_FILLS  = 256;
    public static final int FILL_STRIDE = 8;

    public long takerIdHigh;
    public long takerIdLow;
    public long takerPrice;
    public byte takerSide;
    public long engineSeq;
    public long executedQty;
    public long remainingQty;
    public long totalMakerFee;
    public long totalTakerFee;
    public boolean complete;
    public int fillCount;

    public final long[] fillData = new long[MAX_FILLS * FILL_STRIDE];

    public static final com.lmax.disruptor.EventFactory<TradeOutEvent> FACTORY = TradeOutEvent::new;

    /** Copy essential data from a MutableMatchResult into this slot. */
    public void copyFrom(MutableMatchResult r) {
        takerIdHigh  = r.takerIdHigh;
        takerIdLow   = r.takerIdLow;
        takerPrice   = r.takerPrice;
        takerSide    = r.takerSide;
        engineSeq    = r.engineSeq;
        executedQty  = r.executedQty;
        remainingQty = r.remainingQty;
        totalMakerFee = r.totalMakerFee;
        totalTakerFee = r.totalTakerFee;
        complete     = r.complete;
        fillCount    = Math.min(r.fillCount, MAX_FILLS);

        for (int i = 0; i < fillCount; i++) {
            MutableFill f = r.fills[i];
            int base = i * FILL_STRIDE;
            fillData[base    ] = f.makerIdHigh;
            fillData[base + 1] = f.makerIdLow;
            fillData[base + 2] = f.takerIdHigh;
            fillData[base + 3] = f.takerIdLow;
            fillData[base + 4] = f.filledQty;
            fillData[base + 5] = f.price;
            fillData[base + 6] = f.makerFee;
            fillData[base + 7] = f.takerFee;
        }
    }

    /** Accessor helpers (avoid per-fill object creation on the consumer side). */
    public long fillMakerIdHigh(int i) { return fillData[i * FILL_STRIDE    ]; }
    public long fillMakerIdLow (int i) { return fillData[i * FILL_STRIDE + 1]; }
    public long fillTakerIdHigh(int i) { return fillData[i * FILL_STRIDE + 2]; }
    public long fillTakerIdLow (int i) { return fillData[i * FILL_STRIDE + 3]; }
    public long fillQty        (int i) { return fillData[i * FILL_STRIDE + 4]; }
    public long fillPrice      (int i) { return fillData[i * FILL_STRIDE + 5]; }
    public long fillMakerFee   (int i) { return fillData[i * FILL_STRIDE + 6]; }
    public long fillTakerFee   (int i) { return fillData[i * FILL_STRIDE + 7]; }
}
