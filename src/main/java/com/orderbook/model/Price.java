package com.orderbook.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Price newtype wrapping a {@code long} (unsigned 64-bit value stored as signed long).
 * Prices are represented as fixed-point integers; the scale is application-defined.
 */
public record Price(@JsonValue long value) implements Comparable<Price> {

    @JsonCreator
    public static Price of(long value) { return new Price(value); }

    /** Convert to unsigned 128-bit representation (as Java BigInteger or long). */
    public long asLong() { return value; }

    @Override
    public int compareTo(Price other) {
        return Long.compareUnsigned(this.value, other.value);
    }

    @Override
    public String toString() { return Long.toUnsignedString(value); }
}
