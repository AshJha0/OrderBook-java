package com.orderbook.risk;

import com.orderbook.model.Hash32;

/** Per-order risk entry stored for counter reconciliation on cancellation. */
public record RiskEntry(Hash32 userId, long notional) {}
