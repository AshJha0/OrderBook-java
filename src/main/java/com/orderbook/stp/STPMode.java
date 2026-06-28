package com.orderbook.stp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Self-Trade Prevention mode.
 *
 * <p>Controls what happens when an incoming order would match against a resting
 * order from the same user (identified by {@link com.orderbook.model.Hash32} user id).</p>
 */
public enum STPMode {
    /** No STP (default). Zero overhead on matching hot path. */
    NONE(0, "None"),
    /** Cancel the incoming (taker) order on self-trade. */
    CANCEL_TAKER(1, "CancelTaker"),
    /** Cancel the resting (maker) order and continue matching. */
    CANCEL_MAKER(2, "CancelMaker"),
    /** Cancel both taker and maker orders. */
    CANCEL_BOTH(3, "CancelBoth");

    private final int code;
    private final String label;

    STPMode(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() { return code; }

    @JsonValue
    public String label() { return label; }

    @JsonCreator
    public static STPMode fromLabel(String v) {
        for (STPMode m : values()) {
            if (m.label.equalsIgnoreCase(v) || m.name().equalsIgnoreCase(v)) return m;
        }
        throw new IllegalArgumentException("Unknown STPMode: " + v);
    }

    public boolean isEnabled() { return this != NONE; }

    @Override
    public String toString() { return label; }
}
