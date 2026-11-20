package org.teavm.classlib.java.lang;

import java.security.Permission;

public class TRuntimePermission extends Permission {
    public TRuntimePermission(String name) { super(name); }
    public TRuntimePermission(String name, String actions) { super(name); }
    @Override public boolean implies(Permission p) { return p != null && p.getClass() == getClass() && p.getName().equals(getName()); }
    @Override public boolean equals(Object o) { return o instanceof TRuntimePermission && ((TRuntimePermission) o).getName().equals(getName()); }
    @Override public int hashCode() { return getName().hashCode(); }
    @Override public String getActions() { return ""; }
}
