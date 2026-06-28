package com.orderbook.model;

import com.orderbook.fees.FeeSchedule;

import java.util.ArrayList;
import java.util.List;

/**
 * Display-oriented view of a {@link TradeResult}, with per-transaction fee breakdown.
 */
public record TradeInfo(
        String symbol,
        String orderId,
        long executedQuantity,
        long remainingQuantity,
        boolean isComplete,
        int transactionCount,
        List<TransactionInfo> transactions
) {
    /**
     * Build from a {@link TradeResult}, populating per-transaction fees from {@code feeSchedule}.
     */
    public static TradeInfo fromTradeResult(TradeResult result, FeeSchedule feeSchedule) {
        MatchResult mr = result.matchResult;
        FeeSchedule sched = (feeSchedule != null && !feeSchedule.isZeroFee()) ? feeSchedule : null;

        List<TransactionInfo> txInfos = new ArrayList<>();
        for (Transaction tx : mr.trades()) {
            long notional = tx.notional();
            long makerFee = sched != null ? sched.calculateFee(notional, true) : 0L;
            long takerFee = sched != null ? sched.calculateFee(notional, false) : 0L;
            txInfos.add(new TransactionInfo(
                    tx.price().value(),
                    tx.quantity().value(),
                    tx.tradeId().toString(),
                    tx.makerOrderId().toString(),
                    tx.takerOrderId().toString(),
                    makerFee,
                    takerFee
            ));
        }

        return new TradeInfo(
                result.symbol,
                mr.orderId().toString(),
                mr.executedQuantity().value(),
                mr.remainingQuantity().value(),
                mr.isComplete(),
                mr.tradeCount(),
                List.copyOf(txInfos)
        );
    }
}
