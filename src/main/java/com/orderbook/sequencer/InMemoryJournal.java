package com.orderbook.sequencer;

import com.orderbook.OrderBookException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory journal for testing and development.
 *
 * <p>Mirrors the Rust {@code InMemoryJournal}.</p>
 */
public final class InMemoryJournal<T> implements Journal<T> {

    private final List<JournalEntry<T>> entries = new ArrayList<>();
    private final AtomicLong seq = new AtomicLong(0);

    @Override
    public synchronized long append(SequencerCommand<T> command) {
        long s = seq.getAndIncrement();
        entries.add(new JournalEntry<>(s, System.nanoTime(), command));
        return s;
    }

    @Override
    public synchronized List<JournalEntry<T>> readFrom(long fromSequence) {
        return entries.stream()
                .filter(e -> e.sequenceNum() >= fromSequence)
                .toList();
    }

    @Override
    public long size() { return entries.size(); }

    @Override
    public void close() { /* no-op */ }
}
