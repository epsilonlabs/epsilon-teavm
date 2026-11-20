package demo.teavm;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.teavm.extension.introspect.IntrospectClass;
import org.teavm.extension.introspect.IntrospectField;
import org.teavm.extension.introspect.IntrospectMember;
import org.teavm.extension.introspect.IntrospectMethod;
import org.teavm.extension.spi.reflection.ReflectionPolicy;

/**
 * Compile-time policy telling TeaVM which classes need runtime reflection metadata.
 * Epsilon programs can call any public Java method on the objects they handle
 * (e.g. "abc".toUpperCase(), x.println(), element.eClass()), which EOL resolves reflectively,
 * both on the target objects and on Epsilon's operation contributors. The parser's entry
 * rule is also invoked reflectively.
 *
 * <p>Everything made reflectable is compiled. Members that need APIs a browser cannot
 * provide are stubbed by {@link BrowserCompatPlugin}.
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
            // Native methods are TeaVM intrinsics without a callable implementation.
            // Constructors are only needed for JDK types (new Native("java.util.ArrayList")); exposing
            // Epsilon's would make TeaVM assume that any Epsilon class can be instantiated.
            // Stubbed methods can mention types that are no longer compiled.
            if (Modifier.isPublic(m.modifiers()) && !Modifier.isNative(m.modifiers())
                    && !(epsilon && m.isConstructor())
                    && !BrowserCompatPlugin.isReplaced(cls.name(), m.name())) {
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

    private static boolean isReflectable(String name) {
        return name.startsWith("org.eclipse.epsilon.")
                || inPackage(name, "org.eclipse.emf.ecore") // e.g. eClass(), eContainer() on model elements
                || inPackage(name, "org.eclipse.emf.ecore.impl")
                || inPackage(name, "org.eclipse.emf.common.util")
                || inPackage(name, "java.lang")
                || inPackage(name, "java.util")
                || inPackage(name, "java.lang.ref") // EMF looks up Reference.refersTo() reflectively
                || inPackage(name, "java.math");
    }

    private static boolean inPackage(String className, String pkg) {
        return className.startsWith(pkg + ".") && className.indexOf('.', pkg.length() + 1) < 0;
    }
}
