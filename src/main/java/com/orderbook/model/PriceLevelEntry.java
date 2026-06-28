package com.orderbook.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A single price level in the order book, holding a FIFO queue of resting orders.
 *
 * <p>Mirrors the Rust {@code PriceLevel} from the {@code pricelevel} crate.
 * Thread-safety is achieved with a per-level lock (coarser than Rust's lock-free
 * approach but straightforward and correct).</p>
 */
public final class PriceLevelEntry<T> {

    private final Price price;
    private final ReentrantLock lock = new ReentrantLock();
    private final List<OrderType<T>> orders = new ArrayList<>();

    public PriceLevelEntry(Price price) {
        this.price = price;
    }

    public Price price() { return price; }

    public void addOrder(OrderType<T> order) {
        lock.lock();
        try { orders.add(order); }
        finally { lock.unlock(); }
    }

    /** Remove an order by id; returns true if found and removed. */
    public boolean removeOrder(Id id) {
        lock.lock();
        try {
            return orders.removeIf(o -> o.id().equals(id));
        } finally { lock.unlock(); }
    }

    /** Snapshot of the current resting orders (defensive copy). */
    public List<OrderType<T>> snapshot() {
        lock.lock();
        try { return List.copyOf(orders); }
        finally { lock.unlock(); }
    }

    /** Total visible quantity at this level. */
    public long visibleQuantity() {
        lock.lock();
        try {
            long total = 0L;
            for (OrderType<T> o : orders) total += o.visibleQuantity().value();
            return total;
        } finally { lock.unlock(); }
    }

    public boolean isEmpty() {
        lock.lock();
        try { return orders.isEmpty(); }
        finally { lock.unlock(); }
    }

    public int size() {
        lock.lock();
        try { return orders.size(); }
        finally { lock.unlock(); }
    }

    /**
     * Match a taker against this level. Fills orders FIFO until the taker
     * quantity is exhausted or the level is drained. Updates resting orders
     * in-place.
     *
     * @param takerOrderId  the taker order id
     * @param takerSide     the side of the taker
     * @param needed        quantity the taker still needs to fill
     * @param result        the MatchResult to accumulate trades into
     * @param txIdSupplier  supplier for fresh transaction ids
     */
    public void matchAgainst(
            Id takerOrderId,
            Side takerSide,
            long[] needed,
            MatchResult result,
            java.util.function.Supplier<Id> txIdSupplier
    ) {
        lock.lock();
        try {
            var it = orders.iterator();
            while (it.hasNext() && needed[0] > 0) {
                OrderType<T> maker = it.next();
                long available = maker.visibleQuantity().value();
                if (available == 0) continue;

                long fill = Math.min(needed[0], available);
                Transaction tx = new Transaction(
                        txIdSupplier.get(),
                        maker.id(),
                        takerOrderId,
                        price,
                        Quantity.of(fill),
                        takerSide
                );
                result.addTrade(tx);
                needed[0] -= fill;

                if (fill >= available) {
                    // maker fully filled
                    result.markOrderFilled(maker.id());
                    it.remove();
                } else {
                    // partial fill — reduce maker quantity
                    replaceWithReducedQuantity(it, maker, available - fill);
                }
            }
        } finally { lock.unlock(); }
    }

    @SuppressWarnings("unchecked")
    private void replaceWithReducedQuantity(java.util.Iterator<OrderType<T>> it, OrderType<T> maker, long newQty) {
        // We can't mutate a record, so we reconstruct with reduced quantity.
        // This is called while holding the lock so the iterator is valid.
        // We step back and replace via a ListIterator.
        // The simplest approach: mark via a wrapper; here we remove and re-add.
        // Because we need a list iterator, we do it from the orders list directly.
        int idx = orders.lastIndexOf(maker);
        if (idx >= 0) {
            OrderType<T> reduced = reduceQuantity(maker, newQty);
            orders.set(idx, reduced);
        }
    }

    @SuppressWarnings("unchecked")
    private OrderType<T> reduceQuantity(OrderType<T> order, long newQty) {
        Quantity q = Quantity.of(newQty);
        return switch (order) {
            case OrderType.Standard<T> o -> new OrderType.Standard<>(
                    o.id(), o.price(), q, o.side(), o.userId(), o.timestamp(), o.timeInForce(), o.extraFields());
            case OrderType.Iceberg<T> o -> new OrderType.Iceberg<>(
                    o.id(), o.price(), q, o.hiddenQuantity(), o.side(), o.userId(), o.timestamp(), o.timeInForce(), o.extraFields());
            case OrderType.PostOnly<T> o -> new OrderType.PostOnly<>(
                    o.id(), o.price(), q, o.side(), o.userId(), o.timestamp(), o.timeInForce(), o.extraFields());
            case OrderType.FillOrKill<T> o -> new OrderType.FillOrKill<>(
                    o.id(), o.price(), q, o.side(), o.userId(), o.timestamp(), o.extraFields());
            case OrderType.ImmediateOrCancel<T> o -> new OrderType.ImmediateOrCancel<>(
                    o.id(), o.price(), q, o.side(), o.userId(), o.timestamp(), o.extraFields());
            case OrderType.GoodTillDate<T> o -> new OrderType.GoodTillDate<>(
                    o.id(), o.price(), q, o.side(), o.userId(), o.timestamp(), o.expiryTime(), o.extraFields());
            case OrderType.TrailingStop<T> o -> new OrderType.TrailingStop<>(
                    o.id(), o.price(), q, o.side(), o.userId(), o.timestamp(), o.timeInForce(), o.trailAmount(), o.lastReferencePrice(), o.extraFields());
            case OrderType.PeggedOrder<T> o -> new OrderType.PeggedOrder<>(
                    o.id(), o.price(), q, o.side(), o.userId(), o.timestamp(), o.timeInForce(), o.referencePriceOffset(), o.extraFields());
            case OrderType.MarketToLimit<T> o -> new OrderType.MarketToLimit<>(
                    o.id(), o.price(), q, o.side(), o.userId(), o.timestamp(), o.timeInForce(), o.extraFields());
            case OrderType.Reserve<T> o -> new OrderType.Reserve<>(
                    o.id(), o.price(), q, o.hiddenQuantity(), o.side(), o.userId(), o.timestamp(), o.timeInForce(), o.replenishThreshold(), o.replenishAmount(), o.autoReplenish(), o.extraFields());
        };
    }
}
