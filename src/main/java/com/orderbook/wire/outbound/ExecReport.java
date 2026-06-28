package com.orderbook.wire.outbound;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Outbound execution report (44 bytes, little-endian).
 *
 * <pre>
 * Offset  Size  Field
 *      0     8  engine_seq
 *      8     8  order_id
 *     16     1  status
 *     17     8  filled_qty
 *     25     8  remaining_qty
 *     33     8  price
 *     41     2  reject_reason
 *     43     1  _pad
 * </pre>
 */
public record ExecReport(
        long engineSeq,
        long orderId,
        byte status,
        long filledQty,
        long remainingQty,
        long price,
        short rejectReason
) {
    public static final int SIZE = 44;
    public static final byte STATUS_OPEN             = 0;
    public static final byte STATUS_PARTIALLY_FILLED = 1;
    public static final byte STATUS_FILLED           = 2;
    public static final byte STATUS_CANCELLED        = 3;
    public static final byte STATUS_REJECTED         = 4;

    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);
        buf.putLong(0,   engineSeq);
        buf.putLong(8,   orderId);
        buf.put(16,      status);
        buf.putLong(17,  filledQty);
        buf.putLong(25,  remainingQty);
        buf.putLong(33,  price);
        buf.putShort(41, rejectReason);
        // byte 43 = pad (zero)
        return buf.array();
    }

    public static ExecReport decode(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return new ExecReport(
                buf.getLong(0),
                buf.getLong(8),
                buf.get(16),
                buf.getLong(17),
                buf.getLong(25),
                buf.getLong(33),
                buf.getShort(41)
        );
    }
}
