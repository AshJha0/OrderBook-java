package com.orderbook.ull;

/**
 * A fixed-capacity FIFO queue of orders at a single price level.
 *
 * Implemented as a flat ring buffer of pre-allocated {@link UllOrderNode} slots.
 * There are NO locks and NO heap allocations during matching — every method
 * operates on pre-allocated slots from the pool passed at construction time.
 *
 * <p>Single-writer safe: all mutations happen on the Disruptor event-handler
 * thread, so no synchronisation is needed inside this class.</p>
 *
 * Capacity must be a power of two; the default is 4096 orders per level.
 */
public final class UllPriceLevel {

    public static final int DEFAULT_CAPACITY = 4096;

    private final UllOrderNode[] nodes;
    private final int            mask;

    private int head = 0;   // next slot to read (oldest order)
    private int tail = 0;   // next free slot to write
    private int size = 0;

    public final long price;

    public UllPriceLevel(long price, int capacity) {
        if (Integer.bitCount(capacity) != 1) throw new IllegalArgumentException("capacity must be power of two");
        this.price = price;
        this.mask  = capacity - 1;
        this.nodes = new UllOrderNode[capacity];
        for (int i = 0; i < capacity; i++) nodes[i] = new UllOrderNode();
    }

    public UllPriceLevel(long price) {
        this(price, DEFAULT_CAPACITY);
    }

    // -------------------------------------------------------------------------
    // Insertion
    // -------------------------------------------------------------------------

    /**
     * Add an order to the tail of this price level.
     * Returns the node that was written (for the caller to store in the id→node index).
     * Returns null if the level is full.
     */
    public UllOrderNode add(
            long idHigh, long idLow,
            long quantity, long hiddenQty,
            long u0, long u1, long u2, long u3,
            byte side, byte flags) {

        if (size == nodes.length) return null;   // level full

        UllOrderNode n = nodes[tail & mask];
        n.idHigh     = idHigh;
        n.idLow      = idLow;
        n.quantity   = quantity;
        n.hiddenQty  = hiddenQty;
        n.user0      = u0;
        n.user1      = u1;
        n.user2      = u2;
        n.user3      = u3;
        n.price      = price;
        n.side       = side;
        n.orderFlags = flags;
        n.active     = true;

        tail++;
        size++;
        return n;
    }

    // -------------------------------------------------------------------------
    // Matching (consume from head)
    // -------------------------------------------------------------------------

    /**
     * Match up to {@code needed} quantity against the FIFO queue.
     *
     * <p>For each fill the caller-supplied {@link FillCallback} is invoked with:
     * (makerIdHigh, makerIdLow, filledQty, makerPrice, makerUser0..3)
     * — entirely primitive, zero allocation.</p>
     *
     * @return quantity actually matched (may be less than needed if level runs dry)
     */
    public long match(long needed, FillCallback cb, STPCheck stpCheck,
                      long takerU0, long takerU1, long takerU2, long takerU3) {
        long matched = 0L;

        while (size > 0 && Long.compareUnsigned(matched, needed) < 0) {
            UllOrderNode maker = nodes[head & mask];

            // skip inactive (tombstoned) slots
            if (!maker.active) {
                head++;
                size--;
                continue;
            }

            // STP check — single callback, no allocation
            if (stpCheck != null) {
                int stpAction = stpCheck.check(
                        maker.idHigh, maker.idLow,
                        takerU0, takerU1, takerU2, takerU3,
                        maker.user0, maker.user1, maker.user2, maker.user3);
                if (stpAction == STPCheck.CANCEL_MAKER) {
                    maker.active = false;
                    head++;
                    size--;
                    continue;
                }
                if (stpAction == STPCheck.CANCEL_TAKER) {
                    break;   // stop matching; residual taker qty handled by caller
                }
                if (stpAction == STPCheck.CANCEL_BOTH) {
                    maker.active = false;
                    head++;
                    size--;
                    break;
                }
                // NONE → fall through to fill
            }

            long available = maker.isIceberg() ? maker.quantity : maker.quantity;
            long fill = Long.compareUnsigned(available, needed - matched) <= 0
                    ? available : needed - matched;

            maker.quantity -= fill;
            matched        += fill;

            // replenish iceberg slice from hidden reserve
            if (maker.isIceberg() && maker.quantity == 0L && maker.hiddenQty > 0L) {
                long slice = Math.min(maker.hiddenQty, maker.hiddenQty); // full hidden
                maker.quantity   = slice;
                maker.hiddenQty -= slice;
            }

            cb.onFill(maker.idHigh, maker.idLow, fill, maker.price,
                       maker.user0, maker.user1, maker.user2, maker.user3);

            if (maker.quantity == 0L) {
                maker.active = false;
                head++;
                size--;
            }
        }

        return matched;
    }

    // -------------------------------------------------------------------------
    // Cancellation (O(n) scan; hot path is match, not cancel)
    // -------------------------------------------------------------------------

    /**
     * Remove the order with the given id from any position in the level.
     * Returns true if found and removed.
     */
    public boolean cancel(long idHigh, long idLow) {
        int scanned = 0;
        int idx = head;
        while (scanned < size) {
            UllOrderNode n = nodes[idx & mask];
            if (n.active && n.idHigh == idHigh && n.idLow == idLow) {
                n.active = false;
                // if at head, advance head to keep the ring tight
                if (idx == head) {
                    head++;
                    size--;
                }
                // for mid-queue removes we leave a tombstone; it's skipped in match()
                return true;
            }
            idx++;
            scanned++;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Inspection
    // -------------------------------------------------------------------------

    /**
     * Total visible quantity across all active orders at this level.
     * O(n) — only call for depth queries, never from matching.
     */
    public long visibleQuantity() {
        long total = 0L;
        int scanned = 0;
        int idx = head;
        while (scanned < size) {
            UllOrderNode n = nodes[idx & mask];
            if (n.active) total += n.quantity;
            idx++;
            scanned++;
        }
        return total;
    }

    public int  activeOrderCount() {
        int count = 0;
        int scanned = 0;
        int idx = head;
        while (scanned < size) {
            if (nodes[idx & mask].active) count++;
            idx++;
            scanned++;
        }
        return count;
    }

    public boolean isEmpty() { return activeOrderCount() == 0; }
    public int     rawSize() { return size; }   // includes tombstones

    /**
     * True when the level can be removed from the book (all orders gone or tombstoned).
     */
    public boolean isEffectivelyEmpty() {
        for (int i = 0; i < size; i++) {
            if (nodes[(head + i) & mask].active) return false;
        }
        return true;
    }

    /** Compact tombstones in-place (O(n); call after a batch of cancels). */
    public void compact() {
        int w = 0;
        for (int r = 0; r < size; r++) {
            UllOrderNode src = nodes[(head + r) & mask];
            if (src.active) {
                nodes[(head + w) & mask] = src;
                w++;
            }
        }
        tail = head + w;
        size = w;
    }

    // -------------------------------------------------------------------------
    // Callbacks (primitive, zero-allocation)
    // -------------------------------------------------------------------------

    @FunctionalInterface
    public interface FillCallback {
        void onFill(long makerIdHigh, long makerIdLow, long filledQty, long price,
                    long u0, long u1, long u2, long u3);
    }

    @FunctionalInterface
    public interface STPCheck {
        int NONE         = 0;
        int CANCEL_TAKER = 1;
        int CANCEL_MAKER = 2;
        int CANCEL_BOTH  = 3;

        int check(long makerIdHigh, long makerIdLow,
                  long takerU0, long takerU1, long takerU2, long takerU3,
                  long makerU0, long makerU1, long makerU2, long makerU3);
    }
}
