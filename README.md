# OrderBook — Java

An **ultra-low-latency** limit order book engine written in Java 24.

Two implementations are provided:

| Package | Latency profile | Use case |
|---|---|---|
| `com.orderbook` | ~1–10 µs | Idiomatic Java, full feature set, easy to extend |
| `com.orderbook.ull` | ~100–500 ns | Production HFT / co-location |

---

## Features

- **Price-time priority matching** — FIFO within each price level, best price first across levels
- **10 order types** — Standard, Iceberg, PostOnly, FillOrKill, ImmediateOrCancel, GoodTillDate, TrailingStop, PeggedOrder, MarketToLimit, Reserve
- **Self-Trade Prevention (STP)** — CancelTaker, CancelMaker, CancelBoth modes
- **Fee engine** — Maker/taker fees in basis points; negative maker bps = rebate
- **Pre-trade risk gates** — Per-account open-order count, notional limit, price-band check
- **Kill switch** — Halt new flow instantly; cancel paths remain open to drain the book
- **Order state tracking** — Open → PartiallyFilled → Filled / Cancelled / Rejected lifecycle
- **Event sourcing** — In-memory and file-backed write-ahead journals with deterministic replay
- **Snapshots** — JSON-serialisable point-in-time snapshots with SHA-256 checksums
- **Market impact simulation** — Estimate slippage before executing a large order
- **Implied volatility** — Black-Scholes pricing + Newton-Raphson IV solver
- **NATS integration** — Publish trade and book-change events to NATS JetStream
- **Binary wire protocol** — Little-endian packed messages (NewOrder 48 B, ExecReport 44 B, BookUpdate 25 B, TradePrint 41 B)

---

## Requirements

| Tool | Version |
|------|---------|
| Java | 24+ (OpenJDK or compatible) |
| Maven | 3.9+ |

Developed with OpenJDK 24.0.1. Uses Java 24 sealed classes, records, and pattern matching.

---

## Build & Test

```bash
# Compile
mvn compile

# Run all 50 tests (25 reference + 25 ULL)
mvn test

# Build a JAR
mvn package
```

---

## Project Structure

```
src/main/java/com/orderbook/
├── OrderBook.java                  # Core order book (reference implementation)
├── OrderBookException.java         # Sealed exception hierarchy (18 subtypes)
├── OrderBookSnapshotter.java       # Snapshot capture utility
├── Prelude.java                    # Convenience re-exports
│
├── model/
│   ├── Side.java                   # BUY / SELL
│   ├── OrderType.java              # Sealed hierarchy of 10 order variants
│   ├── TimeInForce.java            # GTC / IOC / FOK / GTD / DAY
│   ├── Id.java                     # UUID-backed order / transaction id
│   ├── Hash32.java                 # 32-byte user identifier (STP key)
│   ├── Price.java                  # Unsigned fixed-point price newtype
│   ├── Quantity.java               # Unsigned quantity newtype
│   ├── TimestampMs.java            # Millisecond timestamp newtype
│   ├── Clock.java                  # Pluggable clock (MonotonicClock, StubClock)
│   ├── PriceLevelEntry.java        # FIFO queue of orders at one price
│   ├── MatchResult.java            # Fills accumulated during one match sweep
│   ├── Transaction.java            # A single maker-taker fill
│   ├── TradeResult.java            # Match result + fees + engine sequence
│   ├── TradeInfo.java              # Display-oriented trade breakdown
│   ├── TransactionInfo.java        # Per-fill detail including fees
│   ├── TradeEvent.java             # Trade event envelope
│   ├── PriceLevelChangedEvent.java # Book-change event
│   ├── OrderStatus.java            # Sealed order lifecycle states
│   ├── OrderStateTracker.java      # Optional per-order state recording
│   ├── RejectReason.java           # Rejection reason enum
│   ├── CancelReason.java           # Cancellation reason enum
│   ├── MarketImpact.java           # Market impact analysis result
│   └── OrderSimulation.java        # Simulated fill breakdown
│
├── fees/
│   └── FeeSchedule.java            # Maker/taker fees in bps; negative = rebate
│
├── stp/
│   ├── STPMode.java                # NONE / CANCEL_TAKER / CANCEL_MAKER / CANCEL_BOTH
│   ├── STPAction.java              # Sealed result of a per-level STP scan
│   └── STPChecker.java             # Stateless STP logic
│
├── risk/
│   ├── RiskConfig.java             # Per-book limits (open orders, notional, price band)
│   ├── RiskState.java              # Live counters + config; checks new admissions
│   ├── RiskCounters.java           # Atomic per-account counters
│   ├── RiskEntry.java              # Per-order entry for counter reconciliation
│   └── ReferencePriceSource.java   # Sealed: LastTrade / Mid / FixedPrice
│
├── snapshot/
│   ├── OrderBookSnapshot.java      # Bid/ask level list + metadata
│   ├── OrderBookSnapshotPackage.java # Snapshot + config (round-trip restore)
│   └── PriceLevelSnapshot.java     # Single price level summary
│
├── statistics/
│   ├── DepthStats.java             # VWAP, std dev, min/max for one side
│   └── DistributionBin.java        # Price-range bucket for depth distribution
│
├── sequencer/
│   ├── Journal.java                # Write-ahead log interface
│   ├── InMemoryJournal.java        # In-memory journal for tests
│   ├── FileJournal.java            # Newline-delimited JSON file journal
│   ├── JournalEntry.java           # Command + sequence number + nanosecond ts
│   ├── JournalReplay.java          # Replay a journal into a fresh OrderBook
│   └── SequencerCommand.java       # Sealed command enum (8 variants)
│
├── implied_volatility/
│   ├── BlackScholes.java           # BS pricing model + vega (Abramowitz & Stegun erf)
│   ├── IVSolver.java               # Newton-Raphson IV solver
│   ├── IVParams.java               # Option parameters (spot, strike, TTE, RFR)
│   ├── IVResult.java               # IV + quality indicator + iteration count
│   ├── IVQuality.java              # HIGH / MEDIUM / LOW / INTERPOLATED
│   └── OptionType.java             # CALL / PUT
│
├── wire/
│   ├── WireError.java              # Sealed wire exception hierarchy
│   ├── inbound/
│   │   ├── NewOrderWire.java       # 48-byte LE new-order message
│   │   └── CancelWire.java         # 16-byte LE cancel message
│   └── outbound/
│       ├── ExecReport.java         # 44-byte execution report
│       ├── BookUpdate.java         # 25-byte book-level update
│       └── TradePrint.java         # 41-byte trade print
│
├── nats/
│   ├── NatsTradePublisher.java     # Publish TradeResult → NATS subject
│   └── NatsBookChangePublisher.java # Publish PriceLevelChangedEvent → NATS subject
│
└── ull/                            # Ultra-low-latency engine (see section below)
    ├── ErrorCode.java
    ├── PaddedLong.java
    ├── ObjectPool.java
    ├── UllOrderNode.java
    ├── UllPriceLevel.java
    ├── MutableFill.java
    ├── MutableMatchResult.java
    ├── UllOrderBook.java
    ├── OrderCommand.java
    ├── TradeOutEvent.java
    ├── OrderCommandHandler.java
    └── OrderBookEngine.java
```

---

## Quick Start (Reference Implementation)

```java
import com.orderbook.OrderBook;
import com.orderbook.model.*;
import com.orderbook.fees.FeeSchedule;
import com.orderbook.stp.STPMode;

// Create a book
OrderBook<Void> book = new OrderBook<>("BTC/USD");

// Optional configuration (fluent)
book.withFeeSchedule(FeeSchedule.of(-2, 5))   // -2 bps maker rebate, 5 bps taker
    .withSTPMode(STPMode.CANCEL_TAKER)
    .withTickSize(100L)
    .withMinOrderSize(1L)
    .withMaxOrderSize(10_000L);

// Rest a sell limit order
OrderType<Void> sellOrder = new OrderType.Standard<>(
    Id.newUuid(),
    Price.of(5_000_000L),           // $50,000.00 in cents
    Quantity.of(10),
    Side.SELL,
    Hash32.ZERO,
    TimestampMs.of(System.currentTimeMillis()),
    TimeInForce.GTC,
    null
);
book.submitOrder(sellOrder);

// Submit a buy that crosses the spread — matches immediately
TradeResult result = book.submitOrder(new OrderType.Standard<>(
    Id.newUuid(),
    Price.of(5_000_000L),
    Quantity.of(10),
    Side.BUY,
    Hash32.ZERO,
    TimestampMs.of(System.currentTimeMillis()),
    TimeInForce.GTC,
    null
));

System.out.println("Filled:    " + result.matchResult.executedQuantity().value());
System.out.println("Taker fee: " + result.totalTakerFees);

// Market order
book.submitMarketOrder(Side.BUY, 5, Hash32.ZERO);

// Cancel
book.cancelOrder(sellOrder.id());

// Depth
book.depth(Side.BUY, 10).forEach(level ->
    System.out.println("  price=" + level[0] + " qty=" + level[1]));

// Kill switch
book.engageKillSwitch();   // rejects new orders
book.massCancel();         // drain resting orders
book.releaseKillSwitch();  // resume
```

### Event Listeners

```java
book.withTradeListener(trade ->
    System.out.println("seq=" + trade.engineSeq
        + " qty=" + trade.matchResult.executedQuantity().value()));

book.withPriceLevelChangedListener(event ->
    System.out.println(event.side + " " + event.price + " qty=" + event.quantity));
```

### Snapshots

```java
import com.orderbook.OrderBookSnapshotter;
import com.orderbook.snapshot.OrderBookSnapshotPackage;

OrderBookSnapshotPackage pkg = OrderBookSnapshotter.snapshot(book);
System.out.println("Checksum: " + pkg.checksum);
System.out.println("JSON: "     + pkg.snapshot.toJson());
```

### Journal / Replay

```java
import com.orderbook.sequencer.*;

Journal<Void> journal = new InMemoryJournal<>();
journal.append(new SequencerCommand.AddOrder<>(sellOrder));

OrderBook<Void> freshBook = new OrderBook<>("BTC/USD");
JournalReplay.replayAll(journal, freshBook);

// File-backed journal
try (var fj = new FileJournal<>(Path.of("orders.ndjson"), Void.class)) {
    fj.append(new SequencerCommand.AddOrder<>(sellOrder));
}
```

### Implied Volatility

```java
import com.orderbook.implied_volatility.*;

IVSolver.solve(
    IVParams.call(100.0, 105.0, 0.25, 0.05),  // spot, strike, TTE, RFR
    3.50,   // market price
    80.0    // spread in bps
).ifPresent(r -> System.out.printf("IV = %.2f%% (%s)%n", r.ivPercent(), r.quality()));
```

### NATS Publishing

```java
import com.orderbook.nats.*;

try (var pub = new NatsTradePublisher("nats://localhost:4222", "trades.BTCUSD")) {
    book.withTradeListener(pub);
}
```

---

## Ultra-Low-Latency Engine (`com.orderbook.ull`)

### Why it's fast

| Reference implementation | ULL implementation | Latency saving |
|---|---|---|
| `ReentrantLock` per price level | No locks — LMAX Disruptor single-writer | Eliminates OS mutex syscalls |
| `new MatchResult()` per order | Pool-borrowed `MutableMatchResult`, reused | Zero GC pressure on hot path |
| `new Transaction()` per fill | `MutableFill[]` pre-allocated at startup | Zero GC pressure on hot path |
| `ConcurrentHashMap<Id, long[]>` | Agrona `Long2ObjectHashMap<UllOrderNode>` | No boxing, open-addressing hash |
| `Optional<Price>` for best bid/ask | `long` with `NO_PRICE` sentinel | No object allocation |
| Exceptions on validation failure | `long` error codes (`ErrorCode.*`) | No stack trace allocation |
| `ArrayList` + per-level lock | Ring-buffer `UllPriceLevel` | Cache-friendly, no locking |
| Reconstructed order record on partial fill | In-place `node.quantity -= fill` | No object churn |
| `AtomicLong` engine sequence | Cache-line–padded `PaddedLong` | Eliminates false sharing |

The engine achieves ultra-low latency through:
- **LMAX Disruptor** — single-writer principle; one dedicated thread owns the book, eliminating every lock and CAS from the matching loop
- **Pre-allocated object pools** — `MutableMatchResult` and `MutableFill` arrays are allocated once at startup and reused in steady state
- **Flat array order queues** — `UllPriceLevel` is a ring-buffer of `UllOrderNode` slots; FIFO matching touches a contiguous memory region
- **Cache-line–padded sequence counter** — `PaddedLong` (8 bytes value + 56 bytes padding) prevents false sharing between the writer and Disruptor consumers
- **Primitive-only hot path** — no `Optional`, no boxing, no exceptions; all results are `long` error codes or executed quantities
- **Agrona `Long2ObjectHashMap`** — open-addressing with primitive `long` keys; ~4× less overhead than `ConcurrentHashMap` under single-writer access
- **ZGC / Generational ZGC** — sub-millisecond GC pauses for any off-path allocations

### ULL Package Structure

```
ull/
├── ErrorCode.java           # long constants replacing exceptions on the hot path
├── PaddedLong.java          # Cache-line–padded volatile long (64-byte aligned)
├── ObjectPool.java          # Non-thread-safe pre-allocation pool (single-writer safe)
├── UllOrderNode.java        # Flat mutable order slot — all primitives, no boxing
├── UllPriceLevel.java       # Ring-buffer FIFO per price level; no locks
│   ├── FillCallback         # Primitive fill callback (makerIdH, makerIdL, qty, price, ...)
│   └── STPCheck             # Primitive STP callback — returns NONE/TAKER/MAKER/BOTH int
├── MutableFill.java         # Pre-allocated fill record, reset and reused per order
├── MutableMatchResult.java  # Pool-borrowed result; 256 pre-allocated MutableFill slots
├── OrderCommand.java        # Inbound Disruptor ring-buffer slot (mutated in-place)
├── TradeOutEvent.java       # Outbound Disruptor slot — flat long[] fill data
├── OrderCommandHandler.java # The one thread that owns UllOrderBook
└── OrderBookEngine.java     # Disruptor lifecycle, fluent builder, publisher API
```

### Quick Start (ULL Engine)

```java
import com.orderbook.ull.*;

// Build the engine
OrderBookEngine engine = new OrderBookEngine.Builder("BTC/USD")
    .inRingSize(1 << 17)       // 131,072 inbound ring-buffer slots
    .busySpin()                 // BusySpinWaitStrategy — lowest latency, burns one core
    .fees(-2, 5)                // -2 bps maker rebate, 5 bps taker fee
    .tradeConsumer((event, seq, endOfBatch) -> {
        // Runs on a separate outbound thread — copy any data you need
        for (int i = 0; i < event.fillCount; i++) {
            System.out.printf("fill qty=%d @ price=%d%n",
                event.fillQty(i), event.fillPrice(i));
        }
    })
    .build();

// Fire-and-forget limit order (async, returns ring-buffer sequence number)
long[] sellId = {0xABCD_0000L, 0x1234_5678L};
engine.submitLimit(
    sellId[0], sellId[1],
    100_00L,    // price: $100.00 in cents
    10L,        // quantity
    (byte) 1,   // side: 1 = SELL
    (byte) 0,   // tif:  0 = GTC
    (byte) 0,   // flags: 0 = none
    0L, 0L, 0L, 0L);   // anonymous user (zero userId)

// Market order
long[] buyId = {0xDEAD_BEEFL, 0x0000_0001L};
engine.submitMarket(buyId[0], buyId[1], 5L, (byte) 0, 0L, 0L, 0L, 0L);

// Cancel
engine.cancel(sellId[0], sellId[1]);

// Kill switch
engine.engageKillSwitch();
engine.massCancel();
engine.releaseKillSwitch();

engine.close();
```

### Direct Book Access (Testing / Snapshots)

Bypass the Disruptor for synchronous, single-threaded use (tests, snapshots):

```java
UllOrderBook book = new UllOrderBook()
    .withTickSize(100L)
    .withFees(-2, 5)
    .withStpMode(1);    // 0=NONE  1=CANCEL_TAKER  2=CANCEL_MAKER  3=CANCEL_BOTH

book.withMatchCallback(result -> {
    System.out.println("executed=" + result.executedQty
        + " remaining=" + result.remainingQty
        + " fills=" + result.fillCount);
});

long[] id = {0xABCDL, 0x1234L};
book.submitLimit(id[0], id[1], 10000L, 10L,
    (byte) 0,   // BUY
    (byte) 0,   // GTC
    (byte) 0,   // no flags
    0L, 0L, 0L, 0L);

long bestBid = book.bestBidRaw();   // Long.MIN_VALUE when empty
long spread  = book.spreadRaw();    // Long.MIN_VALUE when either side empty

long[][] depth = new long[10][2];   // pre-allocate once, reuse
int levels = book.depth((byte) 0, depth, 10);
for (int i = 0; i < levels; i++) {
    System.out.println("price=" + depth[i][0] + " qty=" + depth[i][1]);
}
```

### Error Codes

The ULL hot path never throws. All methods return a `long`:

| Return value | Meaning |
|---|---|
| `>= 0` | Success — value is the executed quantity (0 = resting, >0 = filled) |
| `ErrorCode.KILL_SWITCH` (-1) | Order rejected — kill switch is active |
| `ErrorCode.DUPLICATE_ID` (-2) | Order id already in the book |
| `ErrorCode.INVALID_TICK` (-3) | Price not a multiple of tick size |
| `ErrorCode.INVALID_LOT` (-4) | Quantity not a multiple of lot size |
| `ErrorCode.SIZE_TOO_SMALL` (-5) | Quantity below minimum order size |
| `ErrorCode.SIZE_TOO_LARGE` (-6) | Quantity above maximum order size |
| `ErrorCode.MISSING_USER` (-7) | Zero userId submitted with STP enabled |
| `ErrorCode.NOT_FOUND` (-8) | Cancel target not found |
| `ErrorCode.FOK_CANCELLED` (-13) | FOK order cancelled — insufficient liquidity |

### JVM Flags for Production

```bash
java \
  -XX:+UseZGC -XX:+ZGenerational \
  -Xms2g -Xmx2g \
  -XX:+AlwaysPreTouch \
  -XX:+DisableExplicitGC \
  --add-opens=java.base/java.nio=ALL-UNNAMED \
  -cp your-app.jar \
  com.example.YourMainClass
```

For sub-100 ns tail latency on co-located hardware, pin the Disruptor event-handler thread to a dedicated physical core using [OpenHFT/Java-Thread-Affinity](https://github.com/OpenHFT/Java-Thread-Affinity) and isolate that core from the OS scheduler (`isolcpus` on Linux).

### Wait Strategy Guide

| Strategy | Latency | CPU cost | When to use |
|---|---|---|---|
| `BusySpinWaitStrategy` | Lowest (~50–200 ns) | Burns one full core | Co-location, HFT, dedicated hardware |
| `YieldingWaitStrategy` | Low (~1–5 µs) | High, shares core | Shared server, multiple books |
| `SleepingWaitStrategy` | Higher (~10–100 µs) | Low | Low-frequency venues, background processing |

---

## Dependencies

| Dependency | Purpose |
|---|---|
| **`lmax-disruptor 4.0.0`** | Lock-free ring buffer for the ULL inbound/outbound pipeline |
| **`agrona 1.22.0`** | Primitive collections (`Long2ObjectHashMap`), off-heap buffers |
| `jackson-databind 2.17.1` | JSON serialisation for snapshots, journals, events |
| `slf4j-api 2.0.13` + `logback-classic 1.5.6` | Structured logging (never on the matching hot path) |
| `jnats 2.20.2` *(optional)* | NATS JetStream publishing |
| `jmh-core 1.37` *(test)* | JMH microbenchmarks for the ULL engine |
| `junit-jupiter 5.10.3` *(test)* | Unit tests |
