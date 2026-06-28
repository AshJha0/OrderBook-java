package com.orderbook.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The result of matching a taker order against one or more price levels.
 *
 * <p>Mirrors the Rust {@code MatchResult} from the {@code pricelevel} crate.</p>
 */
public final class MatchResult {

    private final Id orderId;
    private final Quantity initialQuantity;
    private final List<Transaction> trades = new ArrayList<>();
    private final List<Id> filledOrderIds = new ArrayList<>();
    private Quantity remainingQuantity;

    public MatchResult(Id orderId, Quantity initialQuantity) {
        this.orderId = orderId;
        this.initialQuantity = initialQuantity;
        this.remainingQuantity = initialQuantity;
    }

    public Id orderId() { return orderId; }
    public Quantity initialQuantity() { return initialQuantity; }
    public List<Transaction> trades() { return Collections.unmodifiableList(trades); }
    public List<Id> filledOrderIds() { return Collections.unmodifiableList(filledOrderIds); }
    public Quantity remainingQuantity() { return remainingQuantity; }

    public void addTrade(Transaction tx) {
        trades.add(tx);
        long filled = tx.quantity().value();
        long rem = remainingQuantity.value() - filled;
        remainingQuantity = Quantity.of(Math.max(0L, rem));
    }

    public void markOrderFilled(Id id) { filledOrderIds.add(id); }

    public Quantity executedQuantity() {
        return Quantity.of(initialQuantity.value() - remainingQuantity.value());
    }

    public boolean isComplete() {
        return remainingQuantity.isZero();
    }

    public int tradeCount() { return trades.size(); }
}
