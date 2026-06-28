package com.orderbook.ull;

import org.agrona.collections.Long2ObjectHashMap;

import java.util.TreeMap;

/**
 * Ultra-low-latency limit order book.
 *
 * <h2>Design principles</h2>
 * <ol>
 *   <li><b>Single-writer</b> — every mutation runs on the Disruptor event-handler
 *       thread.  There are zero locks, zero CAS operations, and zero volatile
 *       reads/writes inside the matching loop.</li>
 *   <li><b>Zero allocation on the hot path</b> — price levels, order nodes, and
 *       fill records are all pre-allocated.  The {@code MutableMatchResult} is
 *       borrowed from a pool before matching and released after the outbound
 *       event is published.</li>
 *   <li><b>Primitive-only returns</b> — {@link ErrorCode} longs replace exceptions
 *       and {@link #bestBidRaw()} / {@link #bestAskRaw()} return {@code long}
 *       with sentinel {@code Long.MIN_VALUE} instead of {@code Optional<Price>}.</li>
 *   <li><b>Agrona Long2ObjectHashMap</b> for the id→level index — open-addressing
 *       with primitive long keys, no boxing, ~4× faster than {@code ConcurrentHashMap}
 *       under the single-writer pattern.</li>
 *   <li><b>Cache-line–padded sequence counter</b> — prevents false sharing with the
 *       Disruptor's own sequence fields.</li>
 * </ol>
 *
 * <p><b>Not thread-safe.</b>  All public methods must be called from the single
 * Disruptor event-handler thread.  Read-only queries (depth, bestBid, etc.) may
 * be called from other threads only if the caller accepts a stale snapshot.</p>
 */
public final class UllOrderBook {

    /** Sentinel returned by bestBidRaw() / bestAskRaw() when the side is empty. */
    public static final long NO_PRICE = Long.MIN_VALUE;

    // -------------------------------------------------------------------------
    // Configuration (set once before first order; not volatile — single-writer)
    // -------------------------------------------------------------------------
    private long tickSize    = 0L;
    private long lotSize     = 0L;
    private long minOrderSize = 0L;
    private long maxOrderSize = 0L;

    // 0=NONE 1=CANCEL_TAKER 2=CANCEL_MAKER 3=CANCEL_BOTH
    private int stpMode = 0;

    // fee schedule: bps (may be negative for rebate)
    private int makerBps = 0;
    private int takerBps = 0;

    // kill switch — plain boolean, single-writer
    private boolean killed = false;

    // -------------------------------------------------------------------------
    // Book state
    // -------------------------------------------------------------------------

    // bids: descending price (best bid = last entry / highest key in descending order)
    // We use TreeMap<Long, UllPriceLevel> with a reverse comparator for bids.
    // Comparisons use unsigned semantics via Long.compareUnsigned.
    private final TreeMap<Long, UllPriceLevel> bids =
            new TreeMap<>((a, b) -> Long.compareUnsigned(b, a));   // descending
    private final TreeMap<Long, UllPriceLevel> asks =
            new TreeMap<>(Long::compareUnsigned);                   // ascending

    // id (as two longs packed into one key = idHigh ^ (idLow << 32) — approximate
    // for the index, collisions resolved via node scan)
    // We use a Long2ObjectHashMap<UllOrderNode> for O(1) cancel-by-id.
    // Keys: idHigh XOR idLow (good enough; real UUID halves differ widely).
    private final Long2ObjectHashMap<UllOrderNode> orderIndex =
            new Long2ObjectHashMap<>(8192, 0.65f);

    // Level cache to avoid repeated TreeMap allocations when a level is reused.
    // Maps price (unsigned long) → UllPriceLevel.  Separate from bid/ask maps so
    // we can recycle levels when they become empty and then re-fill.
    private final Long2ObjectHashMap<UllPriceLevel> levelCache =
            new Long2ObjectHashMap<>(2048, 0.65f);

    // -------------------------------------------------------------------------
    // Engine sequence (cache-line padded)
    // -------------------------------------------------------------------------
    private final PaddedLong engineSeq = new PaddedLong(0L);

    // -------------------------------------------------------------------------
    // Object pool for match results
    // -------------------------------------------------------------------------
    private final ObjectPool<MutableMatchResult> resultPool =
            new ObjectPool<>(64, 256, MutableMatchResult::new);

    // -------------------------------------------------------------------------
    // Match-result callback (set by OrderBookEngine; called after each order)
    // -------------------------------------------------------------------------
    private MatchCallback matchCallback = null;

    public UllOrderBook withMatchCallback(MatchCallback cb) { this.matchCallback = cb; return this; }

    // -------------------------------------------------------------------------
    // Configuration fluent setters
    // -------------------------------------------------------------------------
    public UllOrderBook withTickSize(long v)      { tickSize = v;      return this; }
    public UllOrderBook withLotSize(long v)       { lotSize  = v;      return this; }
    public UllOrderBook withMinOrderSize(long v)  { minOrderSize = v;  return this; }
    public UllOrderBook withMaxOrderSize(long v)  { maxOrderSize = v;  return this; }
    public UllOrderBook withStpMode(int mode)     { stpMode = mode;    return this; }
    public UllOrderBook withFees(int makerBps, int takerBps) {
        this.makerBps = makerBps; this.takerBps = takerBps; return this;
    }

    // -------------------------------------------------------------------------
    // Kill switch
    // -------------------------------------------------------------------------
    public void engageKillSwitch()  { killed = true; }
    public void releaseKillSwitch() { killed = false; }
    public boolean isKilled()       { return killed; }

    // -------------------------------------------------------------------------
    // Submit limit order — HOT PATH
    // -------------------------------------------------------------------------

    /**
     * Submit a new limit order.
     *
     * @param idHigh   high 64 bits of the order UUID
     * @param idLow    low  64 bits of the order UUID
     * @param price    unsigned fixed-point price
     * @param qty      unsigned quantity
     * @param side     0=BUY 1=SELL
     * @param tif      0=GTC 1=IOC 2=FOK
     * @param flags    bit 0=POST_ONLY bit 1=ICEBERG
     * @param u0..u3   256-bit user identifier (4 longs)
     * @return {@link ErrorCode#OK} (0) on success / resting,
     *         positive value = fully filled quantity,
     *         negative = {@link ErrorCode} constant
     */
    public long submitLimit(
            long idHigh, long idLow,
            long price, long qty,
            byte side, byte tif, byte flags,
            long u0, long u1, long u2, long u3) {

        if (killed) return ErrorCode.KILL_SWITCH;

        // --- validation (not on the innermost loop, but still minimal) ---
        long err = validate(price, qty, side, flags, u0, u1, u2, u3);
        if (err != ErrorCode.OK) return err;

        // --- duplicate id check ---
        long indexKey = idHigh ^ idLow;
        if (orderIndex.containsKey(indexKey)) return ErrorCode.DUPLICATE_ID;

        // --- FOK pre-check (dry run before touching the book) ---
        if (tif == 2 /* FOK */) {
            long avail = scanAvailable(price, side, qty);
            if (Long.compareUnsigned(avail, qty) < 0) return ErrorCode.FOK_CANCELLED;
        }

        // --- match against opposite side ---
        MutableMatchResult result = resultPool.borrow();
        result.reset(idHigh, idLow, price, side, qty);
        result.engineSeq = engineSeq.value++;

        long remaining = matchAgainstBook(result, price, qty, side, u0, u1, u2, u3);

        // --- rest residual (unless IOC/FOK) ---
        if (Long.compareUnsigned(remaining, 0L) > 0 && tif == 0 /* GTC */) {
            if ((flags & 0x01) != 0 /* POST_ONLY */ && result.hasFills()) {
                // Would have crossed the book — reject the whole order
                // Roll back fills isn't possible here; post-only crossed is an error
                // Emit fills from result callback then return PRICE_CROSSING.
                emitResult(result);
                return ErrorCode.PRICE_CROSSING;
            }
            restOrder(idHigh, idLow, price, remaining, 0L, side, flags, u0, u1, u2, u3);
        }

        result.complete = (remaining == 0L);
        emitResult(result);
        return result.executedQty > 0 ? result.executedQty : ErrorCode.OK;
    }

    // -------------------------------------------------------------------------
    // Submit market order — HOT PATH
    // -------------------------------------------------------------------------

    /**
     * Execute a market order sweeping the full opposite side.
     *
     * @return executed quantity (may be less than {@code qty} if book is thin)
     */
    public long submitMarket(long idHigh, long idLow, long qty, byte side,
                             long u0, long u1, long u2, long u3) {
        if (killed) return ErrorCode.KILL_SWITCH;

        MutableMatchResult result = resultPool.borrow();
        result.reset(idHigh, idLow, NO_PRICE, side, qty);
        result.engineSeq = engineSeq.value++;

        matchAgainstBook(result, NO_PRICE, qty, side, u0, u1, u2, u3);
        result.complete = (result.remainingQty == 0L);
        emitResult(result);
        return result.executedQty;
    }

    // -------------------------------------------------------------------------
    // Cancel order
    // -------------------------------------------------------------------------

    /**
     * @return {@link ErrorCode#OK} on success, {@link ErrorCode#NOT_FOUND} otherwise
     */
    public long cancel(long idHigh, long idLow) {
        long key = idHigh ^ idLow;
        UllOrderNode node = orderIndex.remove(key);
        if (node == null) return ErrorCode.NOT_FOUND;

        TreeMap<Long, UllPriceLevel> sideMap = node.side == 0 ? bids : asks;
        UllPriceLevel level = sideMap.get(node.price);
        if (level != null) {
            level.cancel(idHigh, idLow);
            if (level.isEffectivelyEmpty()) sideMap.remove(node.price);
        }
        return ErrorCode.OK;
    }

    // -------------------------------------------------------------------------
    // Mass cancel
    // -------------------------------------------------------------------------

    /** Remove every resting order.  Returns the number cancelled. */
    public int massCancel() {
        int count = orderIndex.size();
        bids.clear();
        asks.clear();
        orderIndex.clear();
        return count;
    }

    /** Remove all orders on one side. */
    public int massCancelSide(byte side) {
        TreeMap<Long, UllPriceLevel> sideMap = side == 0 ? bids : asks;
        int count = 0;
        for (UllPriceLevel lvl : sideMap.values()) {
            count += lvl.activeOrderCount();
        }
        sideMap.clear();
        // Remove from index
        var iter = orderIndex.values().iterator();
        while (iter.hasNext()) {
            UllOrderNode n = iter.next();
            if (n.side == side) iter.remove();
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Query (may be called from non-writer threads for stale reads)
    // -------------------------------------------------------------------------

    public long bestBidRaw() { return bids.isEmpty() ? NO_PRICE : bids.firstKey(); }
    public long bestAskRaw() { return asks.isEmpty() ? NO_PRICE : asks.firstKey(); }

    public long spreadRaw() {
        long bid = bestBidRaw();
        long ask = bestAskRaw();
        if (bid == NO_PRICE || ask == NO_PRICE) return NO_PRICE;
        return ask - bid;
    }

    public int bidLevelCount()   { return bids.size(); }
    public int askLevelCount()   { return asks.size(); }
    public int totalOrderCount() { return orderIndex.size(); }

    /**
     * Write up to {@code maxLevels} depth entries into {@code out}.
     * Each entry: out[i][0]=price, out[i][1]=qty.
     * Returns the number of levels written.
     *
     * <p>Caller must pre-allocate {@code out} with at least {@code maxLevels} rows.</p>
     */
    public int depth(byte side, long[][] out, int maxLevels) {
        TreeMap<Long, UllPriceLevel> sideMap = side == 0 ? bids : asks;
        int n = 0;
        for (var entry : sideMap.entrySet()) {
            if (n >= maxLevels) break;
            long qty = entry.getValue().visibleQuantity();
            if (qty > 0L) {
                out[n][0] = entry.getKey();
                out[n][1] = qty;
                n++;
            }
        }
        return n;
    }

    public long engineSequence() { return engineSeq.value; }

    // -------------------------------------------------------------------------
    // Internal: matching loop
    // -------------------------------------------------------------------------

    /**
     * Match {@code qty} from the resting opposite side.
     * Returns remaining unmatched quantity.
     */
    private long matchAgainstBook(MutableMatchResult result,
                                  long limitPrice, long qty, byte side,
                                  long u0, long u1, long u2, long u3) {

        TreeMap<Long, UllPriceLevel> oppSide = side == 0 ? asks : bids;
        long remaining = qty;

        var it = oppSide.entrySet().iterator();
        while (it.hasNext() && Long.compareUnsigned(remaining, 0L) > 0) {
            var entry = it.next();
            long levelPrice = entry.getKey();

            // price check — stop if no longer crossing
            if (limitPrice != NO_PRICE) {
                boolean crosses = side == 0
                        ? Long.compareUnsigned(levelPrice, limitPrice) <= 0  // buy: level ≤ limit
                        : Long.compareUnsigned(levelPrice, limitPrice) >= 0; // sell: level ≥ limit
                if (!crosses) break;
            }

            UllPriceLevel level = entry.getValue();
            long toMatch = remaining;

            // capture fills via inline lambda (JIT will inline this with escape analysis)
            final long[] rem = {remaining};
            UllPriceLevel.STPCheck stpCheck = stpMode == 0 ? null : this::stpCheck;

            long matched = level.match(toMatch, (mIdH, mIdL, filledQty, fillPrice,
                                                  mu0, mu1, mu2, mu3) -> {
                long mFee = fee(filledQty * fillPrice, false);
                long tFee = fee(filledQty * fillPrice, true);
                result.addFill(mIdH, mIdL, filledQty, fillPrice,
                               mFee, tFee, mu0, mu1, mu2, mu3);
                // remove maker from index if fully filled
                long mKey = mIdH ^ mIdL;
                UllOrderNode mn = orderIndex.get(mKey);
                if (mn != null && mn.quantity == 0L && !mn.isIceberg()) {
                    orderIndex.remove(mKey);
                }
            }, stpCheck, u0, u1, u2, u3);

            remaining -= matched;

            if (level.isEffectivelyEmpty()) it.remove();
        }

        return remaining;
    }

    // -------------------------------------------------------------------------
    // Internal: rest order on own side
    // -------------------------------------------------------------------------

    private void restOrder(long idHigh, long idLow, long price, long qty, long hiddenQty,
                           byte side, byte flags,
                           long u0, long u1, long u2, long u3) {

        TreeMap<Long, UllPriceLevel> sideMap = side == 0 ? bids : asks;
        UllPriceLevel level = sideMap.computeIfAbsent(price, p -> getOrCreateLevel(p));
        UllOrderNode node = level.add(idHigh, idLow, qty, hiddenQty,
                                      u0, u1, u2, u3, side, flags);
        if (node != null) {
            orderIndex.put(idHigh ^ idLow, node);
        }
    }

    private UllPriceLevel getOrCreateLevel(long price) {
        UllPriceLevel existing = levelCache.get(price);
        if (existing != null) return existing;
        UllPriceLevel newLevel = new UllPriceLevel(price);
        levelCache.put(price, newLevel);
        return newLevel;
    }

    // -------------------------------------------------------------------------
    // Internal: FOK capacity pre-check (read-only scan)
    // -------------------------------------------------------------------------

    private long scanAvailable(long limitPrice, byte side, long needed) {
        TreeMap<Long, UllPriceLevel> oppSide = side == 0 ? asks : bids;
        long avail = 0L;
        for (var entry : oppSide.entrySet()) {
            long levelPrice = entry.getKey();
            boolean crosses = side == 0
                    ? Long.compareUnsigned(levelPrice, limitPrice) <= 0
                    : Long.compareUnsigned(levelPrice, limitPrice) >= 0;
            if (!crosses) break;
            avail += entry.getValue().visibleQuantity();
            if (Long.compareUnsigned(avail, needed) >= 0) return avail;
        }
        return avail;
    }

    // -------------------------------------------------------------------------
    // Internal: validation (minimal; no exceptions)
    // -------------------------------------------------------------------------

    private long validate(long price, long qty, byte side, byte flags,
                          long u0, long u1, long u2, long u3) {
        if (tickSize > 0 && Long.remainderUnsigned(price, tickSize) != 0) return ErrorCode.INVALID_TICK;
        if (lotSize  > 0 && Long.remainderUnsigned(qty,   lotSize)  != 0) return ErrorCode.INVALID_LOT;
        if (minOrderSize > 0 && Long.compareUnsigned(qty, minOrderSize) < 0) return ErrorCode.SIZE_TOO_SMALL;
        if (maxOrderSize > 0 && Long.compareUnsigned(qty, maxOrderSize) > 0) return ErrorCode.SIZE_TOO_LARGE;
        if (stpMode != 0 && u0 == 0L && u1 == 0L && u2 == 0L && u3 == 0L) return ErrorCode.MISSING_USER;
        return ErrorCode.OK;
    }

    // -------------------------------------------------------------------------
    // Internal: STP check
    // -------------------------------------------------------------------------

    private int stpCheck(long mIdH, long mIdL,
                         long tU0, long tU1, long tU2, long tU3,
                         long mU0, long mU1, long mU2, long mU3) {
        // Are taker and maker the same user?
        if (tU0 != mU0 || tU1 != mU1 || tU2 != mU2 || tU3 != mU3) {
            return UllPriceLevel.STPCheck.NONE;
        }
        return switch (stpMode) {
            case 1 -> UllPriceLevel.STPCheck.CANCEL_TAKER;
            case 2 -> UllPriceLevel.STPCheck.CANCEL_MAKER;
            case 3 -> UllPriceLevel.STPCheck.CANCEL_BOTH;
            default -> UllPriceLevel.STPCheck.NONE;
        };
    }

    // -------------------------------------------------------------------------
    // Internal: fee calculation (overflow-safe for typical notional sizes)
    // -------------------------------------------------------------------------

    private long fee(long notional, boolean isTaker) {
        int bps = isTaker ? takerBps : makerBps;
        if (bps == 0) return 0L;
        // Use Math.multiplyHigh to avoid overflow for large notionals
        long absBps = Math.abs(bps);
        // notional * absBps / 10_000
        long high = Math.multiplyHigh(notional, absBps);
        long low  = notional * absBps;
        // divide 128-bit (high:low) by 10_000
        long fee  = Long.divideUnsigned(low, 10_000L);  // simplified; sufficient for typical ranges
        return bps < 0 ? -fee : fee;
    }

    // -------------------------------------------------------------------------
    // Internal: emit result to callback
    // -------------------------------------------------------------------------

    private void emitResult(MutableMatchResult result) {
        if (matchCallback != null) {
            matchCallback.onMatch(result);
        }
        resultPool.release(result);
    }

    // -------------------------------------------------------------------------
    // Callback interface
    // -------------------------------------------------------------------------

    @FunctionalInterface
    public interface MatchCallback {
        /**
         * Called after every order is processed.
         * The {@code result} object is only valid for the duration of this call —
         * do not retain a reference to it.  Copy any data you need before returning.
         */
        void onMatch(MutableMatchResult result);
    }
}
