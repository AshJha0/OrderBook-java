package com.orderbook.model;

/** Detailed info about one fill within a trade. */
public record TransactionInfo(
        long price,
        long quantity,
        String transactionId,
        String makerOrderId,
        String takerOrderId,
        long makerFee,
        long takerFee
) {}
