package org.teavm.classlib.java.util;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Minimal java.util.Scanner emulation for TeaVM, sufficient for Epsilon's
 * "read the whole stream" idiom: new Scanner(in).useDelimiter("\\A").next().
 */
public class TScanner implements Closeable {
    private String remaining;

    public TScanner(InputStream in) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            remaining = new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            remaining = "";
        }
    }

    public TScanner(String source) {
        remaining = source;
    }

    public TScanner useDelimiter(String pattern) {
        // Only the "\\A" (whole input) delimiter is supported.
        return this;
    }

    public boolean hasNext() {
        return remaining != null && !remaining.isEmpty();
    }

    public String next() {
        String result = remaining;
        remaining = "";
        return result;
    }

    @Override
    public void close() {
        remaining = null;
    }
}
