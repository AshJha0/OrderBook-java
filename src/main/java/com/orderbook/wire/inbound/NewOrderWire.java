package com.orderbook.wire.inbound;

import com.orderbook.wire.WireError;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Inbound {@code NewOrder} packed wire message (48 bytes, little-endian).
 *
 * <p>Layout matches the Rust {@code NewOrderWire} struct exactly.</p>
 *
 * <pre>
 * Offset  Size  Field
 *      0     8  client_ts    — client timestamp (ms)
 *      8     8  order_id     — unique order id
 *     16     8  account_id   — numeric account id
 *     24     8  price        — i64 tick-scaled price
 *     32     8  qty          — u64 quantity
 *     40     1  side         — 0=Buy 1=Sell
 *     41     1  time_in_force — 0=GTC 1=IOC 2=FOK 3=DAY
 *     42     1  order_type   — 0=Standard
 *     43     5  _pad         — reserved (zero)
 * </pre>
 */
public record NewOrderWire(
        long clientTs,
        long orderId,
        long accountId,
        long price,
        long qty,
        byte side,
        byte timeInForce,
        byte orderType
) {
    public static final int SIZE = 48;
    public static final byte SIDE_BUY  = 0;
    public static final byte SIDE_SELL = 1;
    public static final byte TIF_GTC = 0;
    public static final byte TIF_IOC = 1;
    public static final byte TIF_FOK = 2;
    public static final byte TIF_DAY = 3;
    public static final byte ORDER_TYPE_STANDARD = 0;

    public static NewOrderWire decode(byte[] bytes) throws WireError.ParseError {
        if (bytes.length < SIZE) throw new WireError.ParseError("NewOrderWire requires " + SIZE + " bytes");
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        long clientTs   = buf.getLong(0);
        long orderId    = buf.getLong(8);
        long accountId  = buf.getLong(16);
        long price      = buf.getLong(24);
        long qty        = buf.getLong(32);
        byte side       = buf.get(40);
        byte tif        = buf.get(41);
        byte orderType  = buf.get(42);
        return new NewOrderWire(clientTs, orderId, accountId, price, qty, side, tif, orderType);
    }

    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);
        buf.putLong(0,  clientTs);
        buf.putLong(8,  orderId);
        buf.putLong(16, accountId);
        buf.putLong(24, price);
        buf.putLong(32, qty);
        buf.put(40, side);
        buf.put(41, timeInForce);
        buf.put(42, orderType);
        // bytes 43-47 are zero (padding)
        return buf.array();
    }
}
