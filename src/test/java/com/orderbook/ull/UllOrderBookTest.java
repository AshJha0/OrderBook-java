package com.orderbook.ull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ultra-low-latency order book.
 *
 * Tests call UllOrderBook directly (no Disruptor) to keep setup minimal and
 * isolate matching logic from the threading layer.
 */
class UllOrderBookTest {

    private UllOrderBook book;

    // Collect fills synchronously for assertions
    private MutableMatchResult lastResult;

    @BeforeEach
    void setUp() {
        book = new UllOrderBook();
        book.withMatchCallback(r -> {
            // Deep-copy fills so we can assert after the result is released
            lastResult = new MutableMatchResult();
            lastResult.takerIdHigh  = r.takerIdHigh;
            lastResult.takerIdLow   = r.takerIdLow;
            lastResult.executedQty  = r.executedQty;
            lastResult.remainingQty = r.remainingQty;
            lastResult.complete     = r.complete;
            lastResult.fillCount    = r.fillCount;
            lastResult.totalMakerFee = r.totalMakerFee;
            lastResult.totalTakerFee = r.totalTakerFee;
            for (int i = 0; i < r.fillCount; i++) {
                MutableFill src = r.fills[i];
                MutableFill dst = lastResult.fills[i];
                dst.makerIdHigh = src.makerIdHigh; dst.makerIdLow = src.makerIdLow;
                dst.filledQty   = src.filledQty;   dst.price       = src.price;
                dst.makerFee    = src.makerFee;    dst.takerFee    = src.takerFee;
            }
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static long[] uuid() {
        java.util.UUID u = java.util.UUID.randomUUID();
        return new long[]{u.getMostSignificantBits(), u.getLeastSignificantBits()};
    }

    private long submitBuy(long price, long qty) {
        long[] id = uuid();
        return book.submitLimit(id[0], id[1], price, qty, (byte)0, (byte)0, (byte)0,
                0L, 0L, 0L, 0L);
    }

    private long submitSell(long price, long qty) {
        long[] id = uuid();
        return book.submitLimit(id[0], id[1], price, qty, (byte)1, (byte)0, (byte)0,
                0L, 0L, 0L, 0L);
    }

    // -------------------------------------------------------------------------

    @Test
    void emptyBook_noPrice() {
        assertEquals(UllOrderBook.NO_PRICE, book.bestBidRaw());
        assertEquals(UllOrderBook.NO_PRICE, book.bestAskRaw());
        assertEquals(0, book.totalOrderCount());
    }

    @Test
    void bidRests_whenNoOppositeOrder() {
        long rc = submitBuy(100L, 10L);
        assertFalse(ErrorCode.isError(rc));
        assertEquals(100L, book.bestBidRaw());
        assertEquals(1, book.totalOrderCount());
    }

    @Test
    void askRests_whenNoOppositeOrder() {
        submitSell(200L, 5L);
        assertEquals(200L, book.bestAskRaw());
    }

    @Test
    void fullFill_matchingBuyAgainstRestingSell() {
        submitSell(100L, 10L);
        submitBuy(100L, 10L);

        assertEquals(10L, lastResult.executedQty);
        assertEquals(0L,  lastResult.remainingQty);
        assertTrue(lastResult.complete);
        assertEquals(UllOrderBook.NO_PRICE, book.bestAskRaw());
        assertEquals(0, book.totalOrderCount());
    }

    @Test
    void partialFill_residualRests() {
        submitSell(100L, 5L);
        submitBuy(100L, 10L);

        assertEquals(5L, lastResult.executedQty);
        assertEquals(5L, lastResult.remainingQty);
        assertFalse(lastResult.complete);
        // residual rests on bid side
        assertEquals(100L, book.bestBidRaw());
        assertEquals(UllOrderBook.NO_PRICE, book.bestAskRaw());
    }

    @Test
    void noMatch_differentPrices() {
        submitBuy(100L, 10L);
        submitSell(200L, 10L);
        submitBuy(99L, 5L);

        assertEquals(0L, lastResult.executedQty);
        assertEquals(100L, book.bestBidRaw());
        assertEquals(200L, book.bestAskRaw());
    }

    @Test
    void spread_correct() {
        submitBuy(100L, 1L);
        submitSell(110L, 1L);
        assertEquals(10L, book.spreadRaw());
    }

    @Test
    void killSwitch_rejectsNewOrders() {
        book.engageKillSwitch();
        long rc = submitBuy(100L, 10L);
        assertEquals(ErrorCode.KILL_SWITCH, rc);
    }

    @Test
    void killSwitch_release_allowsOrders() {
        book.engageKillSwitch();
        book.releaseKillSwitch();
        long rc = submitBuy(100L, 10L);
        assertFalse(ErrorCode.isError(rc));
    }

    @Test
    void cancel_removesFromBook() {
        long[] id = uuid();
        book.submitLimit(id[0], id[1], 100L, 10L, (byte)0, (byte)0, (byte)0,
                0L, 0L, 0L, 0L);
        assertEquals(1, book.totalOrderCount());

        long rc = book.cancel(id[0], id[1]);
        assertEquals(ErrorCode.OK, rc);
        assertEquals(0, book.totalOrderCount());
        assertEquals(UllOrderBook.NO_PRICE, book.bestBidRaw());
    }

    @Test
    void cancel_notFound() {
        long rc = book.cancel(1L, 2L);
        assertEquals(ErrorCode.NOT_FOUND, rc);
    }

    @Test
    void ioc_residualNotResting() {
        submitSell(100L, 5L);
        long[] id = uuid();
        // tif=1 = IOC
        book.submitLimit(id[0], id[1], 100L, 10L, (byte)0, (byte)1, (byte)0,
                0L, 0L, 0L, 0L);

        assertEquals(5L, lastResult.executedQty);
        // no residual on bid side
        assertEquals(UllOrderBook.NO_PRICE, book.bestBidRaw());
    }

    @Test
    void fok_cancelledWhenInsufficient() {
        submitSell(100L, 5L);
        long[] id = uuid();
        // tif=2 = FOK, qty=10 but only 5 available
        long rc = book.submitLimit(id[0], id[1], 100L, 10L, (byte)0, (byte)2, (byte)0,
                0L, 0L, 0L, 0L);

        assertEquals(ErrorCode.FOK_CANCELLED, rc);
        // ask side must be untouched
        assertEquals(100L, book.bestAskRaw());
    }

    @Test
    void tickSize_rejectsInvalidPrice() {
        book.withTickSize(10L);
        long rc = submitBuy(105L, 10L);
        assertEquals(ErrorCode.INVALID_TICK, rc);
    }

    @Test
    void tickSize_acceptsValidMultiple() {
        book.withTickSize(10L);
        long rc = submitBuy(100L, 10L);
        assertFalse(ErrorCode.isError(rc));
    }

    @Test
    void minSize_rejectsTooSmall() {
        book.withMinOrderSize(5L);
        long rc = submitBuy(100L, 3L);
        assertEquals(ErrorCode.SIZE_TOO_SMALL, rc);
    }

    @Test
    void maxSize_rejectsTooLarge() {
        book.withMaxOrderSize(10L);
        long rc = submitBuy(100L, 20L);
        assertEquals(ErrorCode.SIZE_TOO_LARGE, rc);
    }

    @Test
    void duplicateId_rejected() {
        long[] id = uuid();
        book.submitLimit(id[0], id[1], 100L, 10L, (byte)0, (byte)0, (byte)0,
                0L, 0L, 0L, 0L);
        long rc = book.submitLimit(id[0], id[1], 100L, 10L, (byte)0, (byte)0, (byte)0,
                0L, 0L, 0L, 0L);
        assertEquals(ErrorCode.DUPLICATE_ID, rc);
    }

    @Test
    void fees_calculatedCorrectly() {
        book.withFees(-2, 5);  // -2 bps maker, 5 bps taker
        submitSell(1000L, 10L);   // maker
        submitBuy(1000L, 10L);    // taker
        // notional = 1000 * 10 = 10_000
        // maker: -2 * 10_000 / 10_000 = -2
        // taker:  5 * 10_000 / 10_000 =  5
        assertEquals(-2L, lastResult.totalMakerFee);
        assertEquals( 5L, lastResult.totalTakerFee);
    }

    @Test
    void stp_missingUser_whenStpEnabled() {
        book.withStpMode(1);  // CANCEL_TAKER
        // anonymous user (all-zero id) must be rejected
        long rc = submitBuy(100L, 10L);
        assertEquals(ErrorCode.MISSING_USER, rc);
    }

    @Test
    void stp_cancelTaker_preventsSelftrade() {
        book.withStpMode(1);  // CANCEL_TAKER
        long u0 = 0xAABBCCDDL, u1 = 0x11223344L, u2 = 0L, u3 = 0L;
        long[] sellId = uuid();
        long[] buyId  = uuid();
        // Rest sell from user
        book.submitLimit(sellId[0], sellId[1], 100L, 10L, (byte)1, (byte)0, (byte)0,
                u0, u1, u2, u3);
        // Same user tries to buy at same price
        book.submitLimit(buyId[0], buyId[1], 100L, 10L, (byte)0, (byte)0, (byte)0,
                u0, u1, u2, u3);

        assertEquals(0L, lastResult.executedQty);
        assertEquals(100L, book.bestAskRaw());  // ask still present
    }

    @Test
    void marketOrder_sweepsMultipleLevels() {
        submitSell(100L, 5L);
        submitSell(101L, 5L);
        long[] id = uuid();
        book.submitMarket(id[0], id[1], 8L, (byte)0, 0L, 0L, 0L, 0L);

        assertEquals(8L, lastResult.executedQty);
    }

    @Test
    void depth_sortedCorrectly() {
        submitBuy(100L, 10L);
        submitBuy(110L,  5L);
        submitBuy( 90L,  3L);

        long[][] out = new long[5][2];
        int levels = book.depth((byte)0, out, 5);

        assertEquals(3, levels);
        assertEquals(110L, out[0][0]);  // best bid first
        assertEquals(100L, out[1][0]);
        assertEquals( 90L, out[2][0]);
    }

    @Test
    void massCancel_emptiesBook() {
        submitBuy(100L, 10L);
        submitSell(200L,  5L);
        int cancelled = book.massCancel();

        assertEquals(2, cancelled);
        assertEquals(UllOrderBook.NO_PRICE, book.bestBidRaw());
        assertEquals(UllOrderBook.NO_PRICE, book.bestAskRaw());
    }

    @Test
    void multipleOrdersSameLevel_fifoMatching() {
        long[] id1 = uuid(), id2 = uuid();
        book.submitLimit(id1[0], id1[1], 100L, 3L, (byte)1, (byte)0, (byte)0,
                0L, 0L, 0L, 0L);
        book.submitLimit(id2[0], id2[1], 100L, 7L, (byte)1, (byte)0, (byte)0,
                0L, 0L, 0L, 0L);
        // Buy sweeps both — first fill should be against id1
        submitBuy(100L, 10L);

        assertEquals(10L, lastResult.executedQty);
        assertEquals(2, lastResult.fillCount);
        assertEquals(id1[0], lastResult.fills[0].makerIdHigh);  // FIFO: id1 first
        assertEquals(id2[0], lastResult.fills[1].makerIdHigh);
    }
}
