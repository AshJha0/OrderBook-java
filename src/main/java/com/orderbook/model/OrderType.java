package com.orderbook.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed hierarchy of all supported order types.
 *
 * <p>Mirrors the Rust {@code OrderType<T>} enum. Java 21 sealed classes enforce
 * exhaustive pattern-matching at compile time — exactly like a Rust enum.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = OrderType.Standard.class,      name = "Standard"),
        @JsonSubTypes.Type(value = OrderType.Iceberg.class,       name = "IcebergOrder"),
        @JsonSubTypes.Type(value = OrderType.PostOnly.class,      name = "PostOnly"),
        @JsonSubTypes.Type(value = OrderType.FillOrKill.class,    name = "FillOrKill"),
        @JsonSubTypes.Type(value = OrderType.ImmediateOrCancel.class, name = "ImmediateOrCancel"),
        @JsonSubTypes.Type(value = OrderType.GoodTillDate.class,  name = "GoodTillDate"),
        @JsonSubTypes.Type(value = OrderType.TrailingStop.class,  name = "TrailingStop"),
        @JsonSubTypes.Type(value = OrderType.PeggedOrder.class,   name = "PeggedOrder"),
        @JsonSubTypes.Type(value = OrderType.MarketToLimit.class, name = "MarketToLimit"),
        @JsonSubTypes.Type(value = OrderType.Reserve.class,       name = "ReserveOrder"),
})
public sealed interface OrderType<T>
        permits OrderType.Standard, OrderType.Iceberg, OrderType.PostOnly,
                OrderType.FillOrKill, OrderType.ImmediateOrCancel,
                OrderType.GoodTillDate, OrderType.TrailingStop,
                OrderType.PeggedOrder, OrderType.MarketToLimit, OrderType.Reserve {

    Id id();
    Price price();
    Quantity quantity();
    Side side();
    Hash32 userId();
    TimestampMs timestamp();
    TimeInForce timeInForce();
    T extraFields();

    /** Total quantity (visible + hidden). */
    default Quantity totalQuantity() { return quantity(); }

    /** Visible quantity available for matching. */
    default Quantity visibleQuantity() { return quantity(); }

    // -------------------------------------------------------------------------
    // Concrete variants
    // -------------------------------------------------------------------------

    record Standard<T>(
            Id id,
            Price price,
            Quantity quantity,
            Side side,
            Hash32 userId,
            TimestampMs timestamp,
            TimeInForce timeInForce,
            T extraFields
    ) implements OrderType<T> {}

    record Iceberg<T>(
            Id id,
            Price price,
            Quantity visibleQuantity,
            Quantity hiddenQuantity,
            Side side,
            Hash32 userId,
            TimestampMs timestamp,
            TimeInForce timeInForce,
            T extraFields
    ) implements OrderType<T> {
        @Override public Quantity quantity() { return visibleQuantity; }
        @Override public Quantity totalQuantity() {
            return Quantity.of(visibleQuantity.value() + hiddenQuantity.value());
        }
    }

    record PostOnly<T>(
            Id id,
            Price price,
            Quantity quantity,
            Side side,
            Hash32 userId,
            TimestampMs timestamp,
            TimeInForce timeInForce,
            T extraFields
    ) implements OrderType<T> {}

    record FillOrKill<T>(
            Id id,
            Price price,
            Quantity quantity,
            Side side,
            Hash32 userId,
            TimestampMs timestamp,
            T extraFields
    ) implements OrderType<T> {
        @Override public TimeInForce timeInForce() { return TimeInForce.FOK; }
    }

    record ImmediateOrCancel<T>(
            Id id,
            Price price,
            Quantity quantity,
            Side side,
            Hash32 userId,
            TimestampMs timestamp,
            T extraFields
    ) implements OrderType<T> {
        @Override public TimeInForce timeInForce() { return TimeInForce.IOC; }
    }

    record GoodTillDate<T>(
            Id id,
            Price price,
            Quantity quantity,
            Side side,
            Hash32 userId,
            TimestampMs timestamp,
            TimestampMs expiryTime,
            T extraFields
    ) implements OrderType<T> {
        @Override public TimeInForce timeInForce() { return TimeInForce.GTD; }
    }

    record TrailingStop<T>(
            Id id,
            Price price,
            Quantity quantity,
            Side side,
            Hash32 userId,
            TimestampMs timestamp,
            TimeInForce timeInForce,
            long trailAmount,
            long lastReferencePrice,
            T extraFields
    ) implements OrderType<T> {}

    record PeggedOrder<T>(
            Id id,
            Price price,
            Quantity quantity,
            Side side,
            Hash32 userId,
            TimestampMs timestamp,
            TimeInForce timeInForce,
            long referencePriceOffset,
            T extraFields
    ) implements OrderType<T> {}

    record MarketToLimit<T>(
            Id id,
            Price price,
            Quantity quantity,
            Side side,
            Hash32 userId,
            TimestampMs timestamp,
            TimeInForce timeInForce,
            T extraFields
    ) implements OrderType<T> {}

    record Reserve<T>(
            Id id,
            Price price,
            Quantity visibleQuantity,
            Quantity hiddenQuantity,
            Side side,
            Hash32 userId,
            TimestampMs timestamp,
            TimeInForce timeInForce,
            long replenishThreshold,
            long replenishAmount,
            boolean autoReplenish,
            T extraFields
    ) implements OrderType<T> {
        @Override public Quantity quantity() { return visibleQuantity; }
        @Override public Quantity totalQuantity() {
            return Quantity.of(visibleQuantity.value() + hiddenQuantity.value());
        }
    }
}
