package com.orderbook.implied_volatility;

/**
 * Newton-Raphson IV solver.
 *
 * <p>Mirrors the Rust {@code solver} module. Finds σ such that
 * {@code BlackScholes.price(params, σ) ≈ targetPrice}.</p>
 */
public final class IVSolver {

    private static final int MAX_ITERATIONS = 100;
    private static final double TOLERANCE = 1e-8;
    private static final double INITIAL_SIGMA = 0.2;
    private static final double MIN_SIGMA = 1e-9;
    private static final double MAX_SIGMA = 10.0;

    private IVSolver() {}

    /**
     * Solve for implied volatility.
     *
     * @param params      option parameters
     * @param targetPrice market price of the option
     * @param spreadBps   bid-ask spread in bps (for quality assessment)
     * @return IV result, or empty if solver failed to converge
     */
    public static java.util.Optional<IVResult> solve(IVParams params, double targetPrice, double spreadBps) {
        if (targetPrice <= 0) return java.util.Optional.empty();

        double sigma = INITIAL_SIGMA;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double modelPrice = BlackScholes.price(params, sigma);
            double diff = modelPrice - targetPrice;

            if (Math.abs(diff) < TOLERANCE) {
                IVQuality quality = IVQuality.fromSpreadBps(spreadBps);
                return java.util.Optional.of(new IVResult(sigma, targetPrice, spreadBps, i + 1, quality));
            }

            double vega = BlackScholes.vega(params, sigma);
            if (Math.abs(vega) < 1e-14) break; // degenerate

            sigma -= diff / vega;
            sigma = Math.max(MIN_SIGMA, Math.min(MAX_SIGMA, sigma));
        }
        return java.util.Optional.empty();
    }
}
