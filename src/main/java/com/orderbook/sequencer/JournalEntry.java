package com.orderbook.sequencer;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single record in the write-ahead journal, wrapping a {@link SequencerCommand}
 * with a monotonic sequence number and nanosecond timestamp.
 */
public record JournalEntry<T>(
        @JsonProperty("sequence_num")  long sequenceNum,
        @JsonProperty("timestamp_ns")  long timestampNs,
        @JsonProperty("command")       SequencerCommand<T> command
) {}
