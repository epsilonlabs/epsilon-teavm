package org.teavm.classlib.java.net;

import java.net.UnknownHostException;

/**
 * Minimal java.net.InetAddress so that Gson's built-in type adapters compile.
 * The browser cannot resolve host names.
 */
public class TInetAddress {
    private final String hostAddress;

    private TInetAddress(String hostAddress) {
        this.hostAddress = hostAddress;
    }

    public static TInetAddress getByName(String host) throws UnknownHostException {
        throw new UnknownHostException("Host name resolution is not available in the browser: " + host);
    }

    public String getHostAddress() {
        return hostAddress;
    }

    @Override
    public String toString() {
        return "/" + hostAddress;
    }
}
