package com.orderbook.model;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional per-order lifecycle tracker.
 *
 * <p>When attached to an {@link com.orderbook.OrderBook}, every state transition
 * (Open → PartiallyFilled → Filled, Cancelled, Rejected) is recorded here.
 * When absent, zero overhead.</p>
 */
public final class OrderStateTracker {

    private final Map<Id, OrderStatus> states = new ConcurrentHashMap<>();

    public void record(Id orderId, OrderStatus status) {
        states.put(orderId, status);
    }

    public Optional<OrderStatus> get(Id orderId) {
        return Optional.ofNullable(states.get(orderId));
    }

    public void remove(Id orderId) { states.remove(orderId); }

    public int size() { return states.size(); }

    public Map<Id, OrderStatus> snapshot() { return Map.copyOf(states); }
}
