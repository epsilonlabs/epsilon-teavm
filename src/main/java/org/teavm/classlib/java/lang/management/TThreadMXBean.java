package org.teavm.classlib.java.lang.management;

/** The subset of ThreadMXBean used by Epsilon (EUnit's CPU timings). The browser has no per-thread CPU clock. */
public interface TThreadMXBean {
    boolean isCurrentThreadCpuTimeSupported();
    boolean isThreadCpuTimeSupported();
    boolean isThreadCpuTimeEnabled();
    void setThreadCpuTimeEnabled(boolean enable);
    long getCurrentThreadCpuTime();
    long getCurrentThreadUserTime();
}
