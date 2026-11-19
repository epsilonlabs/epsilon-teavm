package demo.teavm;

import java.util.ArrayList;
import java.util.List;

import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.Instruction;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

/**
 * TeaVM compiler plugin that adapts libraries to the browser without changing their source:
 * <ul>
 * <li>calls to JDK methods missing from TeaVM's class library are redirected to static
 * replacements in {@link JdkCompat} (instance methods take the receiver as first argument);</li>
 * <li>methods that implement features a browser cannot support (HTTP, XML, processes) are
 * replaced with a body that throws UnsupportedOperationException, so the APIs they use
 * are never compiled.</li>
 * </ul>
 */
public class BrowserCompatPlugin implements TeaVMPlugin {

    /** {JDK class, method}: calls are redirected to the method of the same name in {@link JdkCompat}. */
    private static final String[][] REDIRECTS = {
        // Missing from TeaVM's class library
        {"java.util.Spliterators", "emptySpliterator"},
        {"java.io.File", "toPath"},
        // Present, but different from the JDK
        {"java.net.URL", "openStream"},
        {"java.lang.reflect.Method", "invoke"},
        {"java.lang.reflect.Constructor", "newInstance"},
    };

    /** {class name, method name, unavailable feature}: every overload of the method is stubbed. */
    private static final String[][] STUBS = {
        {"org.eclipse.epsilon.emc.json.JsonModel", "loadModelViaHTTP", "Loading JSON models over HTTP"},
        {"org.eclipse.epsilon.internal.eunit.xml.EUnitXMLFormatter", "generate", "Writing JUnit-style XML reports"},
        {"org.eclipse.epsilon.egl.config.DefaultContentTypeRepository", "load", "Loading EGL content types from XML"},
        {"org.eclipse.epsilon.eol.execute.operations.contributors.StringOperationContributor", "escapeXml", "escapeXml()"},
        {"org.eclipse.epsilon.eol.tools.EolSystem", "executeNative", "Running native processes"},
        {"org.eclipse.epsilon.eol.tools.EolSystem", "executeNativeAsync", "Running native processes"},
        {"org.eclipse.epsilon.common.util.OperatingSystem", "executeCommand", "Running native processes"},
        {"org.eclipse.epsilon.emg.random.EmgRandomGenerator", "nextURI", "EMG's nextURI() (needs java.security.MessageDigest)"},
    };

    /** Whether a method is replaced by a stub; such methods are also left out of reflection metadata. */
    static boolean isStubbed(String className, String methodName) {
        return Transformer.stubbedFeature(className, methodName) != null;
    }

    @Override
    public void install(TeaVMHost host) {
        host.add(new Transformer());
    }

    static class Transformer implements ClassHolderTransformer {
        @Override
        public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
            if (cls.getName().equals(JdkCompat.class.getName())) {
                return;
            }
            for (MethodHolder method : cls.getMethods()) {
                String feature = stubbedFeature(cls.getName(), method.getName());
                if (feature != null && method.getProgram() != null) {
                    stub(method, feature, context);
                    continue;
                }
                Program program = method.getProgram();
                if (program == null) {
                    continue;
                }
                for (BasicBlock block : program.getBasicBlocks()) {
                    for (Instruction insn : block) {
                        if (insn instanceof InvokeInstruction) {
                            redirect((InvokeInstruction) insn);
                        }
                    }
                }
            }
        }

        private static String stubbedFeature(String className, String methodName) {
            for (String[] s : STUBS) {
                if (s[0].equals(className) && s[1].equals(methodName)) {
                    return s[2];
                }
            }
            return null;
        }

        private static void stub(MethodHolder method, String feature, ClassHolderTransformerContext context) {
            method.getModifiers().remove(ElementModifier.NATIVE);
            ProgramEmitter pe = ProgramEmitter.create(method, context.getHierarchy());
            pe.construct(UnsupportedOperationException.class,
                    pe.constant(feature + " is not available in the browser")).raise();
        }

        private static void redirect(InvokeInstruction invoke) {
            MethodReference target = invoke.getMethod();
            if (!isRedirected(target)) {
                return;
            }
            List<ValueType> signature = new ArrayList<>();
            List<Variable> arguments = new ArrayList<>();
            if (invoke.getInstance() != null) {
                signature.add(ValueType.object(target.getClassName()));
                arguments.add(invoke.getInstance());
            }
            for (int i = 0; i < target.parameterCount(); i++) {
                signature.add(target.parameterType(i));
            }
            arguments.addAll(invoke.getArguments());
            signature.add(target.getReturnType());
            invoke.setMethod(new MethodReference(JdkCompat.class.getName(), target.getName(),
                    signature.toArray(new ValueType[0])));
            invoke.setType(InvocationType.SPECIAL);
            invoke.setInstance(null);
            invoke.setArguments(arguments.toArray(new Variable[0]));
        }

        private static boolean isRedirected(MethodReference ref) {
            String cls = jdkName(ref.getClassName());
            for (String[] r : REDIRECTS) {
                if (r[0].equals(cls) && r[1].equals(ref.getName())) {
                    return true;
                }
            }
            return false;
        }

        /** Undoes TeaVM's mapping of java.x.Y to org.teavm.classlib.java.x.TY. */
        private static String jdkName(String cls) {
            String prefix = "org.teavm.classlib.";
            if (!cls.startsWith(prefix)) {
                return cls;
            }
            int dot = cls.lastIndexOf('.');
            String simple = cls.substring(dot + 1);
            return cls.substring(prefix.length(), dot + 1) + (simple.startsWith("T") ? simple.substring(1) : simple);
        }
    }
}
