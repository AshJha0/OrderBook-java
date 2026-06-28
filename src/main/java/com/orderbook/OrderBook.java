package com.orderbook;

import com.orderbook.fees.FeeSchedule;
import com.orderbook.model.*;
import com.orderbook.risk.RiskConfig;
import com.orderbook.risk.RiskState;
import com.orderbook.stp.STPAction;
import com.orderbook.stp.STPChecker;
import com.orderbook.stp.STPMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * High-performance limit order book with price-time priority matching.
 *
 * <p>Mirrors the Rust {@code OrderBook<T>} struct. Uses
 * {@link ConcurrentSkipListMap} for ordered bid/ask price levels and
 * {@link ConcurrentHashMap} for O(1) order and user lookups.</p>
 *
 * <p>The bid side is sorted descending (highest price first for matching);
 * the ask side is sorted ascending (lowest price first). Keys are raw price
 * longs compared as unsigned values via {@link Long#compareUnsigned}.</p>
 *
 * @param <T> application-specific extra fields attached to each order
 */
public final class OrderBook<T> {

    private static final Logger log = LoggerFactory.getLogger(OrderBook.class);

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final String symbol;

    /** Bids: highest price first (descending unsigned comparator). */
    private final ConcurrentSkipListMap<Long, PriceLevelEntry<T>> bids =
            new ConcurrentSkipListMap<>((a, b) -> Long.compareUnsigned(b, a));

    /** Asks: lowest price first (ascending unsigned comparator). */
    private final ConcurrentSkipListMap<Long, PriceLevelEntry<T>> asks =
            new ConcurrentSkipListMap<>(Long::compareUnsigned);

    /** orderId → (price, side) for fast lookup. */
    private final ConcurrentHashMap<Id, long[]> orderLocations = new ConcurrentHashMap<>();
    // long[0] = price (raw), long[1] = 0 for BUY / 1 for SELL

    /** userId → list of order ids. */
    private final ConcurrentHashMap<Hash32, List<Id>> userOrders = new ConcurrentHashMap<>();

    /** Monotonic outbound sequence counter. */
    private final AtomicLong engineSeq = new AtomicLong(0);

    /** Operational kill switch. */
    private final AtomicBoolean killSwitch = new AtomicBoolean(false);

    /** Last trade price (0 = no trade yet). */
    private volatile long lastTradePrice = 0L;

    /** Whether a trade has occurred. */
    private final AtomicBoolean hasTraded = new AtomicBoolean(false);

    /** DAY order expiry timestamp (0 = not set). */
    private volatile long marketCloseTimestamp = 0L;

    // --- Configuration ---
    private Long tickSize;
    private Long lotSize;
    private Long minOrderSize;
    private Long maxOrderSize;
    private STPMode stpMode = STPMode.NONE;
    private FeeSchedule feeSchedule;

    // --- Optional components ---
    private Consumer<TradeResult> tradeListener;
    private Consumer<PriceLevelChangedEvent> priceLevelChangedListener;
    private OrderStateTracker orderStateTracker;
    private Clock clock;
    private final RiskState riskState = new RiskState();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public OrderBook(String symbol) {
        this(symbol, Clock.MonotonicClock.INSTANCE);
    }

    public OrderBook(String symbol, Clock clock) {
        this.symbol = symbol;
        this.clock = clock;
    }

    // -------------------------------------------------------------------------
    // Configuration setters (fluent)
    // -------------------------------------------------------------------------

    public OrderBook<T> withClock(Clock clock)               { this.clock = clock; return this; }
    public OrderBook<T> withTickSize(long ticks)             { this.tickSize = ticks; return this; }
    public OrderBook<T> withLotSize(long lots)               { this.lotSize = lots; return this; }
    public OrderBook<T> withMinOrderSize(long min)           { this.minOrderSize = min; return this; }
    public OrderBook<T> withMaxOrderSize(long max)           { this.maxOrderSize = max; return this; }
    public OrderBook<T> withSTPMode(STPMode mode)            { this.stpMode = mode; return this; }
    public OrderBook<T> withFeeSchedule(FeeSchedule fs)      { this.feeSchedule = fs; return this; }
    public OrderBook<T> withTradeListener(Consumer<TradeResult> l)             { this.tradeListener = l; return this; }
    public OrderBook<T> withPriceLevelChangedListener(Consumer<PriceLevelChangedEvent> l) {
        this.priceLevelChangedListener = l; return this;
    }
    public OrderBook<T> withOrderStateTracker(OrderStateTracker t)  { this.orderStateTracker = t; return this; }
    public OrderBook<T> withRiskConfig(RiskConfig cfg)              { riskState.setConfig(cfg); return this; }
    public OrderBook<T> withMarketCloseTimestamp(long ts)           { this.marketCloseTimestamp = ts; return this; }

    // -------------------------------------------------------------------------
    // Kill switch
    // -------------------------------------------------------------------------

    public void engageKillSwitch()   { killSwitch.set(true); }
    public void releaseKillSwitch()  { killSwitch.set(false); }
    public boolean isKillSwitchEngaged() { return killSwitch.get(); }

    // -------------------------------------------------------------------------
    // Sequence counter
    // -------------------------------------------------------------------------

    public long nextEngineSeq() { return engineSeq.getAndIncrement(); }
    public long engineSeq()     { return engineSeq.get(); }

    // -------------------------------------------------------------------------
    // Order submission — high-level API
    // -------------------------------------------------------------------------

    /**
     * Submit an order. Runs kill-switch, validation, risk, STP, fee, and match.
     *
     * @return {@link TradeResult} describing fills (may be empty if nothing matched)
     */
    public TradeResult submitOrder(OrderType<T> order) throws OrderBookException {
        checkKillSwitchOrReject(order.id());
        validateOrder(order);

        if (stpMode.isEnabled() && order.userId().isZero()) {
            trackState(order.id(), new OrderStatus.Rejected(RejectReason.MISSING_USER_ID,
                    TimestampMs.of(clock.nowMs())));
            throw new OrderBookException.MissingUserId(order.id());
        }

        if (orderLocations.containsKey(order.id())) {
            trackState(order.id(), new OrderStatus.Rejected(RejectReason.DUPLICATE_ORDER_ID,
                    TimestampMs.of(clock.nowMs())));
            throw new OrderBookException.DuplicateOrderId(order.id());
        }

        riskState.checkLimitAdmission(
                order.userId(), order.id(),
                order.price().value(), order.quantity().value(),
                lastTradePrice,
                bestBid().map(Price::value).orElse(0L),
                bestAsk().map(Price::value).orElse(0L)
        );

        return addOrderInternal(order);
    }

    /**
     * Submit a market order — matches immediately at best available prices.
     *
     * @param side     BUY or SELL
     * @param quantity quantity to fill
     * @param userId   user id (for STP checks)
     * @return TradeResult with fills
     */
    public TradeResult submitMarketOrder(Side side, long quantity, Hash32 userId)
            throws OrderBookException {
        if (killSwitch.get()) throw new OrderBookException.KillSwitchActive();

        Id id = Id.newUuid();
        MatchResult mr = matchMarketOrder(id, side, quantity, userId);
        TradeResult result = feeSchedule != null
                ? TradeResult.withFees(symbol, mr, feeSchedule)
                : TradeResult.of(symbol, mr);
        result.engineSeq = nextEngineSeq();
        if (tradeListener != null) tradeListener.accept(result);
        return result;
    }

    // -------------------------------------------------------------------------
    // Lower-level match entry points
    // -------------------------------------------------------------------------

    /**
     * Match a limit order: first try to fill as a taker, then rest the residual.
     */
    private TradeResult addOrderInternal(OrderType<T> order) throws OrderBookException {
        Id id = order.id();
        long qty = order.quantity().value();
        long limitPrice = order.price().value();
        Side side = order.side();

        // FOK pre-check: verify sufficient liquidity before touching the book
        if (order instanceof OrderType.FillOrKill<T>) {
            if (!canFillFok(order.price(), side, qty)) {
                trackState(id, new OrderStatus.Cancelled(CancelReason.FOK_UNFULFILLABLE,
                        TimestampMs.of(clock.nowMs())));
                MatchResult empty = new MatchResult(id, Quantity.of(qty));
                TradeResult res = feeSchedule != null
                        ? TradeResult.withFees(symbol, empty, feeSchedule)
                        : TradeResult.of(symbol, empty);
                res.engineSeq = nextEngineSeq();
                return res;
            }
        }

        MatchResult mr = new MatchResult(id, Quantity.of(qty));

        boolean takerStpCancelled = matchAsLimitTaker(order, mr);

        long remaining = mr.remainingQuantity().value();

        // IOC/FOK residual handling
        if (remaining > 0 && !takerStpCancelled) {
            boolean shouldRest = switch (order.timeInForce()) {
                case IOC -> false;
                case FOK -> false;
                case GTC, DAY, GTD -> true;
            };

            if (!shouldRest) {
                // IOC/FOK: cancel the residual
                trackState(id, new OrderStatus.Cancelled(
                        order.timeInForce() == TimeInForce.FOK
                                ? CancelReason.FOK_UNFULFILLABLE
                                : CancelReason.IOC_RESIDUAL,
                        TimestampMs.of(clock.nowMs())));
            } else {
                // Rest the residual
                restOrder(order, remaining);
                long filled = qty - remaining;
                if (filled > 0) {
                    trackState(id, new OrderStatus.PartiallyFilled(filled, remaining,
                            TimestampMs.of(clock.nowMs())));
                } else {
                    trackState(id, new OrderStatus.Open(TimestampMs.of(clock.nowMs())));
                }
                riskState.onOrderAdmitted(order.userId(), id, limitPrice * remaining);
                addToUserOrders(order.userId(), id);
            }
        } else if (remaining == 0) {
            trackState(id, new OrderStatus.Filled(qty, TimestampMs.of(clock.nowMs())));
        } else {
            // taker was STP-cancelled
            trackState(id, new OrderStatus.Cancelled(CancelReason.STP_CANCEL_TAKER,
                    TimestampMs.of(clock.nowMs())));
        }

        TradeResult result = feeSchedule != null
                ? TradeResult.withFees(symbol, mr, feeSchedule)
                : TradeResult.of(symbol, mr);
        result.engineSeq = nextEngineSeq();
        if (tradeListener != null) tradeListener.accept(result);
        return result;
    }

    /**
     * Walk the opposite side and fill as many lots as possible.
     *
     * @return true if the taker was STP-cancelled and must NOT rest
     */
    private boolean matchAsLimitTaker(OrderType<T> order, MatchResult mr) throws OrderBookException {
        Side takerSide = order.side();
        long limitPrice = order.price().value();
        var oppSide = takerSide == Side.BUY ? asks : bids;

        long[] needed = {order.quantity().value()};
        boolean takerCancelled = false;

        var it = oppSide.entrySet().iterator();
        while (it.hasNext() && needed[0] > 0) {
            var entry = it.next();
            long levelPrice = entry.getKey();

            // Price check
            if (takerSide == Side.BUY && Long.compareUnsigned(levelPrice, limitPrice) > 0) break;
            if (takerSide == Side.SELL && Long.compareUnsigned(levelPrice, limitPrice) < 0) break;

            PriceLevelEntry<T> level = entry.getValue();
            if (level.isEmpty()) { it.remove(); continue; }

            // STP check
            if (stpMode.isEnabled()) {
                List<OrderType<T>> snap = level.snapshot();
                STPAction action = STPChecker.checkAtLevel(snap, order.userId(), stpMode);

                switch (action) {
                    case STPAction.NoConflict ignored -> {}
                    case STPAction.CancelTaker ct -> {
                        needed[0] = ct.safeQuantity();
                        if (needed[0] > 0) {
                            level.matchAgainst(order.id(), takerSide, needed, mr, Id::newUuid);
                        }
                        takerCancelled = true;
                        emitPriceLevelChange(takerSide.opposite(), levelPrice, level.visibleQuantity());
                        if (needed[0] == 0) needed[0] = 0; // sentinel to stop loop
                        // break outer loop after this level
                        it = Collections.<Map.Entry<Long, PriceLevelEntry<T>>>emptyList().iterator();
                        continue;
                    }
                    case STPAction.CancelMaker ignored -> {
                        cancelSameuserMakers(level, order.userId());
                        if (level.isEmpty()) { it.remove(); continue; }
                    }
                    case STPAction.CancelBoth cb -> {
                        needed[0] = cb.safeQuantity();
                        if (needed[0] > 0) {
                            level.matchAgainst(order.id(), takerSide, needed, mr, Id::newUuid);
                        }
                        cancelOrder(cb.makerOrderId());
                        takerCancelled = true;
                        emitPriceLevelChange(takerSide.opposite(), levelPrice, level.visibleQuantity());
                        it = Collections.<Map.Entry<Long, PriceLevelEntry<T>>>emptyList().iterator();
                        continue;
                    }
                }
            }

            level.matchAgainst(order.id(), takerSide, needed, mr, Id::newUuid);
            emitPriceLevelChange(takerSide.opposite(), levelPrice, level.visibleQuantity());
            if (level.isEmpty()) it.remove();
        }

        return takerCancelled;
    }

    /** Cancel all same-user orders at a price level (CancelMaker STP). */
    private void cancelSameuserMakers(PriceLevelEntry<T> level, Hash32 userId) {
        for (OrderType<T> o : level.snapshot()) {
            if (o.userId().equals(userId)) {
                level.removeOrder(o.id());
                orderLocations.remove(o.id());
                removeFromUserOrders(userId, o.id());
                riskState.onOrderRemoved(o.id());
                trackState(o.id(), new OrderStatus.Cancelled(CancelReason.STP_CANCEL_MAKER,
                        TimestampMs.of(clock.nowMs())));
            }
        }
    }

    /** Match a market order against the book. */
    private MatchResult matchMarketOrder(Id id, Side side, long quantity, Hash32 userId) {
        MatchResult mr = new MatchResult(id, Quantity.of(quantity));
        var oppSide = side == Side.BUY ? asks : bids;

        long[] needed = {quantity};
        var it = oppSide.entrySet().iterator();
        while (it.hasNext() && needed[0] > 0) {
            var entry = it.next();
            long levelPrice = entry.getKey();
            PriceLevelEntry<T> level = entry.getValue();
            if (level.isEmpty()) { it.remove(); continue; }

            level.matchAgainst(id, side, needed, mr, Id::newUuid);
            emitPriceLevelChange(side.opposite(), levelPrice, level.visibleQuantity());
            if (level.isEmpty()) it.remove();
        }
        return mr;
    }

    // -------------------------------------------------------------------------
    // Resting orders
    // -------------------------------------------------------------------------

    private void restOrder(OrderType<T> order, long remainingQty) {
        long price = order.price().value();
        Side side = order.side();
        var sideMap = side == Side.BUY ? bids : asks;

        // Store reduced-quantity variant if partial fill happened
        OrderType<T> toRest = order;
        if (remainingQty < order.quantity().value()) {
            toRest = withReducedQty(order, remainingQty);
        }

        PriceLevelEntry<T> level = sideMap.computeIfAbsent(price, p -> new PriceLevelEntry<>(Price.of(p)));
        level.addOrder(toRest);

        orderLocations.put(order.id(), new long[]{price, side == Side.BUY ? 0L : 1L});
        emitPriceLevelChange(side, price, level.visibleQuantity());
    }

    @SuppressWarnings("unchecked")
    private OrderType<T> withReducedQty(OrderType<T> order, long qty) {
        Quantity q = Quantity.of(qty);
        return switch (order) {
            case OrderType.Standard<T> o -> new OrderType.Standard<>(
                    o.id(), o.price(), q, o.side(), o.userId(), o.timestamp(), o.timeInForce(), o.extraFields());
            case OrderType.Iceberg<T> o -> new OrderType.Iceberg<>(
                    o.id(), o.price(), q, o.hiddenQuantity(), o.side(), o.userId(), o.timestamp(), o.timeInForce(), o.extraFields());
            case OrderType.PostOnly<T> o -> new OrderType.PostOnly<>(
                    o.id(), o.price(), q, o.side(), o.userId(), o.timestamp(), o.timeInForce(), o.extraFields());
            case OrderType.FillOrKill<T> o -> new OrderType.FillOrKill<>(
                    o.id(), o.price(), q, o.side(), o.userId(), o.timestamp(), o.extraFields());
            case OrderType.ImmediateOrCancel<T> o -> new OrderType.ImmediateOrCancel<>(
                    o.id(), o.price(), q, o.side(), o.userId(), o.timestamp(), o.extraFields());
            case OrderType.GoodTillDate<T> o -> new OrderType.GoodTillDate<>(
                    o.id(), o.price(), q, o.side(), o.userId(), o.timestamp(), o.expiryTime(), o.extraFields());
            case OrderType.TrailingStop<T> o -> new OrderType.TrailingStop<>(
                    o.id(), o.price(), q, o.side(), o.userId(), o.timestamp(), o.timeInForce(), o.trailAmount(), o.lastReferencePrice(), o.extraFields());
            case OrderType.PeggedOrder<T> o -> new OrderType.PeggedOrder<>(
                    o.id(), o.price(), q, o.side(), o.userId(), o.timestamp(), o.timeInForce(), o.referencePriceOffset(), o.extraFields());
            case OrderType.MarketToLimit<T> o -> new OrderType.MarketToLimit<>(
                    o.id(), o.price(), q, o.side(), o.userId(), o.timestamp(), o.timeInForce(), o.extraFields());
            case OrderType.Reserve<T> o -> new OrderType.Reserve<>(
                    o.id(), o.price(), q, o.hiddenQuantity(), o.side(), o.userId(), o.timestamp(), o.timeInForce(), o.replenishThreshold(), o.replenishAmount(), o.autoReplenish(), o.extraFields());
        };
    }

    // -------------------------------------------------------------------------
    // Cancellation
    // -------------------------------------------------------------------------

    /**
     * Cancel an order by id.
     *
     * @throws OrderBookException.OrderNotFound if the order is not resting
     */
    public void cancelOrder(Id orderId) throws OrderBookException {
        long[] loc = orderLocations.remove(orderId);
        if (loc == null) throw new OrderBookException.OrderNotFound(orderId.toString());

        long price = loc[0];
        Side side = loc[1] == 0 ? Side.BUY : Side.SELL;
        var sideMap = side == Side.BUY ? bids : asks;

        PriceLevelEntry<T> level = sideMap.get(price);
        if (level != null) {
            level.removeOrder(orderId);
            if (level.isEmpty()) sideMap.remove(price);
            emitPriceLevelChange(side, price, level.visibleQuantity());
        }

        // Clean up user orders index
        // We don't know the userId from loc, so scan userOrders (acceptable since cancel is rare)
        userOrders.forEach((uid, ids) -> ids.remove(orderId));

        riskState.onOrderRemoved(orderId);
        trackState(orderId, new OrderStatus.Cancelled(CancelReason.USER_REQUESTED,
                TimestampMs.of(clock.nowMs())));
    }

    /**
     * Mass-cancel all resting orders for a given user.
     *
     * @return number of orders cancelled
     */
    public int massCancelUser(Hash32 userId) {
        List<Id> ids = userOrders.remove(userId);
        if (ids == null) return 0;
        int count = 0;
        for (Id id : ids) {
            try { cancelOrder(id); count++; }
            catch (OrderBookException ignored) {}
        }
        return count;
    }

    /**
     * Cancel all resting orders on both sides (drain the book).
     *
     * @return number of orders cancelled
     */
    public int massCancel() {
        int count = 0;
        for (var side : List.of(bids, asks)) {
            for (var entry : List.copyOf(side.entrySet())) {
                for (OrderType<T> o : entry.getValue().snapshot()) {
                    try { cancelOrder(o.id()); count++; }
                    catch (OrderBookException ignored) {}
                }
            }
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Order modification
    // -------------------------------------------------------------------------

    /**
     * Modify the quantity of a resting order.
     * Reducing quantity preserves time priority; increasing loses it.
     */
    public void modifyOrderQuantity(Id orderId, long newQuantity) throws OrderBookException {
        checkKillSwitch();
        long[] loc = orderLocations.get(orderId);
        if (loc == null) throw new OrderBookException.OrderNotFound(orderId.toString());

        long price = loc[0];
        Side side = loc[1] == 0 ? Side.BUY : Side.SELL;
        var sideMap = side == Side.BUY ? bids : asks;

        PriceLevelEntry<T> level = sideMap.get(price);
        if (level == null) throw new OrderBookException.OrderNotFound(orderId.toString());

        // Find and replace the order in the level
        List<OrderType<T>> snap = level.snapshot();
        for (OrderType<T> o : snap) {
            if (o.id().equals(orderId)) {
                level.removeOrder(orderId);
                OrderType<T> updated = withReducedQty(o, newQuantity);
                level.addOrder(updated); // always append (loses time priority for increases)
                emitPriceLevelChange(side, price, level.visibleQuantity());
                return;
            }
        }
        throw new OrderBookException.OrderNotFound(orderId.toString());
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public String symbol() { return symbol; }

    public Optional<Price> bestBid() {
        var entry = bids.firstEntry();
        return entry == null ? Optional.empty() : Optional.of(Price.of(entry.getKey()));
    }

    public Optional<Price> bestAsk() {
        var entry = asks.firstEntry();
        return entry == null ? Optional.empty() : Optional.of(Price.of(entry.getKey()));
    }

    public Optional<Long> spread() {
        Optional<Price> bid = bestBid();
        Optional<Price> ask = bestAsk();
        if (bid.isEmpty() || ask.isEmpty()) return Optional.empty();
        return Optional.of(ask.get().value() - bid.get().value());
    }

    public long lastTradePrice() { return lastTradePrice; }
    public boolean hasTraded() { return hasTraded.get(); }

    /** Depth on one side: list of (price, totalQty) pairs in priority order. */
    public List<long[]> depth(Side side, int maxLevels) {
        var sideMap = side == Side.BUY ? bids : asks;
        List<long[]> result = new ArrayList<>();
        for (var entry : sideMap.entrySet()) {
            if (result.size() >= maxLevels) break;
            long qty = entry.getValue().visibleQuantity();
            if (qty > 0) result.add(new long[]{entry.getKey(), qty});
        }
        return result;
    }

    public int bidLevelCount() { return bids.size(); }
    public int askLevelCount() { return asks.size(); }
    public int totalOrderCount() { return orderLocations.size(); }

    /** Snapshot of all resting orders on one side, sorted by priority. */
    public List<OrderType<T>> restingOrders(Side side) {
        var sideMap = side == Side.BUY ? bids : asks;
        List<OrderType<T>> result = new ArrayList<>();
        for (var level : sideMap.values()) result.addAll(level.snapshot());
        return result;
    }

    /** Returns depth entries as [price, visibleQty, orderCount] triples. */
    public List<long[]> depthEntries(Side side) {
        var sideMap = side == Side.BUY ? bids : asks;
        List<long[]> result = new ArrayList<>();
        for (var entry : sideMap.entrySet()) {
            var level = entry.getValue();
            result.add(new long[]{entry.getKey(), level.visibleQuantity(), level.size()});
        }
        return result;
    }

    // Package-level getters used by OrderBookSnapshotter
    STPMode stpMode() { return stpMode; }
    Long tickSize() { return tickSize; }
    Long lotSize() { return lotSize; }
    Long minOrderSize() { return minOrderSize; }
    Long maxOrderSize() { return maxOrderSize; }
    com.orderbook.fees.FeeSchedule feeSchedule() { return feeSchedule; }
    com.orderbook.risk.RiskConfig riskConfig() { return riskState.config(); }

    /** Returns true if there is enough resting liquidity to fully fill a FOK of {@code qty} at {@code price}. */
    private boolean canFillFok(Price price, Side side, long qty) {
        var oppSide = side == Side.BUY ? asks : bids;
        long available = 0L;
        for (var entry : oppSide.entrySet()) {
            long levelPrice = entry.getKey();
            if (side == Side.BUY && Long.compareUnsigned(levelPrice, price.value()) > 0) break;
            if (side == Side.SELL && Long.compareUnsigned(levelPrice, price.value()) < 0) break;
            available += entry.getValue().visibleQuantity();
            if (available >= qty) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Validation helpers
    // -------------------------------------------------------------------------

    private void validateOrder(OrderType<T> order) throws OrderBookException {
        long price = order.price().value();
        long qty = order.quantity().value();

        if (tickSize != null && price % tickSize != 0) {
            throw new OrderBookException.InvalidTickSize(price, tickSize);
        }
        if (lotSize != null && qty % lotSize != 0) {
            throw new OrderBookException.InvalidLotSize(qty, lotSize);
        }
        if (minOrderSize != null && qty < minOrderSize) {
            throw new OrderBookException.OrderSizeOutOfRange(qty, minOrderSize, maxOrderSize);
        }
        if (maxOrderSize != null && qty > maxOrderSize) {
            throw new OrderBookException.OrderSizeOutOfRange(qty, minOrderSize, maxOrderSize);
        }

        // PostOnly: reject if it would immediately match
        if (order instanceof OrderType.PostOnly<T> && wouldMatch(order.price(), order.side())) {
            throw new OrderBookException.InvalidOperation(
                    "PostOnly order at price " + price + " would immediately match");
        }

        // GTD: check expiry
        if (order instanceof OrderType.GoodTillDate<T> gtd) {
            if (gtd.expiryTime().value() <= clock.nowMs()) {
                throw new OrderBookException.ExpiredOrder(order.id());
            }
        }
    }

    private boolean wouldMatch(Price price, Side side) {
        if (side == Side.BUY) {
            var entry = asks.firstEntry();
            return entry != null && Long.compareUnsigned(entry.getKey(), price.value()) <= 0;
        } else {
            var entry = bids.firstEntry();
            return entry != null && Long.compareUnsigned(entry.getKey(), price.value()) >= 0;
        }
    }

    private void checkKillSwitchOrReject(Id orderId) throws OrderBookException {
        if (killSwitch.get()) {
            trackState(orderId, new OrderStatus.Rejected(RejectReason.KILL_SWITCH_ACTIVE,
                    TimestampMs.of(clock.nowMs())));
            throw new OrderBookException.KillSwitchActive();
        }
    }

    private void checkKillSwitch() throws OrderBookException {
        if (killSwitch.get()) throw new OrderBookException.KillSwitchActive();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void trackState(Id orderId, OrderStatus status) {
        if (orderStateTracker != null) orderStateTracker.record(orderId, status);
    }

    private void emitPriceLevelChange(Side side, long price, long qty) {
        if (priceLevelChangedListener != null) {
            priceLevelChangedListener.accept(
                    new PriceLevelChangedEvent(side, price, qty, nextEngineSeq()));
        }
    }

    private void addToUserOrders(Hash32 userId, Id orderId) {
        userOrders.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(orderId);
    }

    private void removeFromUserOrders(Hash32 userId, Id orderId) {
        List<Id> ids = userOrders.get(userId);
        if (ids != null) ids.remove(orderId);
    }
}
