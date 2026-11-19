package org.teavm.classlib.java.sql;

/** Minimal java.sql.Timestamp, present so that libraries such as Gson that know about it can be compiled. */
public class TTimestamp extends java.util.Date {
    private static final long serialVersionUID = 1L;

    public TTimestamp(long time) {
        super(time);
    }
}
