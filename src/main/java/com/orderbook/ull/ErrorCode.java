package com.orderbook.ull;

/**
 * Primitive error codes returned from the hot path instead of throwing exceptions.
 * Using long so callers can test with a single comparison.
 * Negative values = errors; 0 = success/resting; positive = filled quantity.
 */
public final class ErrorCode {
    private ErrorCode() {}

    public static final long OK              =  0L;
    public static final long KILL_SWITCH     = -1L;
    public static final long DUPLICATE_ID    = -2L;
    public static final long INVALID_TICK    = -3L;
    public static final long INVALID_LOT     = -4L;
    public static final long SIZE_TOO_SMALL  = -5L;
    public static final long SIZE_TOO_LARGE  = -6L;
    public static final long MISSING_USER    = -7L;
    public static final long NOT_FOUND       = -8L;
    public static final long PRICE_CROSSING  = -9L;
    public static final long RISK_OPEN_LIMIT = -10L;
    public static final long RISK_NOTIONAL   = -11L;
    public static final long RISK_PRICE_BAND = -12L;
    public static final long FOK_CANCELLED   = -13L;

    public static boolean isError(long code) { return code < 0; }

    public static String name(long code) {
        return switch ((int) code) {
            case  0  -> "OK";
            case -1  -> "KILL_SWITCH";
            case -2  -> "DUPLICATE_ID";
            case -3  -> "INVALID_TICK";
            case -4  -> "INVALID_LOT";
            case -5  -> "SIZE_TOO_SMALL";
            case -6  -> "SIZE_TOO_LARGE";
            case -7  -> "MISSING_USER";
            case -8  -> "NOT_FOUND";
            case -9  -> "PRICE_CROSSING";
            case -10 -> "RISK_OPEN_LIMIT";
            case -11 -> "RISK_NOTIONAL";
            case -12 -> "RISK_PRICE_BAND";
            case -13 -> "FOK_CANCELLED";
            default  -> "UNKNOWN(" + code + ")";
        };
    }
}
