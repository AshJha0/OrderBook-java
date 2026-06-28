package com.orderbook.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Quantity newtype wrapping an unsigned 64-bit integer.
 */
public record Quantity(@JsonValue long value) implements Comparable<Quantity> {

    public static final Quantity ZERO = new Quantity(0L);

    @JsonCreator
    public static Quantity of(long value) { return new Quantity(value); }

    public long asLong() { return value; }

    public boolean isZero() { return value == 0L; }

    public Quantity subtract(long qty) {
        return new Quantity(Math.max(0L, value - qty));
    }

    @Override
    public int compareTo(Quantity other) {
        return Long.compareUnsigned(this.value, other.value);
    }

    @Override
    public String toString() { return Long.toUnsignedString(value); }
}
