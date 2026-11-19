package demo.teavm;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.teavm.extension.introspect.IntrospectClass;
import org.teavm.extension.introspect.IntrospectField;
import org.teavm.extension.introspect.IntrospectMember;
import org.teavm.extension.introspect.IntrospectMethod;
import org.teavm.extension.spi.reflection.ReflectionPolicy;

/**
 * Compile-time policy telling TeaVM which classes need runtime reflection metadata.
 * EOL resolves operation calls (e.g. "abc".toUpperCase(), x.println()) by looking up
 * public Java methods reflectively, both on the target objects and on Epsilon's
 * operation contributors. The parser's entry rule is also invoked reflectively.
 */
public class EpsilonReflectionPolicy implements ReflectionPolicy {

    @Override
    public Collection<IntrospectMember> classAccessibleMembers(IntrospectClass<?> cls) {
        if (cls == null || cls.name() == null || !isReflectable(cls.name())) {
            return Collections.emptyList();
        }
        List<IntrospectMember> members = new ArrayList<>();
        boolean epsilon = cls.name().startsWith("org.eclipse.epsilon.");
        for (IntrospectMethod m : cls.declaredMethods()) {
            if (Modifier.isPublic(m.modifiers()) && !Modifier.isNative(m.modifiers()) && !(epsilon && EXCLUDED_EPSILON_METHODS.contains(m.name()))) {
                members.add(m);
            }
        }
        for (IntrospectField f : cls.declaredFields()) {
            if (Modifier.isPublic(f.modifiers())) {
                members.add(f);
            }
        }
        return members;
    }

    @Override
    public boolean isClassFoundByName(IntrospectClass<?> cls) {
        return false;
    }

    /**
     * Epsilon operations whose implementations need JDK APIs that don't exist in the
     * browser (XML parsers, parallel streams). Keeping them out of reflection metadata
     * keeps TeaVM from trying to compile them.
     */
    private static final Set<String> EXCLUDED_EPSILON_METHODS = Set.of("escapeXml", "parallelStream");

    private static boolean isReflectable(String name) {
        // eol.tools contains System/native-process helpers that cannot work in a browser
        return (name.startsWith("org.eclipse.epsilon.") && !name.startsWith("org.eclipse.epsilon.eol.tools."))
                || inPackage(name, "java.lang")
                || inPackage(name, "java.util")
                || inPackage(name, "java.math");
    }

    private static boolean inPackage(String className, String pkg) {
        return className.startsWith(pkg + ".") && className.indexOf('.', pkg.length() + 1) < 0;
    }
}
