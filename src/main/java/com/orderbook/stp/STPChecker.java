package com.orderbook.stp;

import com.orderbook.model.Hash32;
import com.orderbook.model.OrderType;

import java.util.List;

/**
 * Stateless STP check logic — mirrors Rust's {@code check_stp_at_level}.
 */
public final class STPChecker {

    private STPChecker() {}

    /**
     * Scan orders at a price level and determine the STP action.
     *
     * @param orders      resting orders at this level in FIFO order
     * @param takerUserId the incoming taker's user id
     * @param mode        the active STP mode
     */
    public static <T> STPAction checkAtLevel(
            List<OrderType<T>> orders,
            Hash32 takerUserId,
            STPMode mode
    ) {
        if (!mode.isEnabled() || takerUserId.isZero()) {
            return new STPAction.NoConflict();
        }

        return switch (mode) {
            case NONE -> new STPAction.NoConflict();

            case CANCEL_TAKER -> {
                long safe = 0L;
                for (OrderType<T> o : orders) {
                    if (o.userId().equals(takerUserId)) {
                        yield new STPAction.CancelTaker(safe);
                    }
                    safe += o.visibleQuantity().value();
                }
                yield new STPAction.NoConflict();
            }

            case CANCEL_MAKER -> {
                boolean hasSelf = orders.stream().anyMatch(o -> o.userId().equals(takerUserId));
                yield hasSelf ? new STPAction.CancelMaker() : new STPAction.NoConflict();
            }

            case CANCEL_BOTH -> {
                long safe = 0L;
                for (OrderType<T> o : orders) {
                    if (o.userId().equals(takerUserId)) {
                        yield new STPAction.CancelBoth(safe, o.id());
                    }
                    safe += o.visibleQuantity().value();
                }
                yield new STPAction.NoConflict();
            }
        };
    }
}
