package org.teavm.classlib.java.io;

import java.io.IOException;
import java.io.OutputStream;

/** Java serialization is not available in the browser: construction always fails. */
public class TObjectOutputStream extends OutputStream {
    public TObjectOutputStream(OutputStream stream) throws IOException {
        throw new UnsupportedOperationException("Java serialization is not available in the browser");
    }
    public void writeObject(Object o) throws IOException { throw new UnsupportedOperationException(); }
    @Override public void write(int b) { throw new UnsupportedOperationException(); }
}
