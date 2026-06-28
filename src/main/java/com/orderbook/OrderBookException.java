package com.orderbook;

import com.orderbook.model.Id;
import com.orderbook.model.Side;
import com.orderbook.stp.STPMode;

/**
 * Exception hierarchy for order book errors.
 *
 * <p>Uses sealed subclasses so callers can pattern-match exhaustively.</p>
 */
public sealed class OrderBookException extends Exception
        permits OrderBookException.OrderNotFound,
                OrderBookException.InvalidPriceLevel,
                OrderBookException.PriceCrossing,
                OrderBookException.InsufficientLiquidity,
                OrderBookException.InsufficientLiquidityNotional,
                OrderBookException.InvalidOperation,
                OrderBookException.KillSwitchActive,
                OrderBookException.SerializationError,
                OrderBookException.DeserializationError,
                OrderBookException.ChecksumMismatch,
                OrderBookException.InvalidTickSize,
                OrderBookException.InvalidLotSize,
                OrderBookException.OrderSizeOutOfRange,
                OrderBookException.DuplicateOrderId,
                OrderBookException.MissingUserId,
                OrderBookException.SelfTradePrevented,
                OrderBookException.OpenOrderLimitBreached,
                OrderBookException.NotionalLimitBreached,
                OrderBookException.RiskCheckFailed,
                OrderBookException.ExpiredOrder {

    protected OrderBookException(String message) { super(message); }
    protected OrderBookException(String message, Throwable cause) { super(message, cause); }

    public static final class OrderNotFound extends OrderBookException {
        public final String orderId;
        public OrderNotFound(String orderId) {
            super("Order not found: " + orderId);
            this.orderId = orderId;
        }
    }

    public static final class InvalidPriceLevel extends OrderBookException {
        public final long price;
        public InvalidPriceLevel(long price) {
            super("Invalid price level: " + Long.toUnsignedString(price));
            this.price = price;
        }
    }

    public static final class PriceCrossing extends OrderBookException {
        public final long price;
        public final Side side;
        public final long oppositePrice;
        public PriceCrossing(long price, Side side, long oppositePrice) {
            super("Price crossing: price=" + Long.toUnsignedString(price) + " side=" + side
                    + " opposite=" + Long.toUnsignedString(oppositePrice));
            this.price = price;
            this.side = side;
            this.oppositePrice = oppositePrice;
        }
    }

    public static final class InsufficientLiquidity extends OrderBookException {
        public final Side side;
        public final long requested;
        public final long available;
        public InsufficientLiquidity(Side side, long requested, long available) {
            super("Insufficient liquidity on " + side + ": requested=" + requested + " available=" + available);
            this.side = side;
            this.requested = requested;
            this.available = available;
        }
    }

    public static final class InsufficientLiquidityNotional extends OrderBookException {
        public final Side side;
        public final long requested;
        public final long spent;
        public InsufficientLiquidityNotional(Side side, long requested, long spent) {
            super("Insufficient notional liquidity on " + side + ": requested=" + requested + " spent=" + spent);
            this.side = side;
            this.requested = requested;
            this.spent = spent;
        }
    }

    public static final class InvalidOperation extends OrderBookException {
        public InvalidOperation(String message) { super(message); }
    }

    public static final class KillSwitchActive extends OrderBookException {
        public KillSwitchActive() { super("Kill switch is active; new order flow is rejected"); }
    }

    public static final class SerializationError extends OrderBookException {
        public SerializationError(String message) { super("Serialization error: " + message); }
        public SerializationError(String message, Throwable cause) { super("Serialization error: " + message, cause); }
    }

    public static final class DeserializationError extends OrderBookException {
        public DeserializationError(String message) { super("Deserialization error: " + message); }
        public DeserializationError(String message, Throwable cause) { super("Deserialization error: " + message, cause); }
    }

    public static final class ChecksumMismatch extends OrderBookException {
        public final String expected;
        public final String actual;
        public ChecksumMismatch(String expected, String actual) {
            super("Checksum mismatch: expected=" + expected + " actual=" + actual);
            this.expected = expected;
            this.actual = actual;
        }
    }

    public static final class InvalidTickSize extends OrderBookException {
        public final long price;
        public final long tickSize;
        public InvalidTickSize(long price, long tickSize) {
            super("Price " + Long.toUnsignedString(price) + " is not a multiple of tick size " + Long.toUnsignedString(tickSize));
            this.price = price;
            this.tickSize = tickSize;
        }
    }

    public static final class InvalidLotSize extends OrderBookException {
        public final long quantity;
        public final long lotSize;
        public InvalidLotSize(long quantity, long lotSize) {
            super("Quantity " + quantity + " is not a multiple of lot size " + lotSize);
            this.quantity = quantity;
            this.lotSize = lotSize;
        }
    }

    public static final class OrderSizeOutOfRange extends OrderBookException {
        public final long quantity;
        public final Long min;
        public final Long max;
        public OrderSizeOutOfRange(long quantity, Long min, Long max) {
            super("Order quantity " + quantity + " out of range [" + min + ", " + max + "]");
            this.quantity = quantity;
            this.min = min;
            this.max = max;
        }
    }

    public static final class DuplicateOrderId extends OrderBookException {
        public final Id orderId;
        public DuplicateOrderId(Id orderId) {
            super("Duplicate order id: " + orderId);
            this.orderId = orderId;
        }
    }

    public static final class MissingUserId extends OrderBookException {
        public final Id orderId;
        public MissingUserId(Id orderId) {
            super("Missing user id on order " + orderId + " when STP is enabled");
            this.orderId = orderId;
        }
    }

    public static final class SelfTradePrevented extends OrderBookException {
        public final STPMode mode;
        public final Id takerOrderId;
        public final com.orderbook.model.Hash32 userId;
        public SelfTradePrevented(STPMode mode, Id takerOrderId, com.orderbook.model.Hash32 userId) {
            super("Self-trade prevented [" + mode + "]: taker=" + takerOrderId + " user=" + userId);
            this.mode = mode;
            this.takerOrderId = takerOrderId;
            this.userId = userId;
        }
    }

    public static final class OpenOrderLimitBreached extends OrderBookException {
        public final long current;
        public final long limit;
        public OpenOrderLimitBreached(long current, long limit) {
            super("Open order limit breached: current=" + current + " limit=" + limit);
            this.current = current;
            this.limit = limit;
        }
    }

    public static final class NotionalLimitBreached extends OrderBookException {
        public final long current;
        public final long limit;
        public NotionalLimitBreached(long current, long limit) {
            super("Notional limit breached: current=" + current + " limit=" + limit);
            this.current = current;
            this.limit = limit;
        }
    }

    public static final class RiskCheckFailed extends OrderBookException {
        public RiskCheckFailed(String reason) { super("Risk check failed: " + reason); }
    }

    public static final class ExpiredOrder extends OrderBookException {
        public final Id orderId;
        public ExpiredOrder(Id orderId) {
            super("Order has expired: " + orderId);
            this.orderId = orderId;
        }
    }
}
