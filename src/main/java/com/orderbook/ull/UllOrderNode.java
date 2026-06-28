package com.orderbook.ull;

/**
 * Flat, mutable order record stored inside a price level's ring buffer.
 *
 * All fields are primitives — no boxed types, no records, no allocation on
 * the matching hot path.  Nodes are pre-allocated in a pool and recycled.
 *
 * UUID is stored as two longs (high/low bits).
 * 256-bit userId is stored as four longs.
 * Side: 0 = BUY, 1 = SELL.
 */
public final class UllOrderNode {

    /** Sentinel: this slot is free / has been fully matched or cancelled. */
    public static final long EMPTY = 0L;

    // --- order identity ---
    public long idHigh;
    public long idLow;

    // --- quantity ---
    public long quantity;       // remaining visible quantity
    public long hiddenQty;      // iceberg reserve; 0 for non-iceberg

    // --- user identity (for STP) ---
    public long user0, user1, user2, user3;  // 256-bit / 4×64

    // --- price (redundant with level, kept for quick reference) ---
    public long price;

    // --- metadata ---
    public byte  side;          // 0 = BUY, 1 = SELL
    public byte  orderFlags;    // bit 0 = POST_ONLY, bit 1 = ICEBERG
    public boolean active;

    public void reset() {
        idHigh = idLow = 0L;
        quantity = hiddenQty = 0L;
        user0 = user1 = user2 = user3 = 0L;
        price = 0L;
        side = 0;
        orderFlags = 0;
        active = false;
    }

    /** Returns true iff the node's userId matches the given 256-bit user. */
    public boolean isSameUser(long u0, long u1, long u2, long u3) {
        return user0 == u0 && user1 == u1 && user2 == u2 && user3 == u3;
    }

    public boolean isZeroUser() {
        return user0 == 0L && user1 == 0L && user2 == 0L && user3 == 0L;
    }

    public boolean isIceberg()  { return (orderFlags & 0x02) != 0; }
    public boolean isPostOnly() { return (orderFlags & 0x01) != 0; }
}
