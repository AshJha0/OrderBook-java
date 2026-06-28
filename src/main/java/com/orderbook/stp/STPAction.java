package com.orderbook.stp;

import com.orderbook.model.Id;

/**
 * Result of an STP check at a single price level.
 */
public sealed interface STPAction
        permits STPAction.NoConflict, STPAction.CancelTaker,
                STPAction.CancelMaker, STPAction.CancelBoth {

    /** No self-trade detected; proceed normally. */
    record NoConflict() implements STPAction {}

    /**
     * CancelTaker triggered.
     *
     * @param safeQuantity quantity of non-self orders before the first self order;
     *                     0 means the first order is self.
     */
    record CancelTaker(long safeQuantity) implements STPAction {}

    /** CancelMaker triggered; caller re-scans and cancels all same-user makers. */
    record CancelMaker() implements STPAction {}

    /**
     * CancelBoth triggered.
     *
     * @param safeQuantity  quantity before the first self order
     * @param makerOrderId  the first same-user maker id to cancel
     */
    record CancelBoth(long safeQuantity, Id makerOrderId) implements STPAction {}
}
