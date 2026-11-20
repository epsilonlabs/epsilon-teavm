package org.teavm.classlib.java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

public class TReentrantReadWriteLock implements ReadWriteLock {
    private final ReadLock readLock = new ReadLock();
    private final WriteLock writeLock = new WriteLock();

    public TReentrantReadWriteLock() {
    }

    public TReentrantReadWriteLock(boolean fair) {
    }

    @Override public ReadLock readLock() { return readLock; }
    @Override public WriteLock writeLock() { return writeLock; }

    static class DelegatingLock implements Lock {
        private final ReentrantLock lock = new ReentrantLock();
        @Override public void lock() { lock.lock(); }
        @Override public void lockInterruptibly() throws InterruptedException { lock.lockInterruptibly(); }
        @Override public boolean tryLock() { return lock.tryLock(); }
        @Override public boolean tryLock(long time, TimeUnit unit) throws InterruptedException { return lock.tryLock(time, unit); }
        @Override public void unlock() { lock.unlock(); }
        @Override public Condition newCondition() { return lock.newCondition(); }
    }

    public static class ReadLock extends DelegatingLock {
    }

    public static class WriteLock extends DelegatingLock {
    }
}
