package demo.teavm;

import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodHolder;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.vm.spi.TeaVMPlugin;

/**
 * TeaVM compiler plugin that replaces the bodies of EOL methods needing APIs a browser
 * cannot provide (native processes, XML) with code that throws UnsupportedOperationException.
 * The APIs they use are then never compiled.
 */
public class BrowserCompatPlugin implements TeaVMPlugin {

    /** {class name, method name, unavailable feature}: every overload of the method is stubbed. */
    private static final String[][] STUBS = {
        {"org.eclipse.epsilon.eol.execute.operations.contributors.StringOperationContributor", "escapeXml", "escapeXml()"},
        {"org.eclipse.epsilon.eol.tools.EolSystem", "executeNative", "Running native processes"},
        {"org.eclipse.epsilon.eol.tools.EolSystem", "executeNativeAsync", "Running native processes"},
        {"org.eclipse.epsilon.common.util.OperatingSystem", "executeCommand", "Running native processes"},
    };

    /** Whether a method is stubbed; such methods are also left out of reflection metadata. */
    static boolean isStubbed(String className, String methodName) {
        return stubbedFeature(className, methodName) != null;
    }

    private static String stubbedFeature(String className, String methodName) {
        for (String[] stub : STUBS) {
            if (stub[0].equals(className) && stub[1].equals(methodName)) {
                return stub[2];
            }
        }
        return null;
    }

    @Override
    public void install(org.teavm.vm.spi.TeaVMHost host) {
        host.add(new ClassHolderTransformer() {
            @Override
            public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
                for (MethodHolder method : cls.getMethods()) {
                    String feature = stubbedFeature(cls.getName(), method.getName());
                    if (feature != null && method.getProgram() != null) {
                        method.getModifiers().remove(ElementModifier.NATIVE);
                        ProgramEmitter pe = ProgramEmitter.create(method, context.getHierarchy());
                        pe.construct(UnsupportedOperationException.class,
                                pe.constant(feature + " is not available in the browser")).raise();
                    }
                }
            }
        });
    }
}
