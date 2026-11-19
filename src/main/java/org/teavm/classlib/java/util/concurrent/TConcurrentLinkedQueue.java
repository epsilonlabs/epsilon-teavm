package org.teavm.classlib.java.util.concurrent;

import java.util.Collection;
import java.util.LinkedList;

/** Single-threaded stand-in for java.util.concurrent.ConcurrentLinkedQueue. */
public class TConcurrentLinkedQueue<E> extends LinkedList<E> {
    public TConcurrentLinkedQueue() {
    }

    public TConcurrentLinkedQueue(Collection<? extends E> c) {
        super(c);
    }
}
