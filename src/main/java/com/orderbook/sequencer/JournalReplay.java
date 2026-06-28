package com.orderbook.sequencer;

import com.orderbook.OrderBook;
import com.orderbook.OrderBookException;

import java.util.List;

/**
 * Replays a journal into a fresh {@link OrderBook}, reconstructing its state.
 *
 * <p>Mirrors the Rust {@code replay} module.</p>
 */
public final class JournalReplay {

    private JournalReplay() {}

    /**
     * Replay all journal entries (from sequence 0) into the given book.
     *
     * @param journal  source journal
     * @param book     target (should be freshly constructed)
     * @param <T>      extra-fields type
     */
    public static <T> void replayAll(Journal<T> journal, OrderBook<T> book)
            throws OrderBookException {
        replayFrom(journal, book, 0L);
    }

    /**
     * Replay journal entries starting at {@code fromSequence}.
     */
    public static <T> void replayFrom(Journal<T> journal, OrderBook<T> book, long fromSequence)
            throws OrderBookException {
        List<JournalEntry<T>> entries = journal.readFrom(fromSequence);
        for (JournalEntry<T> entry : entries) {
            apply(entry.command(), book);
        }
    }

    private static <T> void apply(SequencerCommand<T> cmd, OrderBook<T> book) throws OrderBookException {
        switch (cmd) {
            case SequencerCommand.AddOrder<T> c -> book.submitOrder(c.order());
            case SequencerCommand.CancelOrder<T> c -> book.cancelOrder(c.orderId());
            case SequencerCommand.MarketOrder<T> c ->
                    book.submitMarketOrder(c.side(), c.quantity(), com.orderbook.model.Hash32.ZERO);
            case SequencerCommand.MarketOrderByAmount<T> ignored ->
                    throw new OrderBookException.InvalidOperation("MarketOrderByAmount replay not yet implemented");
            case SequencerCommand.CancelAll<T> ignored -> book.massCancel();
            case SequencerCommand.CancelBySide<T> c -> {
                for (var o : book.restingOrders(c.side())) book.cancelOrder(o.id());
            }
            case SequencerCommand.CancelByUser<T> c -> book.massCancelUser(c.userId());
            case SequencerCommand.CancelByPriceRange<T> c -> {
                for (var o : book.restingOrders(c.side())) {
                    long p = o.price().value();
                    if (Long.compareUnsigned(p, c.minPrice()) >= 0
                            && Long.compareUnsigned(p, c.maxPrice()) <= 0) {
                        book.cancelOrder(o.id());
                    }
                }
            }
        }
    }
}
