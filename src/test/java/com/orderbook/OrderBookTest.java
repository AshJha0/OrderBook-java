package com.orderbook;

import com.orderbook.fees.FeeSchedule;
import com.orderbook.model.*;
import com.orderbook.stp.STPMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private OrderBook<Void> book;
    private Clock.StubClock clock;

    @BeforeEach
    void setUp() {
        clock = new Clock.StubClock(1_000L);
        book = new OrderBook<>("BTC/USD", clock);
    }

    // -------------------------------------------------------------------------
    // Helper builders
    // -------------------------------------------------------------------------

    private OrderType.Standard<Void> limit(long price, long qty, Side side) {
        return new OrderType.Standard<>(
                Id.newUuid(),
                Price.of(price),
                Quantity.of(qty),
                side,
                Hash32.ZERO,
                TimestampMs.of(clock.nowMs()),
                TimeInForce.GTC,
                null
        );
    }

    private OrderType.Standard<Void> limitUser(long price, long qty, Side side, Hash32 userId) {
        return new OrderType.Standard<>(
                Id.newUuid(),
                Price.of(price),
                Quantity.of(qty),
                side,
                userId,
                TimestampMs.of(clock.nowMs()),
                TimeInForce.GTC,
                null
        );
    }

    private OrderType.ImmediateOrCancel<Void> ioc(long price, long qty, Side side) {
        return new OrderType.ImmediateOrCancel<>(
                Id.newUuid(), Price.of(price), Quantity.of(qty), side,
                Hash32.ZERO, TimestampMs.of(clock.nowMs()), null);
    }

    private OrderType.FillOrKill<Void> fok(long price, long qty, Side side) {
        return new OrderType.FillOrKill<>(
                Id.newUuid(), Price.of(price), Quantity.of(qty), side,
                Hash32.ZERO, TimestampMs.of(clock.nowMs()), null);
    }

    // -------------------------------------------------------------------------
    // Basic tests
    // -------------------------------------------------------------------------

    @Test
    void newBook_isEmpty() {
        assertTrue(book.bestBid().isEmpty());
        assertTrue(book.bestAsk().isEmpty());
        assertEquals(0, book.totalOrderCount());
    }

    @Test
    void submitBuyLimit_restsOnBidSide() throws OrderBookException {
        book.submitOrder(limit(100, 10, Side.BUY));

        Optional<Price> bid = book.bestBid();
        assertTrue(bid.isPresent());
        assertEquals(100L, bid.get().value());
        assertEquals(1, book.bidLevelCount());
        assertEquals(0, book.askLevelCount());
    }

    @Test
    void submitSellLimit_restsOnAskSide() throws OrderBookException {
        book.submitOrder(limit(200, 5, Side.SELL));

        Optional<Price> ask = book.bestAsk();
        assertTrue(ask.isPresent());
        assertEquals(200L, ask.get().value());
    }

    @Test
    void matchingOrder_fullFill() throws OrderBookException {
        // rest a sell at 100
        book.submitOrder(limit(100, 10, Side.SELL));
        // aggressive buy at 100
        TradeResult result = book.submitOrder(limit(100, 10, Side.BUY));

        assertTrue(result.matchResult.isComplete());
        assertEquals(10L, result.matchResult.executedQuantity().value());
        assertEquals(0L, result.matchResult.remainingQuantity().value());
        // book is empty after the match
        assertTrue(book.bestAsk().isEmpty());
    }

    @Test
    void matchingOrder_partialFill() throws OrderBookException {
        book.submitOrder(limit(100, 5, Side.SELL));
        TradeResult result = book.submitOrder(limit(100, 10, Side.BUY));

        assertEquals(5L, result.matchResult.executedQuantity().value());
        assertEquals(5L, result.matchResult.remainingQuantity().value());
        // residual rests on bid side
        assertTrue(book.bestBid().isPresent());
        assertTrue(book.bestAsk().isEmpty());
    }

    @Test
    void noMatch_differentPrices() throws OrderBookException {
        book.submitOrder(limit(100, 10, Side.BUY));
        book.submitOrder(limit(200, 10, Side.SELL));

        assertEquals(0, book.submitOrder(limit(99, 5, Side.BUY)).matchResult.tradeCount());
        assertTrue(book.bestBid().isPresent());
        assertTrue(book.bestAsk().isPresent());
    }

    @Test
    void spread_calculation() throws OrderBookException {
        book.submitOrder(limit(100, 10, Side.BUY));
        book.submitOrder(limit(110, 10, Side.SELL));
        assertEquals(Optional.of(10L), book.spread());
    }

    // -------------------------------------------------------------------------
    // Kill switch
    // -------------------------------------------------------------------------

    @Test
    void killSwitch_rejectsNewOrders() throws OrderBookException {
        book.engageKillSwitch();
        assertThrows(OrderBookException.KillSwitchActive.class,
                () -> book.submitOrder(limit(100, 10, Side.BUY)));
    }

    @Test
    void killSwitch_allowsCancellation() throws OrderBookException {
        book.submitOrder(limit(100, 10, Side.BUY));
        Id orderId = book.restingOrders(Side.BUY).get(0).id();
        book.engageKillSwitch();
        // should not throw
        assertDoesNotThrow(() -> book.cancelOrder(orderId));
    }

    @Test
    void killSwitch_release_allowsNewOrders() throws OrderBookException {
        book.engageKillSwitch();
        book.releaseKillSwitch();
        assertDoesNotThrow(() -> book.submitOrder(limit(100, 10, Side.BUY)));
    }

    // -------------------------------------------------------------------------
    // Cancellation
    // -------------------------------------------------------------------------

    @Test
    void cancelOrder_removesFromBook() throws OrderBookException {
        book.submitOrder(limit(100, 10, Side.BUY));
        Id orderId = book.restingOrders(Side.BUY).get(0).id();
        book.cancelOrder(orderId);
        assertTrue(book.bestBid().isEmpty());
        assertEquals(0, book.totalOrderCount());
    }

    @Test
    void cancelOrder_notFound_throws() {
        assertThrows(OrderBookException.OrderNotFound.class,
                () -> book.cancelOrder(Id.newUuid()));
    }

    // -------------------------------------------------------------------------
    // IOC / FOK
    // -------------------------------------------------------------------------

    @Test
    void ioc_residualCancelled() throws OrderBookException {
        book.submitOrder(limit(100, 5, Side.SELL));
        TradeResult result = book.submitOrder(ioc(100, 10, Side.BUY));

        assertEquals(5L, result.matchResult.executedQuantity().value());
        // residual must NOT rest
        assertTrue(book.bestBid().isEmpty());
    }

    @Test
    void fok_fullFillOrCancel_partialBook() throws OrderBookException {
        book.submitOrder(limit(100, 5, Side.SELL));
        // FOK for 10 — only 5 available — should get 0 fills (FOK semantics)
        TradeResult result = book.submitOrder(fok(100, 10, Side.BUY));
        // the sell-side should be untouched (FOK cancelled)
        assertTrue(book.bestAsk().isPresent());
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    @Test
    void tickSizeValidation_rejectsInvalidPrice() {
        book.withTickSize(10L);
        assertThrows(OrderBookException.InvalidTickSize.class,
                () -> book.submitOrder(limit(105, 10, Side.BUY)));
    }

    @Test
    void tickSizeValidation_acceptsMultiple() throws OrderBookException {
        book.withTickSize(10L);
        assertDoesNotThrow(() -> book.submitOrder(limit(100, 10, Side.BUY)));
    }

    @Test
    void minOrderSize_rejectsTooSmall() {
        book.withMinOrderSize(5L);
        assertThrows(OrderBookException.OrderSizeOutOfRange.class,
                () -> book.submitOrder(limit(100, 3, Side.BUY)));
    }

    @Test
    void maxOrderSize_rejectsTooLarge() {
        book.withMaxOrderSize(10L);
        assertThrows(OrderBookException.OrderSizeOutOfRange.class,
                () -> book.submitOrder(limit(100, 20, Side.BUY)));
    }

    @Test
    void duplicateOrderId_rejected() throws OrderBookException {
        var order = limit(100, 10, Side.BUY);
        book.submitOrder(order);
        // submit same order id again — must be rejected
        assertThrows(OrderBookException.DuplicateOrderId.class,
                () -> book.submitOrder(order));
    }

    // -------------------------------------------------------------------------
    // Fees
    // -------------------------------------------------------------------------

    @Test
    void fees_calculatedCorrectly() throws OrderBookException {
        book.withFeeSchedule(FeeSchedule.of(-2, 5)); // -2 bps maker, 5 bps taker
        book.submitOrder(limit(1000, 10, Side.SELL));  // maker
        TradeResult result = book.submitOrder(limit(1000, 10, Side.BUY));  // taker

        // notional = 1000 * 10 = 10_000
        // maker fee: -2 * 10_000 / 10_000 = -2
        // taker fee:  5 * 10_000 / 10_000 =  5
        assertEquals(-2L, result.totalMakerFees);
        assertEquals( 5L, result.totalTakerFees);
        assertEquals( 3L, result.totalFees());
    }

    // -------------------------------------------------------------------------
    // STP
    // -------------------------------------------------------------------------

    @Test
    void stp_missingUserId_rejected_when_stp_enabled() {
        book.withSTPMode(STPMode.CANCEL_TAKER);
        // An order with Hash32.ZERO (anonymous) must be rejected when STP is enabled
        assertThrows(OrderBookException.MissingUserId.class,
                () -> book.submitOrder(limit(100, 10, Side.BUY)));
    }

    @Test
    void stp_cancelTaker_preventsSelftrade() throws OrderBookException {
        Hash32 user = Hash32.fromHex("aa".repeat(32));
        book.withSTPMode(STPMode.CANCEL_TAKER);
        // Rest a sell order from user
        book.submitOrder(limitUser(100, 10, Side.SELL, user));
        // Same user tries to buy at same price — self-trade prevention should stop the taker
        TradeResult result = book.submitOrder(limitUser(100, 10, Side.BUY, user));
        // No fills should have occurred (taker was STP'd)
        assertEquals(0, result.matchResult.tradeCount());
        // Sell-side resting order must still be there
        assertTrue(book.bestAsk().isPresent());
    }

    // -------------------------------------------------------------------------
    // Market orders
    // -------------------------------------------------------------------------

    @Test
    void marketOrder_sweepsBestPrices() throws OrderBookException {
        book.submitOrder(limit(100, 5, Side.SELL));
        book.submitOrder(limit(101, 5, Side.SELL));
        TradeResult result = book.submitMarketOrder(Side.BUY, 8, Hash32.ZERO);

        assertEquals(8L, result.matchResult.executedQuantity().value());
    }

    // -------------------------------------------------------------------------
    // Depth
    // -------------------------------------------------------------------------

    @Test
    void depth_returnsSortedLevels() throws OrderBookException {
        book.submitOrder(limit(100, 10, Side.BUY));
        book.submitOrder(limit(110, 5, Side.BUY));
        book.submitOrder(limit(90,  3, Side.BUY));

        List<long[]> depth = book.depth(Side.BUY, 5);
        assertEquals(3, depth.size());
        // best bid first (descending)
        assertEquals(110L, depth.get(0)[0]);
        assertEquals(100L, depth.get(1)[0]);
        assertEquals(90L,  depth.get(2)[0]);
    }

    // -------------------------------------------------------------------------
    // Mass cancel
    // -------------------------------------------------------------------------

    @Test
    void massCancel_removesAll() throws OrderBookException {
        book.submitOrder(limit(100, 10, Side.BUY));
        book.submitOrder(limit(200, 5, Side.SELL));
        int cancelled = book.massCancel();
        assertEquals(2, cancelled);
        assertTrue(book.bestBid().isEmpty());
        assertTrue(book.bestAsk().isEmpty());
    }
}
