package org.teavm.classlib.java.util.concurrent.locks;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * Single-threaded ReentrantLock: tracks the hold count so that the queries code
 * makes about the lock (isLocked, getHoldCount, ...) give the same answers.
 * Waiting on a condition can never be woken by another thread, so it returns at once.
 */
public class TReentrantLock implements Lock {
    private int holds;

    public TReentrantLock() {
    }

    public TReentrantLock(boolean fair) {
    }

    @Override public void lock() { holds++; }
    @Override public void lockInterruptibly() { holds++; }
    @Override public boolean tryLock() { holds++; return true; }
    @Override public boolean tryLock(long time, TimeUnit unit) { holds++; return true; }

    @Override
    public void unlock() {
        if (holds == 0) {
            throw new IllegalMonitorStateException();
        }
        holds--;
    }

    public boolean isLocked() { return holds > 0; }
    public boolean isHeldByCurrentThread() { return holds > 0; }
    public int getHoldCount() { return holds; }
    public boolean isFair() { return false; }
    public boolean hasQueuedThreads() { return false; }
    public int getQueueLength() { return 0; }

    @Override
    public Condition newCondition() {
        return new Condition() {
            @Override public void await() { }
            @Override public void awaitUninterruptibly() { }
            @Override public long awaitNanos(long nanos) { return 0; }
            @Override public boolean await(long time, TimeUnit unit) { return false; }
            @Override public boolean awaitUntil(Date deadline) { return false; }
            @Override public void signal() { }
            @Override public void signalAll() { }
        };
    }
}
