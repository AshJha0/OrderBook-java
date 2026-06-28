package com.orderbook.wire;

/** Exceptions in the wire protocol layer. */
public sealed class WireError extends Exception
        permits WireError.FramingError, WireError.ParseError, WireError.EncodingError {

    protected WireError(String message) { super(message); }

    public static final class FramingError  extends WireError { public FramingError(String m)  { super(m); } }
    public static final class ParseError    extends WireError { public ParseError(String m)    { super(m); } }
    public static final class EncodingError extends WireError { public EncodingError(String m) { super(m); } }
}
