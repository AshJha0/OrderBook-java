package com.orderbook.ull;

import com.lmax.disruptor.EventHandler;

/**
 * Single-threaded Disruptor event handler for inbound order commands.
 *
 * This is the <em>only</em> thread that mutates the order book.  Because of
 * the single-writer guarantee provided by the Disruptor, the {@link UllOrderBook}
 * requires no internal synchronisation at all.
 *
 * After processing each command the handler optionally publishes the result to
 * an outbound Disruptor for async delivery to downstream consumers (risk,
 * logging, NATS).
 */
public final class OrderCommandHandler implements EventHandler<OrderCommand> {

    private final UllOrderBook book;
    private final OutboundPublisher outbound;   // may be null if no outbound bus

    public OrderCommandHandler(UllOrderBook book, OutboundPublisher outbound) {
        this.book     = book;
        this.outbound = outbound;
    }

    @Override
    public void onEvent(OrderCommand cmd, long sequence, boolean endOfBatch) {
        switch (cmd.type) {

            case OrderCommand.LIMIT_ORDER ->
                cmd.resultCode = book.submitLimit(
                        cmd.idHigh, cmd.idLow,
                        cmd.price, cmd.qty,
                        cmd.side, cmd.tif, cmd.flags,
                        cmd.u0, cmd.u1, cmd.u2, cmd.u3);

            case OrderCommand.MARKET_ORDER ->
                cmd.resultCode = book.submitMarket(
                        cmd.idHigh, cmd.idLow,
                        cmd.qty, cmd.side,
                        cmd.u0, cmd.u1, cmd.u2, cmd.u3);

            case OrderCommand.CANCEL ->
                cmd.resultCode = book.cancel(cmd.idHigh, cmd.idLow);

            case OrderCommand.MASS_CANCEL ->
                cmd.resultCode = book.massCancel();

            case OrderCommand.MASS_CANCEL_SIDE ->
                cmd.resultCode = book.massCancelSide(cmd.side);

            case OrderCommand.KILL_ENGAGE -> {
                book.engageKillSwitch();
                cmd.resultCode = ErrorCode.OK;
            }

            case OrderCommand.KILL_RELEASE -> {
                book.releaseKillSwitch();
                cmd.resultCode = ErrorCode.OK;
            }

            default -> cmd.resultCode = ErrorCode.NOT_FOUND;
        }
    }

    @FunctionalInterface
    public interface OutboundPublisher {
        void publish(MutableMatchResult result);
    }
}
