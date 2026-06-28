package com.orderbook.snapshot;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.orderbook.fees.FeeSchedule;
import com.orderbook.risk.RiskConfig;
import com.orderbook.stp.STPMode;

/**
 * Full snapshot package that includes matching configuration for round-trip restore.
 *
 * <p>Mirrors the Rust {@code OrderBookSnapshotPackage}.</p>
 */
public final class OrderBookSnapshotPackage {

    @JsonProperty("snapshot")          public final OrderBookSnapshot snapshot;
    @JsonProperty("checksum")          public final String checksum;
    @JsonProperty("engine_seq")        public final long engineSeq;
    @JsonProperty("kill_switch_engaged") public final boolean killSwitchEngaged;
    @JsonProperty("stp_mode")          public final STPMode stpMode;
    @JsonProperty("tick_size")         public final Long tickSize;
    @JsonProperty("lot_size")          public final Long lotSize;
    @JsonProperty("min_order_size")    public final Long minOrderSize;
    @JsonProperty("max_order_size")    public final Long maxOrderSize;
    @JsonProperty("fee_schedule")      public final FeeSchedule feeSchedule;
    @JsonProperty("risk_config")       public final RiskConfig riskConfig;

    public OrderBookSnapshotPackage(
            OrderBookSnapshot snapshot,
            String checksum,
            long engineSeq,
            boolean killSwitchEngaged,
            STPMode stpMode,
            Long tickSize,
            Long lotSize,
            Long minOrderSize,
            Long maxOrderSize,
            FeeSchedule feeSchedule,
            RiskConfig riskConfig) {
        this.snapshot = snapshot;
        this.checksum = checksum;
        this.engineSeq = engineSeq;
        this.killSwitchEngaged = killSwitchEngaged;
        this.stpMode = stpMode;
        this.tickSize = tickSize;
        this.lotSize = lotSize;
        this.minOrderSize = minOrderSize;
        this.maxOrderSize = maxOrderSize;
        this.feeSchedule = feeSchedule;
        this.riskConfig = riskConfig;
    }
}
