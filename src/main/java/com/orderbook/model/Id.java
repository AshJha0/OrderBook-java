package com.orderbook.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.UUID;

/**
 * Opaque order / transaction identifier backed by a UUID.
 */
public final class Id {

    public static final Id ZERO = new Id(new UUID(0L, 0L));

    private final UUID uuid;

    private Id(UUID uuid) { this.uuid = uuid; }

    public static Id newUuid() {
        return new Id(UUID.randomUUID());
    }

    @JsonCreator
    public static Id of(String s) {
        return new Id(UUID.fromString(s));
    }

    public static Id of(UUID uuid) {
        return new Id(uuid);
    }

    public UUID toUuid() { return uuid; }

    @JsonValue
    @Override
    public String toString() { return uuid.toString(); }

    @Override
    public boolean equals(Object o) {
        return o instanceof Id other && uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() { return uuid.hashCode(); }
}
