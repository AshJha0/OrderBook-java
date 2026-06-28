package com.orderbook;

import com.orderbook.model.Side;
import com.orderbook.model.OrderType;
import com.orderbook.snapshot.OrderBookSnapshot;
import com.orderbook.snapshot.OrderBookSnapshotPackage;
import com.orderbook.snapshot.PriceLevelSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for taking and restoring order book snapshots.
 *
 * <p>Separation from {@link OrderBook} keeps the core book class clean and
 * makes the snapshot path easy to test in isolation.</p>
 */
public final class OrderBookSnapshotter {

    private OrderBookSnapshotter() {}

    /**
     * Capture a {@link OrderBookSnapshotPackage} from the current book state.
     */
    public static <T> OrderBookSnapshotPackage snapshot(OrderBook<T> book) throws OrderBookException {
        long ts = System.currentTimeMillis();
        List<PriceLevelSnapshot> bids = levelSnapshots(book, Side.BUY);
        List<PriceLevelSnapshot> asks = levelSnapshots(book, Side.SELL);

        OrderBookSnapshot snap = new OrderBookSnapshot(book.symbol(), ts, bids, asks);
        String checksum = snap.checksum();

        return new OrderBookSnapshotPackage(
                snap,
                checksum,
                book.engineSeq(),
                book.isKillSwitchEngaged(),
                book.stpMode(),
                book.tickSize(),
                book.lotSize(),
                book.minOrderSize(),
                book.maxOrderSize(),
                book.feeSchedule(),
                book.riskConfig()
        );
    }

    private static <T> List<PriceLevelSnapshot> levelSnapshots(OrderBook<T> book, Side side) {
        List<PriceLevelSnapshot> result = new ArrayList<>();
        for (var entry : book.depthEntries(side)) {
            long price = entry[0];
            long qty   = entry[1];
            int  count = (int) entry[2];
            result.add(PriceLevelSnapshot.of(price, qty, qty, count));
        }
        return result;
    }
}
