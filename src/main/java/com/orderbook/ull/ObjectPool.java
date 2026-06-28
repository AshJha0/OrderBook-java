package com.orderbook.ull;

import java.util.ArrayDeque;
import java.util.function.Supplier;

/**
 * A simple, non-thread-safe object pool sized for the single-writer thread.
 * Designed to be field-allocated once per book and reused in steady state.
 *
 * <p>Not thread-safe by design — the caller (UllOrderBook / OrderCommandHandler)
 * runs on a single dedicated Disruptor thread.</p>
 *
 * @param <T> the pooled type
 */
public final class ObjectPool<T> {

    private final ArrayDeque<T> pool;
    private final Supplier<T>   factory;
    private final int           maxSize;

    private int borrowed = 0;

    public ObjectPool(int initialSize, int maxSize, Supplier<T> factory) {
        this.factory = factory;
        this.maxSize = maxSize;
        this.pool    = new ArrayDeque<>(initialSize);
        for (int i = 0; i < initialSize; i++) pool.addLast(factory.get());
    }

    /** Borrow an object from the pool (or allocate a fresh one if empty). */
    public T borrow() {
        T obj = pool.pollFirst();
        if (obj == null) obj = factory.get();
        borrowed++;
        return obj;
    }

    /** Return an object to the pool so it can be reused. */
    public void release(T obj) {
        borrowed--;
        if (pool.size() < maxSize) pool.addLast(obj);
    }

    public int borrowed() { return borrowed; }
    public int pooled()   { return pool.size(); }
}
