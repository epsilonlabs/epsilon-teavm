package org.teavm.classlib.java.sql;

/** Minimal java.sql.Time, present so that libraries such as Gson that know about it can be compiled. */
public class TTime extends java.util.Date {
    private static final long serialVersionUID = 1L;

    public TTime(long time) {
        super(time);
    }
}
