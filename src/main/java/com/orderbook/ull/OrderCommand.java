package com.orderbook.ull;

/**
 * Disruptor ring-buffer event for inbound order commands.
 *
 * One statically allocated instance per ring-buffer slot — fields are mutated
 * in-place by the publisher and read in-place by the handler.  No heap
 * allocation during steady-state operation.
 *
 * Command types:
 *   0 = LIMIT_ORDER
 *   1 = MARKET_ORDER
 *   2 = CANCEL
 *   3 = MASS_CANCEL
 *   4 = MASS_CANCEL_SIDE
 *   5 = KILL_SWITCH_ENGAGE
 *   6 = KILL_SWITCH_RELEASE
 */
public final class OrderCommand {

    public byte  type;

    // common fields
    public long idHigh;
    public long idLow;
    public long price;        // NO_PRICE for market orders
    public long qty;
    public byte side;         // 0=BUY 1=SELL
    public byte tif;          // 0=GTC 1=IOC 2=FOK
    public byte flags;        // bit 0=POST_ONLY bit 1=ICEBERG

    // user id (256-bit as 4 longs)
    public long u0, u1, u2, u3;

    // result written back by the handler (read by the publisher after barrier)
    public long resultCode;   // ErrorCode or executed qty

    public static final byte LIMIT_ORDER        = 0;
    public static final byte MARKET_ORDER       = 1;
    public static final byte CANCEL             = 2;
    public static final byte MASS_CANCEL        = 3;
    public static final byte MASS_CANCEL_SIDE   = 4;
    public static final byte KILL_ENGAGE        = 5;
    public static final byte KILL_RELEASE       = 6;

    /** Factory for Disruptor EventFactory. */
    public static final com.lmax.disruptor.EventFactory<OrderCommand> FACTORY = OrderCommand::new;

    public void reset() {
        type = 0; idHigh = idLow = price = qty = 0L;
        side = tif = flags = 0;
        u0 = u1 = u2 = u3 = 0L;
        resultCode = 0L;
    }
}
