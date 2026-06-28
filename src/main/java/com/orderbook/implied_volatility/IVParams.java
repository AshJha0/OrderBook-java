package com.orderbook.implied_volatility;

/**
 * Parameters for implied volatility calculation.
 */
public record IVParams(
        double spot,
        double strike,
        double timeToExpiry,
        double riskFreeRate,
        OptionType optionType
) {
    public static IVParams call(double spot, double strike, double tte, double rfr) {
        return new IVParams(spot, strike, tte, rfr, OptionType.CALL);
    }

    public static IVParams put(double spot, double strike, double tte, double rfr) {
        return new IVParams(spot, strike, tte, rfr, OptionType.PUT);
    }

    public double intrinsicValue() {
        return switch (optionType) {
            case CALL -> Math.max(0.0, spot - strike);
            case PUT  -> Math.max(0.0, strike - spot);
        };
    }

    public boolean isItm() { return intrinsicValue() > 0.0; }

    public boolean isAtm() { return Math.abs(spot - strike) / strike < 0.001; }

    public boolean isOtm() { return !isItm() && !isAtm(); }
}
