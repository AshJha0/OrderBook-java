package com.orderbook;

/**
 * Convenience re-exports of the most commonly used types.
 *
 * <p>In your code you can {@code import com.orderbook.Prelude.*} or simply
 * import the individual types from their own packages.</p>
 */
public final class Prelude {

    private Prelude() {}

    // Core model
    public static final Class<com.orderbook.model.Side>        Side        = com.orderbook.model.Side.class;
    public static final Class<com.orderbook.model.OrderType>   OrderType   = com.orderbook.model.OrderType.class;
    public static final Class<com.orderbook.model.TimeInForce> TimeInForce = com.orderbook.model.TimeInForce.class;
    public static final Class<com.orderbook.model.Id>          Id          = com.orderbook.model.Id.class;
    public static final Class<com.orderbook.model.Hash32>      Hash32      = com.orderbook.model.Hash32.class;
    public static final Class<com.orderbook.model.Price>       Price       = com.orderbook.model.Price.class;
    public static final Class<com.orderbook.model.Quantity>    Quantity    = com.orderbook.model.Quantity.class;
    public static final Class<com.orderbook.model.TimestampMs> TimestampMs = com.orderbook.model.TimestampMs.class;
    public static final Class<com.orderbook.model.MatchResult> MatchResult = com.orderbook.model.MatchResult.class;
    public static final Class<com.orderbook.model.TradeResult> TradeResult = com.orderbook.model.TradeResult.class;
    public static final Class<com.orderbook.model.Clock>       Clock       = com.orderbook.model.Clock.class;

    // Fees / STP
    public static final Class<com.orderbook.fees.FeeSchedule>  FeeSchedule = com.orderbook.fees.FeeSchedule.class;
    public static final Class<com.orderbook.stp.STPMode>       STPMode     = com.orderbook.stp.STPMode.class;

    // Book
    public static final Class<OrderBook>                        OrderBook   = OrderBook.class;
    public static final Class<OrderBookException>               Error       = OrderBookException.class;
}
