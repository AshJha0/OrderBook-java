package com.orderbook.wire.inbound;

import com.orderbook.wire.WireError;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Inbound {@code Cancel} packed wire message (16 bytes, little-endian).
 *
 * <pre>
 * Offset  Size  Field
 *      0     8  order_id  — order to cancel
 *      8     8  _pad      — reserved (zero)
 * </pre>
 */
public record CancelWire(long orderId) {

    public static final int SIZE = 16;

    public static CancelWire decode(byte[] bytes) throws WireError.ParseError {
        if (bytes.length < SIZE) throw new WireError.ParseError("CancelWire requires " + SIZE + " bytes");
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return new CancelWire(buf.getLong(0));
    }

    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);
        buf.putLong(0, orderId);
        return buf.array();
    }
}
