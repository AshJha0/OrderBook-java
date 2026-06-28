package com.orderbook.model;

/**
 * Reasons an order may be cancelled.
 */
public enum CancelReason {
    USER_REQUESTED,
    IOC_RESIDUAL,
    FOK_UNFULFILLABLE,
    DAY_ORDER_EXPIRED,
    GTD_EXPIRED,
    STP_CANCEL_TAKER,
    STP_CANCEL_MAKER,
    STP_CANCEL_BOTH,
    KILL_SWITCH_DRAIN,
    MASS_CANCEL,
    UNKNOWN;
}
