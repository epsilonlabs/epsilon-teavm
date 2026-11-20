package org.teavm.classlib.java.io;

import java.io.IOException;
import java.io.InputStream;

/** Java serialization is not available in the browser: construction always fails. */
public class TObjectInputStream extends InputStream {
    public TObjectInputStream(InputStream stream) throws IOException {
        throw new UnsupportedOperationException("Java serialization is not available in the browser");
    }
    public Object readObject() throws IOException, ClassNotFoundException { throw new UnsupportedOperationException(); }
    @Override public int read() { throw new UnsupportedOperationException(); }
}
