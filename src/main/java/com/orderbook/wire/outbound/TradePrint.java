package com.orderbook.wire.outbound;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Outbound trade-print message (41 bytes, little-endian).
 *
 * <pre>
 * Offset  Size  Field
 *      0     8  engine_seq
 *      8     8  maker_order_id
 *     16     8  taker_order_id
 *     24     8  price
 *     32     8  quantity
 *     40     1  taker_side   — 0=Buy 1=Sell
 * </pre>
 */
public record TradePrint(
        long engineSeq,
        long makerOrderId,
        long takerOrderId,
        long price,
        long quantity,
        byte takerSide
) {
    public static final int SIZE = 41;

    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);
        buf.putLong(0,  engineSeq);
        buf.putLong(8,  makerOrderId);
        buf.putLong(16, takerOrderId);
        buf.putLong(24, price);
        buf.putLong(32, quantity);
        buf.put(40,     takerSide);
        return buf.array();
    }

    public static TradePrint decode(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return new TradePrint(
                buf.getLong(0),
                buf.getLong(8),
                buf.getLong(16),
                buf.getLong(24),
                buf.getLong(32),
                buf.get(40)
        );
    }
}
