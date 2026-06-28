package com.orderbook.wire.outbound;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Outbound book-update message (24 bytes, little-endian).
 *
 * <pre>
 * Offset  Size  Field
 *      0     8  engine_seq
 *      8     1  side        — 0=Bid 1=Ask
 *      9     8  price
 *     17     8  quantity    — 0 = level removed
 *     25     ?  (no pad — 25 bytes total rounded up)
 * </pre>
 *
 * <p>Mirrors the Rust {@code BookUpdateWire} outbound encoder.</p>
 */
public record BookUpdate(
        long engineSeq,
        byte side,
        long price,
        long quantity
) {
    public static final int SIZE = 25;
    public static final byte SIDE_BID = 0;
    public static final byte SIDE_ASK = 1;

    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);
        buf.putLong(0, engineSeq);
        buf.put(8, side);
        buf.putLong(9, price);
        buf.putLong(17, quantity);
        return buf.array();
    }

    public static BookUpdate decode(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return new BookUpdate(
                buf.getLong(0),
                buf.get(8),
                buf.getLong(9),
                buf.getLong(17)
        );
    }
}
