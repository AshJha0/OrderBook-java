package com.orderbook.ull;

/**
 * Pre-allocated, reusable match result.  Populated during a match sweep and
 * consumed by the outbound Disruptor before being released back to the pool.
 *
 * All fill data is held in a flat {@code MutableFill[]} array allocated once
 * at construction time — zero heap allocation during steady-state matching.
 */
public final class MutableMatchResult {

    /** Maximum fills tracked per order. Orders sweeping many levels will truncate
     *  beyond this; increase if needed for your depth profile. */
    public static final int MAX_FILLS = 256;

    public long takerIdHigh;
    public long takerIdLow;
    public long takerPrice;
    public byte takerSide;           // 0=BUY 1=SELL
    public long engineSeq;

    public long executedQty;
    public long remainingQty;
    public long totalMakerFee;
    public long totalTakerFee;

    /** True once all requested quantity has been matched. */
    public boolean complete;

    public int fillCount;
    public final MutableFill[] fills;

    public MutableMatchResult() {
        fills = new MutableFill[MAX_FILLS];
        for (int i = 0; i < MAX_FILLS; i++) fills[i] = new MutableFill();
    }

    public void reset(long takerIdHigh, long takerIdLow, long price, byte side, long requestedQty) {
        this.takerIdHigh  = takerIdHigh;
        this.takerIdLow   = takerIdLow;
        this.takerPrice   = price;
        this.takerSide    = side;
        this.executedQty  = 0L;
        this.remainingQty = requestedQty;
        this.totalMakerFee = 0L;
        this.totalTakerFee = 0L;
        this.complete     = false;
        this.engineSeq    = 0L;
        this.fillCount    = 0;
        for (int i = 0; i < MAX_FILLS; i++) fills[i].reset();
    }

    /** Record a fill into the next free slot.  Silently ignores overflow beyond MAX_FILLS. */
    public void addFill(long makerIdHigh, long makerIdLow,
                        long filledQty, long price,
                        long makerFee, long takerFee,
                        long mu0, long mu1, long mu2, long mu3) {
        executedQty  += filledQty;
        remainingQty -= filledQty;
        totalMakerFee += makerFee;
        totalTakerFee += takerFee;

        if (fillCount < MAX_FILLS) {
            MutableFill f = fills[fillCount++];
            f.makerIdHigh = makerIdHigh;
            f.makerIdLow  = makerIdLow;
            f.takerIdHigh = takerIdHigh;
            f.takerIdLow  = takerIdLow;
            f.filledQty   = filledQty;
            f.price       = price;
            f.makerFee    = makerFee;
            f.takerFee    = takerFee;
            f.makerU0 = mu0; f.makerU1 = mu1; f.makerU2 = mu2; f.makerU3 = mu3;
        }
    }

    public boolean hasFills() { return fillCount > 0; }
}
