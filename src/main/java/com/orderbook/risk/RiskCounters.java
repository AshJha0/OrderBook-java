package com.orderbook.risk;

import java.util.concurrent.atomic.AtomicLong;

/** Per-account running totals for risk checks. */
public final class RiskCounters {
    public final AtomicLong openOrders = new AtomicLong(0);
    public final AtomicLong notional = new AtomicLong(0);

    public void addOrder(long orderNotional) {
        openOrders.incrementAndGet();
        notional.addAndGet(orderNotional);
    }

    public void removeOrder(long orderNotional) {
        openOrders.decrementAndGet();
        notional.addAndGet(-orderNotional);
    }
}
