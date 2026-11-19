package org.teavm.classlib.java.util;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

/**
 * Minimal java.util.Scanner emulation for TeaVM. Supports the idioms Epsilon uses:
 * reading a whole input with useDelimiter("\\A").next(), and line-by-line reading
 * with hasNextLine()/nextLine() (with the JDK's set of line separators).
 */
public class TScanner implements Closeable {
    private String text;
    private int pos;

    public TScanner(InputStream in) {
        text = readAll(in);
    }

    public TScanner(File file) throws FileNotFoundException {
        try (InputStream in = new FileInputStream(file)) {
            text = readAll(in);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            text = "";
        }
    }

    public TScanner(String source) {
        text = source;
    }

    private static String readAll(InputStream in) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    public TScanner useDelimiter(String pattern) {
        // Only the "\\A" (whole input) delimiter is supported.
        return this;
    }

    public boolean hasNext() {
        return pos < text.length();
    }

    public String next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        String result = text.substring(pos);
        pos = text.length();
        return result;
    }

    public boolean hasNextLine() {
        return pos < text.length();
    }

    public String nextLine() {
        if (!hasNextLine()) {
            throw new NoSuchElementException("No line found");
        }
        for (int i = pos; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == ' ' || c == ' ' || c == '\u0085') {
                String line = text.substring(pos, i);
                pos = (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') ? i + 2 : i + 1;
                return line;
            }
        }
        String line = text.substring(pos);
        pos = text.length();
        return line;
    }

    @Override
    public void close() {
    }
}
