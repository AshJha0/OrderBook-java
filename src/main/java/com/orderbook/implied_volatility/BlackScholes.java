package com.orderbook.implied_volatility;

/**
 * Black-Scholes pricing model.
 *
 * <p>Port of the Rust {@code BlackScholes} struct using the same
 * Abramowitz and Stegun erf approximation (error &lt; 1.5×10⁻⁷).</p>
 */
public final class BlackScholes {

    private BlackScholes() {}

    private static final double SQRT_2 = Math.sqrt(2.0);

    /** Abramowitz & Stegun approximation of erf(x) — max error 1.5e-7. */
    public static double erf(double x) {
        final double A1 =  0.254829592;
        final double A2 = -0.284496736;
        final double A3 =  1.421413741;
        final double A4 = -1.453152027;
        final double A5 =  1.061405429;
        final double P  =  0.3275911;

        double sign = x < 0 ? -1.0 : 1.0;
        double ax = Math.abs(x);
        double t = 1.0 / (1.0 + P * ax);
        double y = 1.0 - (((((A5 * t + A4) * t + A3) * t + A2) * t + A1) * t) * Math.exp(-ax * ax);
        return sign * y;
    }

    /** Standard normal CDF: P(Z ≤ x). */
    public static double normCdf(double x) {
        return 0.5 * (1.0 + erf(x / SQRT_2));
    }

    /** Standard normal PDF. */
    public static double normPdf(double x) {
        return Math.exp(-0.5 * x * x) / Math.sqrt(2.0 * Math.PI);
    }

    /** d₁ parameter: [ln(S/K) + (r + σ²/2)T] / (σ√T). */
    public static double d1(double spot, double strike, double tte, double rfr, double sigma) {
        return (Math.log(spot / strike) + (rfr + 0.5 * sigma * sigma) * tte)
                / (sigma * Math.sqrt(tte));
    }

    /** d₂ = d₁ − σ√T. */
    public static double d2(double d1, double sigma, double tte) {
        return d1 - sigma * Math.sqrt(tte);
    }

    /** Option price. */
    public static double price(IVParams p, double sigma) {
        if (sigma <= 0 || p.timeToExpiry() <= 0) return Math.max(0.0, p.intrinsicValue());
        double d1 = d1(p.spot(), p.strike(), p.timeToExpiry(), p.riskFreeRate(), sigma);
        double d2 = d2(d1, sigma, p.timeToExpiry());
        double disc = Math.exp(-p.riskFreeRate() * p.timeToExpiry());
        return switch (p.optionType()) {
            case CALL -> p.spot() * normCdf(d1) - p.strike() * disc * normCdf(d2);
            case PUT  -> p.strike() * disc * normCdf(-d2) - p.spot() * normCdf(-d1);
        };
    }

    /** Vega (∂price/∂σ). */
    public static double vega(IVParams p, double sigma) {
        if (sigma <= 0 || p.timeToExpiry() <= 0) return 0.0;
        double d1 = d1(p.spot(), p.strike(), p.timeToExpiry(), p.riskFreeRate(), sigma);
        return p.spot() * normPdf(d1) * Math.sqrt(p.timeToExpiry());
    }
}
