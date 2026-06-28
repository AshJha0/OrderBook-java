package com.orderbook.model;

/**
 * Closed taxonomy of order rejection reasons, mirroring the Rust {@code RejectReason} enum.
 */
public enum RejectReason {
    KILL_SWITCH_ACTIVE,
    DUPLICATE_ORDER_ID,
    MISSING_USER_ID,
    SELF_TRADE_PREVENTED,
    INVALID_TICK_SIZE,
    INVALID_LOT_SIZE,
    ORDER_SIZE_OUT_OF_RANGE,
    INSUFFICIENT_LIQUIDITY,
    POST_ONLY_WOULD_MATCH,
    OPEN_ORDER_LIMIT_BREACHED,
    NOTIONAL_LIMIT_BREACHED,
    RISK_CHECK_FAILED,
    EXPIRED,
    INVALID_PRICE,
    INVALID_QUANTITY,
    UNKNOWN;
}
