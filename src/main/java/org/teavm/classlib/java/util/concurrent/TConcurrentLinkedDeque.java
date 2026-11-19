package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayDeque;
import java.util.Collection;

/** The browser is single-threaded, so a plain ArrayDeque is a valid stand-in. */
public class TConcurrentLinkedDeque<E> extends ArrayDeque<E> {
    public TConcurrentLinkedDeque() {
    }

    public TConcurrentLinkedDeque(Collection<? extends E> c) {
        super(c);
    }
}
