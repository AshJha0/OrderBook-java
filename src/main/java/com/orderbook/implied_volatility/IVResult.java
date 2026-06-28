package com.orderbook.implied_volatility;

/**
 * Result of an implied volatility calculation.
 */
public record IVResult(
        double iv,
        double priceUsed,
        double spreadBps,
        int iterations,
        IVQuality quality
) {
    public double ivPercent() { return iv * 100.0; }
    public boolean isHighQuality() { return quality == IVQuality.HIGH; }
    public boolean isAcceptableQuality() {
        return quality == IVQuality.HIGH || quality == IVQuality.MEDIUM;
    }
}
