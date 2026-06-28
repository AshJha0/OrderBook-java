package com.orderbook.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.HexFormat;

/**
 * A 32-byte user identifier used for STP checks and per-user order tracking.
 * Equivalent to the Rust {@code Hash32} newtype.
 */
public final class Hash32 {

    public static final Hash32 ZERO = new Hash32(new byte[32]);

    private final byte[] bytes;

    private Hash32(byte[] bytes) {
        if (bytes.length != 32) throw new IllegalArgumentException("Hash32 must be 32 bytes");
        this.bytes = bytes.clone();
    }

    public static Hash32 of(byte[] bytes) {
        return new Hash32(bytes);
    }

    @JsonCreator
    public static Hash32 fromHex(String hex) {
        return new Hash32(HexFormat.of().parseHex(hex));
    }

    public static Hash32 zero() { return ZERO; }

    public boolean isZero() { return Arrays.equals(bytes, ZERO.bytes); }

    @JsonValue
    public String toHex() { return HexFormat.of().formatHex(bytes); }

    @Override
    public boolean equals(Object o) {
        return o instanceof Hash32 other && Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(bytes); }

    @Override
    public String toString() { return toHex(); }
}
