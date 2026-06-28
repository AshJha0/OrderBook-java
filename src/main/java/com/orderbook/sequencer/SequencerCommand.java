package com.orderbook.sequencer;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.orderbook.model.Hash32;
import com.orderbook.model.Id;
import com.orderbook.model.OrderType;
import com.orderbook.model.Side;

/**
 * Commands submitted to the sequencer for total-ordered execution.
 *
 * <p>Mirrors the Rust {@code SequencerCommand<T>} enum.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SequencerCommand.AddOrder.class,          name = "AddOrder"),
        @JsonSubTypes.Type(value = SequencerCommand.CancelOrder.class,       name = "CancelOrder"),
        @JsonSubTypes.Type(value = SequencerCommand.MarketOrder.class,       name = "MarketOrder"),
        @JsonSubTypes.Type(value = SequencerCommand.MarketOrderByAmount.class, name = "MarketOrderByAmount"),
        @JsonSubTypes.Type(value = SequencerCommand.CancelAll.class,         name = "CancelAll"),
        @JsonSubTypes.Type(value = SequencerCommand.CancelBySide.class,      name = "CancelBySide"),
        @JsonSubTypes.Type(value = SequencerCommand.CancelByUser.class,      name = "CancelByUser"),
        @JsonSubTypes.Type(value = SequencerCommand.CancelByPriceRange.class, name = "CancelByPriceRange"),
})
public sealed interface SequencerCommand<T>
        permits SequencerCommand.AddOrder, SequencerCommand.CancelOrder,
                SequencerCommand.MarketOrder, SequencerCommand.MarketOrderByAmount,
                SequencerCommand.CancelAll, SequencerCommand.CancelBySide,
                SequencerCommand.CancelByUser, SequencerCommand.CancelByPriceRange {

    record AddOrder<T>(OrderType<T> order) implements SequencerCommand<T> {}

    record CancelOrder<T>(Id orderId) implements SequencerCommand<T> {}

    record MarketOrder<T>(Id id, long quantity, Side side) implements SequencerCommand<T> {}

    record MarketOrderByAmount<T>(Id id, long amount, Side side) implements SequencerCommand<T> {}

    record CancelAll<T>() implements SequencerCommand<T> {}

    record CancelBySide<T>(Side side) implements SequencerCommand<T> {}

    record CancelByUser<T>(Hash32 userId) implements SequencerCommand<T> {}

    record CancelByPriceRange<T>(Side side, long minPrice, long maxPrice) implements SequencerCommand<T> {}
}
