package org.teavm.classlib.java.util.concurrent;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Objects;

/**
 * Single-threaded stand-in for java.util.concurrent.ConcurrentLinkedQueue.
 * It mirrors the JDK's internal call structure where subclasses depend on it:
 * add() goes through offer(), while addAll(), remove() and contains() do not,
 * so subclasses such as Epsilon's SizeCachingConcurrentQueue count elements correctly.
 */
public class TConcurrentLinkedQueue<E> extends AbstractQueue<E> {
    private final LinkedList<E> items = new LinkedList<>();

    public TConcurrentLinkedQueue() {
    }

    public TConcurrentLinkedQueue(Collection<? extends E> c) {
        for (E e : c) {
            items.add(Objects.requireNonNull(e));
        }
    }

    @Override
    public boolean add(E e) {
        return offer(e);
    }

    @Override
    public boolean offer(E e) {
        items.add(Objects.requireNonNull(e));
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        if (c == this) {
            throw new IllegalArgumentException();
        }
        boolean changed = false;
        for (E e : c) {
            items.add(Objects.requireNonNull(e));
            changed = true;
        }
        return changed;
    }

    @Override
    public E poll() {
        return items.poll();
    }

    @Override
    public E peek() {
        return items.peek();
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean contains(Object o) {
        return o != null && items.contains(o);
    }

    @Override
    public boolean remove(Object o) {
        return o != null && items.remove(o);
    }

    @Override
    public Iterator<E> iterator() {
        return items.iterator();
    }
}
