package org.teavm.classlib.java.util.concurrent.locks;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

public class TReentrantReadWriteLock implements ReadWriteLock {
    private final ReentrantLock readLock = new ReentrantLock();
    private final ReentrantLock writeLock = new ReentrantLock();

    public TReentrantReadWriteLock() {
    }

    public TReentrantReadWriteLock(boolean fair) {
    }

    @Override public Lock readLock() { return readLock; }
    @Override public Lock writeLock() { return writeLock; }
}
