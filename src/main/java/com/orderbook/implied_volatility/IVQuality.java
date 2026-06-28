package com.orderbook.implied_volatility;

/** IV calculation quality based on bid-ask spread at calculation time. */
public enum IVQuality {
    /** Spread < 100 bps — high liquidity. */
    HIGH,
    /** Spread 100–500 bps — moderate liquidity. */
    MEDIUM,
    /** Spread > 500 bps — low liquidity. */
    LOW,
    /** Interpolated from nearby strikes. */
    INTERPOLATED;

    public static IVQuality fromSpreadBps(double bps) {
        if (bps < 100.0) return HIGH;
        if (bps < 500.0) return MEDIUM;
        return LOW;
    }
}
