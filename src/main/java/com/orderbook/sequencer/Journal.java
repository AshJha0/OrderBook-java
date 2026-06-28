package com.orderbook.sequencer;

import com.orderbook.OrderBookException;

import java.util.List;

/**
 * Journal abstraction for write-ahead logging and deterministic replay.
 *
 * <p>Mirrors the Rust {@code Journal} trait.</p>
 */
public interface Journal<T> extends AutoCloseable {

    /** Append a command to the journal, assigning the next sequence number. */
    long append(SequencerCommand<T> command) throws OrderBookException;

    /** Read all entries starting from {@code fromSequence} (inclusive). */
    List<JournalEntry<T>> readFrom(long fromSequence) throws OrderBookException;

    /** Total number of journal entries. */
    long size();

    @Override
    void close();
}
