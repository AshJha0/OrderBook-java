package com.orderbook.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Millisecond timestamp newtype. */
public record TimestampMs(@JsonValue long value) implements Comparable<TimestampMs> {

    @JsonCreator
    public static TimestampMs of(long value) { return new TimestampMs(value); }

    @Override
    public int compareTo(TimestampMs other) {
        return Long.compareUnsigned(this.value, other.value);
    }

    @Override
    public String toString() { return Long.toUnsignedString(value); }
}
