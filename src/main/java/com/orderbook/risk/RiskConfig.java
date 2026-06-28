package com.orderbook.risk;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-book risk configuration.
 *
 * <p>All limits default to {@code null} (disabled). A fully-default config
 * is a no-op passthrough — every check returns immediately.</p>
 */
public final class RiskConfig {

    /** Max resting orders per account. {@code null} = disabled. */
    @JsonProperty("max_open_orders_per_account")
    private Long maxOpenOrdersPerAccount;

    /** Max resting notional per account (price × qty). {@code null} = disabled. */
    @JsonProperty("max_notional_per_account")
    private Long maxNotionalPerAccount;

    /** Max price deviation from reference in bps. {@code null} = disabled. */
    @JsonProperty("price_band_bps")
    private Integer priceBandBps;

    /** Which reference price to use for the price-band check. */
    @JsonProperty("reference_price")
    private ReferencePriceSource referencePrice;

    public RiskConfig() {}

    public Long maxOpenOrdersPerAccount() { return maxOpenOrdersPerAccount; }
    public Long maxNotionalPerAccount() { return maxNotionalPerAccount; }
    public Integer priceBandBps() { return priceBandBps; }
    public ReferencePriceSource referencePrice() { return referencePrice; }

    public RiskConfig withMaxOpenOrdersPerAccount(long n) {
        this.maxOpenOrdersPerAccount = n; return this;
    }

    public RiskConfig withMaxNotionalPerAccount(long n) {
        this.maxNotionalPerAccount = n; return this;
    }

    public RiskConfig withPriceBandBps(int bps, ReferencePriceSource source) {
        this.priceBandBps = bps;
        this.referencePrice = source;
        return this;
    }

    public boolean isDisabled() {
        return maxOpenOrdersPerAccount == null
                && maxNotionalPerAccount == null
                && priceBandBps == null;
    }
}
