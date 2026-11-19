package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Browser stand-in for ThreadPoolExecutor: runs every task synchronously on the
 * calling thread, so Epsilon's parallel operations degrade to sequential ones.
 */
public class TThreadPoolExecutor implements TExecutorService {
    private boolean shutdown;

    public TThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
    }

    protected void beforeExecute(Thread t, Runnable r) {
    }

    protected void afterExecute(Runnable r, Throwable t) {
    }

    @Override
    public void execute(Runnable command) {
        Throwable failure = null;
        beforeExecute(Thread.currentThread(), command);
        try {
            command.run();
        } catch (RuntimeException | Error e) {
            failure = e;
            throw e;
        } finally {
            afterExecute(command, failure);
        }
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown = true;
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        try {
            return new Done<>(task.call(), null);
        } catch (Throwable t) {
            return new Done<>(null, t);
        }
    }

    @Override
    public Future<?> submit(Runnable task) {
        return submit(() -> {
            task.run();
            return null;
        });
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        List<Future<T>> results = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            results.add(submit(task));
        }
        return results;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws ExecutionException {
        Throwable last = null;
        for (Callable<T> task : tasks) {
            try {
                return task.call();
            } catch (Throwable t) {
                last = t;
            }
        }
        throw new ExecutionException(last);
    }

    static final class Done<V> implements Future<V> {
        private final V value;
        private final Throwable error;

        Done(V value, Throwable error) {
            this.value = value;
            this.error = error;
        }

        @Override public boolean cancel(boolean b) { return false; }
        @Override public boolean isCancelled() { return false; }
        @Override public boolean isDone() { return true; }

        @Override
        public V get() throws ExecutionException {
            if (error != null) throw new ExecutionException(error);
            return value;
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws ExecutionException {
            return get();
        }
    }
}
