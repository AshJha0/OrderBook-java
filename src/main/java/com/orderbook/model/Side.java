package com.orderbook.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The side of an order: buy (bid) or sell (ask). */
public enum Side {
    BUY("Buy"),
    SELL("Sell");

    private final String value;

    Side(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    @JsonCreator
    public static Side fromValue(String v) {
        for (Side s : values()) {
            if (s.value.equalsIgnoreCase(v) || s.name().equalsIgnoreCase(v)) return s;
        }
        throw new IllegalArgumentException("Unknown Side: " + v);
    }

    public Side opposite() {
        return this == BUY ? SELL : BUY;
    }
}
