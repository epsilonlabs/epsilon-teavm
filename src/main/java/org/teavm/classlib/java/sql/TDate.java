package org.teavm.classlib.java.sql;

/** Minimal java.sql.Date, present so that libraries such as Gson that know about it can be compiled. */
public class TDate extends java.util.Date {
    private static final long serialVersionUID = 1L;

    public TDate(long time) {
        super(time);
    }
}
