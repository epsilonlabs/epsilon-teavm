package org.teavm.classlib.java.lang.management;

public final class TManagementFactory {
    private static final TThreadMXBean THREAD_MX_BEAN = new TThreadMXBean() {
        @Override public boolean isCurrentThreadCpuTimeSupported() { return false; }
        @Override public boolean isThreadCpuTimeSupported() { return false; }
        @Override public boolean isThreadCpuTimeEnabled() { return false; }
        @Override public void setThreadCpuTimeEnabled(boolean enable) { }
        // As the JDK does when CPU time measurement is unsupported or disabled
        @Override public long getCurrentThreadCpuTime() { return -1; }
        @Override public long getCurrentThreadUserTime() { return -1; }
    };

    private TManagementFactory() {
    }

    public static TThreadMXBean getThreadMXBean() {
        return THREAD_MX_BEAN;
    }
}
