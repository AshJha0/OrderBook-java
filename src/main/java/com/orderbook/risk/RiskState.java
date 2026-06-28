package com.orderbook.risk;

import com.orderbook.OrderBookException;
import com.orderbook.model.Hash32;
import com.orderbook.model.Id;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-book risk state: optional config plus per-account counters and per-order entries.
 *
 * <p>When {@link #config} is {@code null}, every check is a passthrough.</p>
 */
public final class RiskState {

    private volatile RiskConfig config;
    private final ConcurrentHashMap<Hash32, RiskCounters> accountCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Id, RiskEntry> orderEntries = new ConcurrentHashMap<>();

    public RiskState() {}

    public void setConfig(RiskConfig config) { this.config = config; }
    public RiskConfig config() { return config; }

    /**
     * Check if a new limit order may be admitted.
     *
     * @param userId      account identifier
     * @param orderId     order being submitted
     * @param limitPrice  order price in raw ticks
     * @param quantity    order quantity
     * @param lastTradePrice last trade price (for price-band check, 0 if none)
     * @param bestBid     best bid price (for mid check, 0 if none)
     * @param bestAsk     best ask price (for mid check, 0 if none)
     */
    public void checkLimitAdmission(
            Hash32 userId,
            Id orderId,
            long limitPrice,
            long quantity,
            long lastTradePrice,
            long bestBid,
            long bestAsk
    ) throws OrderBookException {
        if (config == null || config.isDisabled()) return;

        long notional = limitPrice * quantity;
        RiskCounters counters = accountCounters.computeIfAbsent(userId, k -> new RiskCounters());

        if (config.maxOpenOrdersPerAccount() != null) {
            long current = counters.openOrders.get();
            if (current >= config.maxOpenOrdersPerAccount()) {
                throw new OrderBookException.OpenOrderLimitBreached(current, config.maxOpenOrdersPerAccount());
            }
        }

        if (config.maxNotionalPerAccount() != null) {
            long current = counters.notional.get();
            if (current + notional > config.maxNotionalPerAccount()) {
                throw new OrderBookException.NotionalLimitBreached(current + notional, config.maxNotionalPerAccount());
            }
        }

        if (config.priceBandBps() != null && config.referencePrice() != null) {
            long refPrice = resolveRefPrice(config.referencePrice(), lastTradePrice, bestBid, bestAsk);
            if (refPrice > 0) {
                long deviation = Math.abs(limitPrice - refPrice) * 10_000L / refPrice;
                if (deviation > config.priceBandBps()) {
                    throw new OrderBookException.RiskCheckFailed(
                            "Price " + limitPrice + " deviates " + deviation + " bps from reference " + refPrice);
                }
            }
        }
    }

    public void onOrderAdmitted(Hash32 userId, Id orderId, long notional) {
        if (config == null || config.isDisabled()) return;
        accountCounters.computeIfAbsent(userId, k -> new RiskCounters()).addOrder(notional);
        orderEntries.put(orderId, new RiskEntry(userId, notional));
    }

    public void onOrderRemoved(Id orderId) {
        if (config == null || config.isDisabled()) return;
        RiskEntry entry = orderEntries.remove(orderId);
        if (entry != null) {
            RiskCounters c = accountCounters.get(entry.userId());
            if (c != null) c.removeOrder(entry.notional());
        }
    }

    private long resolveRefPrice(ReferencePriceSource src, long lastTrade, long bestBid, long bestAsk) {
        return switch (src) {
            case ReferencePriceSource.LastTrade ignored -> lastTrade;
            case ReferencePriceSource.Mid ignored -> {
                if (bestBid > 0 && bestAsk > 0) yield (bestBid + bestAsk) / 2;
                yield lastTrade;
            }
            case ReferencePriceSource.FixedPrice fp -> fp.price();
        };
    }
}
