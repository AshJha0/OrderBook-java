package com.orderbook.snapshot;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderbook.OrderBookException;

import java.security.MessageDigest;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * Point-in-time snapshot of an order book for market data and persistence.
 *
 * <p>Mirrors the Rust {@code OrderBookSnapshot} struct.</p>
 */
public final class OrderBookSnapshot {

    @JsonProperty("symbol")       public final String symbol;
    @JsonProperty("timestamp")    public final long timestamp;
    @JsonProperty("bids")         public final List<PriceLevelSnapshot> bids;
    @JsonProperty("asks")         public final List<PriceLevelSnapshot> asks;

    public OrderBookSnapshot(String symbol, long timestamp,
                             List<PriceLevelSnapshot> bids, List<PriceLevelSnapshot> asks) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.bids = List.copyOf(bids);
        this.asks = List.copyOf(asks);
    }

    public OptionalLong bestBid() {
        return bids.stream()
                .mapToLong(PriceLevelSnapshot::price)
                .max();
    }

    public OptionalLong bestAsk() {
        return asks.stream()
                .mapToLong(PriceLevelSnapshot::price)
                .min();
    }

    public OptionalDouble midPrice() {
        OptionalLong bid = bestBid();
        OptionalLong ask = bestAsk();
        if (bid.isPresent() && ask.isPresent()) {
            return OptionalDouble.of((bid.getAsLong() + (double) ask.getAsLong()) / 2.0);
        }
        return OptionalDouble.empty();
    }

    public OptionalLong spread() {
        OptionalLong bid = bestBid();
        OptionalLong ask = bestAsk();
        if (bid.isPresent() && ask.isPresent()) {
            return OptionalLong.of(Math.max(0L, ask.getAsLong() - bid.getAsLong()));
        }
        return OptionalLong.empty();
    }

    public long totalBidVolume() {
        return bids.stream().mapToLong(PriceLevelSnapshot::visibleQuantity).sum();
    }

    public long totalAskVolume() {
        return asks.stream().mapToLong(PriceLevelSnapshot::visibleQuantity).sum();
    }

    /** Serialize to JSON. */
    public String toJson() throws OrderBookException {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (Exception e) {
            throw new OrderBookException.SerializationError(e.getMessage(), e);
        }
    }

    /** Compute SHA-256 checksum of the JSON representation. */
    public String checksum() throws OrderBookException {
        try {
            byte[] data = toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new OrderBookException.SerializationError(e.getMessage(), e);
        }
    }
}
