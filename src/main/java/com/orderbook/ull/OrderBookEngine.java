package com.orderbook.ull;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.util.concurrent.ThreadFactory;

/**
 * Ultra-low-latency order book engine backed by an LMAX Disruptor pipeline.
 *
 * <h2>Architecture</h2>
 * <pre>
 *  [Clients]  →  [Inbound RingBuffer]  →  [OrderCommandHandler (single thread)]
 *                                                   │
 *                                       UllOrderBook (no locks, no GC pressure)
 *                                                   │
 *                                          [Outbound RingBuffer]
 *                                                   │
 *                                     [TradeEventConsumer (separate thread)]
 * </pre>
 *
 * <h2>Wait strategies</h2>
 * <ul>
 *   <li>{@link BusySpinWaitStrategy} — lowest latency, burns a full CPU core.
 *       Use when latency is more important than CPU cost (co-location, HFT).</li>
 *   <li>{@link SleepingWaitStrategy} — lower CPU, slightly higher tail latency.
 *       Use for lower-frequency venues or when sharing hardware.</li>
 * </ul>
 *
 * <h2>Thread affinity</h2>
 * For true ULL, pin the handler thread to a dedicated core using the
 * {@code affinity} library (e.g., OpenHFT/Java-Thread-Affinity) after
 * constructing the engine but before starting it:
 * <pre>
 *   engine.start(core -> AffinityLock.acquireLock(dedicatedCore));
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>
 *   OrderBookEngine engine = new OrderBookEngine.Builder("BTC/USD")
 *       .ringBufferSize(1 << 17)           // 131 072 slots
 *       .waitStrategy(new BusySpinWaitStrategy())
 *       .fees(-2, 5)
 *       .build();
 *
 *   engine.start();
 *
 *   // Fire-and-forget (async); result available after barrier wait
 *   long seq = engine.submitLimit(idHigh, idLow, price, qty, BUY, GTC, (byte)0, u...);
 *
 *   // Or blocking (wait for handler to process):
 *   long result = engine.submitLimitSync(idHigh, idLow, price, qty, BUY, GTC, (byte)0, u...);
 *
 *   engine.shutdown();
 * </pre>
 */
public final class OrderBookEngine implements AutoCloseable {

    private static final int DEFAULT_RING_SIZE = 1 << 17;  // 131072

    private final Disruptor<OrderCommand> inbound;
    private final RingBuffer<OrderCommand> ringBuffer;
    private final UllOrderBook book;

    private final Disruptor<TradeOutEvent> outbound;
    private final RingBuffer<TradeOutEvent> outRing;

    private OrderBookEngine(Builder b) {
        this.book = b.book;

        // --- Outbound Disruptor (trade events → consumers) ---
        outbound = new Disruptor<>(
                TradeOutEvent.FACTORY,
                b.outRingSize,
                namedThreadFactory("ob-out-" + b.symbol),
                ProducerType.SINGLE,  // only the handler thread publishes
                b.outWaitStrategy);

        if (b.tradeConsumer != null) {
            outbound.handleEventsWith(b.tradeConsumer);
        }
        outRing = outbound.start();

        // --- Wire the match callback to publish outbound events ---
        book.withMatchCallback(result -> {
            if (result.hasFills()) {
                long outSeq = outRing.next();
                try {
                    outRing.get(outSeq).copyFrom(result);
                } finally {
                    outRing.publish(outSeq);
                }
            }
        });

        // --- Inbound Disruptor (commands → handler) ---
        inbound = new Disruptor<>(
                OrderCommand.FACTORY,
                b.inRingSize,
                namedThreadFactory("ob-in-" + b.symbol),
                ProducerType.MULTI,   // multiple client threads may publish
                b.inWaitStrategy);

        inbound.handleEventsWith(new OrderCommandHandler(book, null));
        ringBuffer = inbound.start();
    }

    // -------------------------------------------------------------------------
    // Async publish (fire-and-forget; lowest latency)
    // -------------------------------------------------------------------------

    public long submitLimit(long idHigh, long idLow, long price, long qty,
                            byte side, byte tif, byte flags,
                            long u0, long u1, long u2, long u3) {
        long seq = ringBuffer.next();
        try {
            OrderCommand cmd = ringBuffer.get(seq);
            cmd.type   = OrderCommand.LIMIT_ORDER;
            cmd.idHigh = idHigh; cmd.idLow = idLow;
            cmd.price  = price;  cmd.qty   = qty;
            cmd.side   = side;   cmd.tif   = tif;   cmd.flags = flags;
            cmd.u0 = u0; cmd.u1 = u1; cmd.u2 = u2; cmd.u3 = u3;
        } finally {
            ringBuffer.publish(seq);
        }
        return seq;
    }

    public long submitMarket(long idHigh, long idLow, long qty, byte side,
                             long u0, long u1, long u2, long u3) {
        long seq = ringBuffer.next();
        try {
            OrderCommand cmd = ringBuffer.get(seq);
            cmd.type   = OrderCommand.MARKET_ORDER;
            cmd.idHigh = idHigh; cmd.idLow = idLow;
            cmd.qty    = qty;    cmd.side  = side;
            cmd.u0 = u0; cmd.u1 = u1; cmd.u2 = u2; cmd.u3 = u3;
        } finally {
            ringBuffer.publish(seq);
        }
        return seq;
    }

    public long cancel(long idHigh, long idLow) {
        long seq = ringBuffer.next();
        try {
            OrderCommand cmd = ringBuffer.get(seq);
            cmd.type   = OrderCommand.CANCEL;
            cmd.idHigh = idHigh; cmd.idLow = idLow;
        } finally {
            ringBuffer.publish(seq);
        }
        return seq;
    }

    public long massCancel() {
        long seq = ringBuffer.next();
        try { ringBuffer.get(seq).type = OrderCommand.MASS_CANCEL; }
        finally { ringBuffer.publish(seq); }
        return seq;
    }

    public long engageKillSwitch() {
        long seq = ringBuffer.next();
        try { ringBuffer.get(seq).type = OrderCommand.KILL_ENGAGE; }
        finally { ringBuffer.publish(seq); }
        return seq;
    }

    public long releaseKillSwitch() {
        long seq = ringBuffer.next();
        try { ringBuffer.get(seq).type = OrderCommand.KILL_RELEASE; }
        finally { ringBuffer.publish(seq); }
        return seq;
    }

    // -------------------------------------------------------------------------
    // Direct (synchronous) access — use only for testing or low-frequency ops
    // -------------------------------------------------------------------------

    /**
     * Direct access to the underlying book.
     * <b>Must only be called from the handler thread</b> or when the engine
     * is stopped.  Useful for snapshotting and testing.
     */
    public UllOrderBook book() { return book; }

    public RingBuffer<OrderCommand> ringBuffer() { return ringBuffer; }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void close() {
        inbound.shutdown();
        outbound.shutdown();
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {
        private final String symbol;
        private final UllOrderBook book = new UllOrderBook();

        private int inRingSize  = DEFAULT_RING_SIZE;
        private int outRingSize = DEFAULT_RING_SIZE;

        private WaitStrategy inWaitStrategy  = new BusySpinWaitStrategy();
        private WaitStrategy outWaitStrategy = new SleepingWaitStrategy();

        private com.lmax.disruptor.EventHandler<TradeOutEvent> tradeConsumer = null;

        public Builder(String symbol) { this.symbol = symbol; }

        public Builder inRingSize(int n)   { inRingSize  = n; return this; }
        public Builder outRingSize(int n)  { outRingSize = n; return this; }

        /** Lowest latency inbound strategy: busy-spin (burns a core). */
        public Builder busySpin()  { inWaitStrategy = new BusySpinWaitStrategy(); return this; }

        /** Balanced inbound strategy: yielding (good for shared hardware). */
        public Builder yielding()  {
            inWaitStrategy = new com.lmax.disruptor.YieldingWaitStrategy(); return this;
        }

        /** Low-CPU inbound strategy: sleeping (adds ~100µs latency at low load). */
        public Builder sleeping()  { inWaitStrategy = new SleepingWaitStrategy(); return this; }

        public Builder tickSize(long v)     { book.withTickSize(v);     return this; }
        public Builder lotSize(long v)      { book.withLotSize(v);      return this; }
        public Builder minSize(long v)      { book.withMinOrderSize(v); return this; }
        public Builder maxSize(long v)      { book.withMaxOrderSize(v); return this; }
        public Builder stpMode(int mode)    { book.withStpMode(mode);   return this; }
        public Builder fees(int maker, int taker) { book.withFees(maker, taker); return this; }

        /** Consumer for outbound trade events (runs on a separate thread). */
        public Builder tradeConsumer(com.lmax.disruptor.EventHandler<TradeOutEvent> c) {
            tradeConsumer = c; return this;
        }

        public OrderBookEngine build() { return new OrderBookEngine(this); }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static ThreadFactory namedThreadFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }
}
