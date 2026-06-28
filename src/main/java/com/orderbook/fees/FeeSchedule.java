package com.orderbook.fees;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configurable fee schedule for maker and taker fees.
 *
 * <p>Fees are in basis points (bps): 1 bps = 0.01% = 0.0001.
 * Negative maker bps = maker rebate.</p>
 */
public final class FeeSchedule {

    /** Maker fee in basis points (negative = rebate). */
    @JsonProperty("maker_fee_bps")
    private final int makerFeeBps;

    /** Taker fee in basis points (non-negative). */
    @JsonProperty("taker_fee_bps")
    private final int takerFeeBps;

    @JsonCreator
    public FeeSchedule(
            @JsonProperty("maker_fee_bps") int makerFeeBps,
            @JsonProperty("taker_fee_bps") int takerFeeBps) {
        this.makerFeeBps = makerFeeBps;
        this.takerFeeBps = takerFeeBps;
    }

    public static FeeSchedule of(int makerFeeBps, int takerFeeBps) {
        return new FeeSchedule(makerFeeBps, takerFeeBps);
    }

    public static FeeSchedule zeroFee() { return new FeeSchedule(0, 0); }

    public static FeeSchedule takerOnly(int takerFeeBps) { return new FeeSchedule(0, takerFeeBps); }

    public static FeeSchedule withMakerRebate(int makerRebateBps, int takerFeeBps) {
        return new FeeSchedule(-Math.abs(makerRebateBps), takerFeeBps);
    }

    public int makerFeeBps() { return makerFeeBps; }
    public int takerFeeBps() { return takerFeeBps; }

    public boolean hasMakerRebate() { return makerFeeBps < 0; }

    public boolean isZeroFee() { return makerFeeBps == 0 && takerFeeBps == 0; }

    /**
     * Calculate fee for a given notional.
     *
     * <p>Uses unsigned arithmetic to stay correct above {@code Long.MAX_VALUE}.
     * Truncates toward zero (matches Rust behaviour).</p>
     *
     * @param notional the trade notional (price × quantity), unsigned long
     * @param isMaker  true for maker, false for taker
     * @return fee (positive = charge, negative = rebate)
     */
    public long calculateFee(long notional, boolean isMaker) {
        int bps = isMaker ? makerFeeBps : takerFeeBps;
        if (bps == 0) return 0L;

        long absBps = Math.abs((long) bps);
        // unsigned multiply then divide to avoid sign issues at large notionals
        // Use 128-bit arithmetic via BigInteger for correctness
        java.math.BigInteger n = java.math.BigInteger.valueOf(Long.toUnsignedString(notional).isEmpty()
                ? 0 : 0); // placeholder
        // Simpler: treat notional as unsigned via Long.toUnsignedString path
        java.math.BigInteger notionalBig = toUnsignedBig(notional);
        java.math.BigInteger magnitude = notionalBig
                .multiply(java.math.BigInteger.valueOf(absBps))
                .divide(java.math.BigInteger.valueOf(10_000));
        long magnitudeLong = magnitude.min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
        return bps < 0 ? -magnitudeLong : magnitudeLong;
    }

    private static java.math.BigInteger toUnsignedBig(long v) {
        if (v >= 0) return java.math.BigInteger.valueOf(v);
        return java.math.BigInteger.valueOf(v & Long.MAX_VALUE).setBit(63);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FeeSchedule f && makerFeeBps == f.makerFeeBps && takerFeeBps == f.takerFeeBps;
    }

    @Override
    public int hashCode() { return 31 * makerFeeBps + takerFeeBps; }

    @Override
    public String toString() {
        return "FeeSchedule{maker=" + makerFeeBps + "bps, taker=" + takerFeeBps + "bps}";
    }
}
