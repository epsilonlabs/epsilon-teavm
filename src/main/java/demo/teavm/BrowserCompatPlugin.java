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
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

/**
 * TeaVM compiler plugin that adapts libraries to the browser without changing their source.
 * <ul>
 * <li>{@link #METHODS}: replaces the bodies of library methods. Methods that need what a browser
 * cannot provide (native processes, the Eclipse workspace) throw UnsupportedOperationException
 * or return nothing, and others call a replacement in {@link Replacements}. The code the
 * original bodies used is then never compiled.</li>
 * <li>{@link #CALLS}: redirects calls to JDK methods that TeaVM's class library lacks to
 * {@link Replacements}. JDK methods cannot be replaced like library methods, as their
 * implementations come from TeaVM.</li>
 * </ul>
 */
public class BrowserCompatPlugin implements TeaVMPlugin {

    private static final String RETURN_NULL = "<return null>";
    private static final String RETURN_EMPTY_MAP = "<return empty map>";
    private static final String DO_NOTHING = "<do nothing>";
    /** Prefix of an action that calls the named static method of {@link Replacements}. */
    private static final String CALL = "call ";

    /**
     * {class, method, action}. The method is a name (all overloads) or a name and descriptor such
     * as {@code parse(Ljava/lang/String;)Lorg/w3c/dom/Document;}. The action is one of the
     * constants above, or the name of the unavailable feature, for a method that throws.
     * A replacement method takes the receiver (for instance methods) and then the original parameters.
     */
    private static final String[][] METHODS = {
        // EOL features that need native processes or XML
        {"org.eclipse.epsilon.eol.execute.operations.contributors.StringOperationContributor", "escapeXml", "escapeXml()"},
        {"org.eclipse.epsilon.eol.tools.EolSystem", "executeNative", "Running native processes"},
        {"org.eclipse.epsilon.eol.tools.EolSystem", "executeNativeAsync", "Running native processes"},
        {"org.eclipse.epsilon.common.util.OperatingSystem", "executeCommand", "Running native processes"},

        // Flexmi builds its DOM with an identity XSLT transformation; build it from SAX events instead
        {"org.eclipse.epsilon.flexmi.xml.FlexmiXmlParser", "parse(Ljava/io/InputStream;)Lorg/w3c/dom/Document;", CALL + "parseFlexmiXml"},
        {"org.eclipse.epsilon.flexmi.xml.Xml", "parse(Ljava/lang/String;)Lorg/w3c/dom/Document;", CALL + "parseXml"},
        {"org.eclipse.epsilon.flexmi.xml.Xml", "<clinit>", DO_NOTHING},
        {"org.eclipse.epsilon.flexmi.yaml.FlexmiYamlParser", "toXml", "XSLT"},
        {"org.eclipse.epsilon.flexmi.yaml.FlexmiYamlParser", "main", "FlexmiYamlParser.main"},

        // JAXP looks implementations up by class name; use Xerces directly
        {"javax.xml.parsers.SAXParserFactory", "newInstance()Ljavax/xml/parsers/SAXParserFactory;", CALL + "newSAXParserFactory"},
        {"javax.xml.parsers.DocumentBuilderFactory", "newInstance()Ljavax/xml/parsers/DocumentBuilderFactory;", CALL + "newDocumentBuilderFactory"},
        {"javax.xml.datatype.DatatypeFactory", "newInstance()Ljavax/xml/datatype/DatatypeFactory;", CALL + "newDatatypeFactory"},
        // Xerces does the same for its own components, with an ObjectFactory in each of these packages
        {"org.apache.xerces.parsers.ObjectFactory", "createObject", CALL + "newXercesObject"},
        {"org.apache.xerces.parsers.ObjectFactory", "newInstance", CALL + "newXercesObject"},
        {"org.apache.xerces.impl.dv.ObjectFactory", "createObject", CALL + "newXercesObject"},
        {"org.apache.xerces.impl.dv.ObjectFactory", "newInstance", CALL + "newXercesObject"},
        {"org.apache.xerces.xinclude.ObjectFactory", "createObject", CALL + "newXercesObject"},
        {"org.apache.xerces.xinclude.ObjectFactory", "newInstance", CALL + "newXercesObject"},
        {"org.apache.xerces.dom.ObjectFactory", "createObject", CALL + "newXercesObject"},
        {"org.apache.xerces.dom.ObjectFactory", "newInstance", CALL + "newXercesObject"},

        // Eclipse platform and workspace integration in EMF and Emfatic
        {"org.eclipse.emf.ecore.plugin.EcorePlugin", "computePlatformPluginToPlatformResourceMap", RETURN_EMPTY_MAP},
        {"org.eclipse.emf.ecore.plugin.EcorePlugin", "computePlatformResourceToPlatformPluginMap", RETURN_EMPTY_MAP},
        {"org.eclipse.emf.ecore.plugin.EcorePlugin", "getWorkspaceRoot", RETURN_NULL},
        {"org.eclipse.emf.ecore.plugin.EcorePlugin$PDEHelper", "*", "The Eclipse workspace"},
        {"org.eclipse.emf.ecore.resource.impl.PlatformResourceURIHandlerImpl", "getWorkspaceRoot", RETURN_NULL},
        {"org.eclipse.emf.ecore.resource.impl.PlatformResourceURIHandlerImpl$WorkbenchHelper", "*", "The Eclipse workspace"},
        {"org.eclipse.emf.ecore.resource.impl.PlatformResourceURIHandlerImpl$PlatformResourceOutputStream", "*", "The Eclipse workspace"},
    };

    /** {class, method}: calls are redirected to the method of the same name in {@link Replacements}. */
    private static final String[][] CALLS = {
        // Eclipse's Platform class is not on the classpath; EMF and Emfatic check it before using it
        {"org.eclipse.core.runtime.Platform", "isRunning"},
        {"org.eclipse.core.runtime.Platform", "getBundle"},
        {"java.lang.ClassLoader", "loadClass"},
        {"java.lang.Class", "getResource"},
        {"java.util.Locale", "getScript"},
        {"java.lang.SecurityManager", "checkPermission"},
        {"java.lang.SecurityManager", "checkPackageAccess"},
    };

    /** Whether a method's body is replaced; such methods are also left out of reflection metadata. */
    static boolean isReplaced(String className, String methodName) {
        for (String[] m : METHODS) {
            if (m[0].equals(className) && (m[1].equals("*") || m[1].equals(methodName) || m[1].startsWith(methodName + "("))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void install(TeaVMHost host) {
        host.add(new ClassHolderTransformer() {
            @Override
            public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
                if (cls.getName().equals(Replacements.class.getName())) {
                    return;
                }
                for (MethodHolder method : cls.getMethods()) {
                    String action = action(cls.getName(), method);
                    if (action != null) {
                        replaceBody(method, action, context);
                    } else if (method.getProgram() != null) {
                        for (BasicBlock block : method.getProgram().getBasicBlocks()) {
                            for (Instruction insn : block) {
                                if (insn instanceof InvokeInstruction) {
                                    redirect((InvokeInstruction) insn);
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    private static String action(String className, MethodHolder method) {
        String descriptor = method.getDescriptor().toString();
        for (String[] m : METHODS) {
            if (m[0].equals(className) && (m[1].equals("*") || m[1].equals(method.getName()) || m[1].equals(descriptor))) {
                return m[2];
            }
        }
        return null;
    }

    private static void replaceBody(MethodHolder method, String action, ClassHolderTransformerContext context) {
        if (method.getProgram() == null && !method.hasModifier(ElementModifier.NATIVE)) {
            return; // abstract
        }
        method.getModifiers().remove(ElementModifier.NATIVE);
        ProgramEmitter pe = ProgramEmitter.create(method, context.getHierarchy());
        ValueType resultType = method.getResultType();
        if (action == DO_NOTHING) {
            pe.exit();
        } else if (action == RETURN_NULL) {
            pe.constantNull(resultType).returnValue();
        } else if (action == RETURN_EMPTY_MAP) {
            pe.construct(java.util.HashMap.class).returnValue();
        } else if (action.startsWith(CALL)) {
            boolean isStatic = method.hasModifier(ElementModifier.STATIC);
            List<ValueType> signature = new ArrayList<>();
            List<ValueEmitter> args = new ArrayList<>();
            if (!isStatic) {
                signature.add(ValueType.object(method.getOwnerName()));
                args.add(pe.var(0, ValueType.object(method.getOwnerName())));
            }
            for (int i = 0; i < method.parameterCount(); i++) {
                signature.add(method.parameterType(i));
                args.add(pe.var(i + 1, method.parameterType(i)));
            }
            signature.add(resultType);
            MethodReference target = new MethodReference(Replacements.class.getName(),
                    action.substring(CALL.length()), signature.toArray(new ValueType[0]));
            ValueEmitter result = pe.invoke(target, args.toArray(new ValueEmitter[0]));
            if (resultType == ValueType.VOID) {
                pe.exit();
            } else {
                result.returnValue();
            }
        } else {
            pe.construct(UnsupportedOperationException.class,
                    pe.constant(action + " is not available in the browser")).raise();
        }
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
        invoke.setMethod(new MethodReference(Replacements.class.getName(), target.getName(),
                signature.toArray(new ValueType[0])));
        invoke.setType(InvocationType.SPECIAL);
        invoke.setInstance(null);
        invoke.setArguments(arguments.toArray(new Variable[0]));
    }

    private static boolean isRedirected(MethodReference ref) {
        String cls = jdkName(ref.getClassName());
        for (String[] c : CALLS) {
            if (c[0].equals(cls) && c[1].equals(ref.getName())) {
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
