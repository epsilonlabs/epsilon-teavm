package demo.teavm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Spliterator;
import java.util.Spliterators;

/**
 * Replacements for JDK methods that TeaVM's class library lacks, for classes it does
 * provide (so they cannot be added as org.teavm.classlib emulations).
 * {@link BrowserCompatPlugin} redirects calls to the JDK methods here at compile time.
 */
public final class JdkCompat {
    private JdkCompat() {
    }

    /** java.util.Spliterators.emptySpliterator() (used by Guava). */
    public static <T> Spliterator<T> emptySpliterator() {
        return Spliterators.spliterator(new Object[0], 0);
    }

    /**
     * java.net.URL.openStream(), adding the file: protocol, which TeaVM's URL lacks.
     * Epsilon reads programs and templates through file: URLs.
     */
    public static InputStream openStream(URL url) throws IOException {
        if ("file".equals(url.getProtocol())) {
            return new FileInputStream(new File(URLDecoder.decode(url.getPath(), "UTF-8")));
        }
        return url.openStream();
    }

    /**
     * java.lang.reflect.Method.invoke(). TeaVM 0.16 lets exceptions thrown by the invoked method
     * escape as they are, while the JDK wraps them in an InvocationTargetException. Epsilon relies
     * on the wrapping to report errors at the right place in the program. (Exceptions of the
     * types invoke() itself declares are passed through, as TeaVM may throw them while checking
     * arguments; Epsilon handles them in the same way either way.)
     */
    public static Object invoke(Method method, Object target, Object... args)
            throws IllegalAccessException, InvocationTargetException {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw e;
        } catch (Throwable t) {
            throw new InvocationTargetException(t);
        }
    }

    /** java.lang.reflect.Constructor.newInstance(), wrapping exceptions as {@link #invoke} does. */
    public static <T> T newInstance(Constructor<T> constructor, Object... args)
            throws InstantiationException, IllegalAccessException, InvocationTargetException {
        try {
            return constructor.newInstance(args);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw e;
        } catch (Throwable t) {
            throw new InvocationTargetException(t);
        }
    }

    /** java.io.File.toPath(), for the default file system. */
    public static Path toPath(File file) {
        return Paths.get(file.getPath());
    }
}
