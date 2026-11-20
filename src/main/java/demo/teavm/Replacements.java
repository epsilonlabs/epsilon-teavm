package demo.teavm;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.Permission;
import java.util.Locale;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;

import org.eclipse.epsilon.flexmi.xml.FlexmiXmlParser;
import org.osgi.framework.Bundle;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Replacements for library and JDK methods, called in their place by {@link BrowserCompatPlugin}.
 * A replacement for an instance method takes the receiver as its first parameter.
 */
public final class Replacements {
    private Replacements() {
    }

    // ---- Flexmi ----

    /** FlexmiXmlParser.parse(InputStream). */
    public static Document parseFlexmiXml(FlexmiXmlParser parser, InputStream inputStream) throws Exception {
        return DomBuilder.parse(new InputSource(inputStream));
    }

    /** Xml.parse(String), used for Flexmi templates. */
    public static Document parseXml(String xml) throws Exception {
        // As in Xml.parse: the platform's default charset
        return DomBuilder.parse(new InputSource(new ByteArrayInputStream(xml.getBytes(Charset.defaultCharset()))));
    }

    // ---- JAXP: use Xerces, instead of looking up an implementation by class name ----

    /** SAXParserFactory.newInstance(). */
    public static SAXParserFactory newSAXParserFactory() {
        return new org.apache.xerces.jaxp.SAXParserFactoryImpl();
    }

    /** DocumentBuilderFactory.newInstance(). */
    public static DocumentBuilderFactory newDocumentBuilderFactory() {
        return new org.apache.xerces.jaxp.DocumentBuilderFactoryImpl();
    }

    /** DatatypeFactory.newInstance(). */
    public static DatatypeFactory newDatatypeFactory() {
        return new org.apache.xerces.jaxp.datatype.DatatypeFactoryImpl();
    }

    /**
     * Xerces' ObjectFactory.createObject(factoryId, [propertiesFilename,] fallbackClassName) and
     * newInstance(className, classLoader, doFallback): creates the component Xerces asks for by name.
     */
    public static Object newXercesObject(String factoryId, String fallbackClassName) {
        return newXercesObject(fallbackClassName);
    }

    public static Object newXercesObject(String factoryId, String propertiesFilename, String fallbackClassName) {
        return newXercesObject(fallbackClassName);
    }

    public static Object newXercesObject(String className, ClassLoader classLoader, boolean doFallback) {
        return newXercesObject(className);
    }

    private static Object newXercesObject(String className) {
        switch (className) {
            case "org.apache.xerces.parsers.XIncludeAwareParserConfiguration":
                return new org.apache.xerces.parsers.XIncludeAwareParserConfiguration();
            case "org.apache.xerces.impl.dv.dtd.DTDDVFactoryImpl":
                return new org.apache.xerces.impl.dv.dtd.DTDDVFactoryImpl();
            default:
                throw new IllegalStateException("Creating Xerces' " + className + " is not supported in the browser");
        }
    }

    // ---- Eclipse platform: not running ----

    /** Platform.isRunning(). */
    public static boolean isRunning() {
        return false;
    }

    /** Platform.getBundle(String). */
    public static Bundle getBundle(String symbolicName) {
        return null;
    }

    // ---- JDK methods missing from TeaVM's class library ----

    /** ClassLoader.loadClass(String): TeaVM cannot load classes by name. */
    public static Class<?> loadClass(ClassLoader loader, String name) throws ClassNotFoundException {
        throw new ClassNotFoundException(name);
    }

    /** Class.getResource(String): there are no class path resources. */
    public static URL getResource(Class<?> cls, String name) {
        return null;
    }

    /** Locale.getScript(). */
    public static String getScript(Locale locale) {
        return "";
    }

    /** SecurityManager.checkPermission(Permission): there is no security manager. */
    public static void checkPermission(SecurityManager manager, Permission permission) {
    }

    /** SecurityManager.checkPackageAccess(String). */
    public static void checkPackageAccess(SecurityManager manager, String pkg) {
    }
}
