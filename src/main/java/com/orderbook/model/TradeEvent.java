package com.orderbook.model;

/** Trade event envelope carrying metadata alongside the result. */
public record TradeEvent(
        String symbol,
        TradeResult tradeResult,
        long timestamp,
        long engineSeq
) {}
