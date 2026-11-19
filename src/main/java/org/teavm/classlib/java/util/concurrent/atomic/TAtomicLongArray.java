package org.teavm.classlib.java.util.concurrent.atomic;

import java.util.Arrays;

/** Single-threaded AtomicLongArray. */
public class TAtomicLongArray {
    private final long[] array;

    public TAtomicLongArray(int length) { array = new long[length]; }
    public TAtomicLongArray(long[] values) { array = values.clone(); }

    public final int length() { return array.length; }
    public final long get(int i) { return array[i]; }
    public final void set(int i, long v) { array[i] = v; }
    public final void lazySet(int i, long v) { array[i] = v; }
    public final long getAndSet(int i, long v) { long old = array[i]; array[i] = v; return old; }
    public final boolean compareAndSet(int i, long expect, long update) {
        if (array[i] != expect) { return false; }
        array[i] = update;
        return true;
    }
    public final boolean weakCompareAndSet(int i, long expect, long update) { return compareAndSet(i, expect, update); }
    public final long getAndIncrement(int i) { return array[i]++; }
    public final long getAndDecrement(int i) { return array[i]--; }
    public final long getAndAdd(int i, long d) { long old = array[i]; array[i] += d; return old; }
    public final long incrementAndGet(int i) { return ++array[i]; }
    public final long decrementAndGet(int i) { return --array[i]; }
    public final long addAndGet(int i, long d) { return array[i] += d; }
    @Override public String toString() { return Arrays.toString(array); }
}
