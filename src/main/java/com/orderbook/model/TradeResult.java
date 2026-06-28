package com.orderbook.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.orderbook.fees.FeeSchedule;

/**
 * Enhanced trade result including symbol, fees, and engine sequence.
 *
 * <p>Mirrors the Rust {@code TradeResult} struct.</p>
 */
public final class TradeResult {

    @JsonProperty("symbol")
    public final String symbol;

    @JsonProperty("match_result")
    public final MatchResult matchResult;

    @JsonProperty("total_maker_fees")
    public final long totalMakerFees;

    @JsonProperty("total_taker_fees")
    public final long totalTakerFees;

    @JsonProperty("engine_seq")
    public long engineSeq;

    @JsonProperty("quote_notional")
    public final long quoteNotional;

    private TradeResult(String symbol, MatchResult matchResult,
                        long totalMakerFees, long totalTakerFees,
                        long engineSeq, long quoteNotional) {
        this.symbol = symbol;
        this.matchResult = matchResult;
        this.totalMakerFees = totalMakerFees;
        this.totalTakerFees = totalTakerFees;
        this.engineSeq = engineSeq;
        this.quoteNotional = quoteNotional;
    }

    /** Create a TradeResult with zero fees. */
    public static TradeResult of(String symbol, MatchResult matchResult) {
        return new TradeResult(symbol, matchResult, 0L, 0L, 0L, computeQuoteNotional(matchResult));
    }

    /** Create a TradeResult with fees computed from the given schedule. */
    public static TradeResult withFees(String symbol, MatchResult matchResult, FeeSchedule feeSchedule) {
        long makerSum = 0L, takerSum = 0L;
        if (feeSchedule != null && !feeSchedule.isZeroFee()) {
            for (Transaction tx : matchResult.trades()) {
                long notional = tx.notional();
                makerSum = saturatingAdd(makerSum, feeSchedule.calculateFee(notional, true));
                takerSum = saturatingAdd(takerSum, feeSchedule.calculateFee(notional, false));
            }
        }
        return new TradeResult(symbol, matchResult, makerSum, takerSum, 0L, computeQuoteNotional(matchResult));
    }

    public long totalFees() { return saturatingAdd(totalMakerFees, totalTakerFees); }

    private static long computeQuoteNotional(MatchResult mr) {
        long total = 0L;
        for (Transaction tx : mr.trades()) {
            total = saturatingAddUnsigned(total, tx.notional());
        }
        return total;
    }

    private static long saturatingAdd(long a, long b) {
        long result = a + b;
        if (((a ^ result) & (b ^ result)) < 0) {
            return b > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return result;
    }

    private static long saturatingAddUnsigned(long a, long b) {
        long result = a + b;
        if (Long.compareUnsigned(result, a) < 0) return -1L; // unsigned max
        return result;
    }
}
