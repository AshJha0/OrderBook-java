package com.orderbook.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Time-in-force policies that control how long an order stays active.
 */
public enum TimeInForce {
    /** Good Till Cancelled — rests until explicitly cancelled. */
    GTC("Gtc"),
    /** Immediate Or Cancel — fill what you can right now, cancel the rest. */
    IOC("Ioc"),
    /** Fill Or Kill — fill entirely or cancel entirely. */
    FOK("Fok"),
    /** Good Till Date — rests until a specified expiry timestamp. */
    GTD("Gtd"),
    /** Day order — rests until market close. */
    DAY("Day");

    private final String value;

    TimeInForce(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    @JsonCreator
    public static TimeInForce fromValue(String v) {
        for (TimeInForce t : values()) {
            if (t.value.equalsIgnoreCase(v) || t.name().equalsIgnoreCase(v)) return t;
        }
        throw new IllegalArgumentException("Unknown TimeInForce: " + v);
    }
}
