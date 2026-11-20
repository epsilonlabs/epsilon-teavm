package demo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.emfatic.core.generator.ecore.Builder;
import org.eclipse.emf.emfatic.core.generator.ecore.Connector;
import org.eclipse.emf.emfatic.core.lang.gen.parser.EmfaticParserDriver;
import org.eclipse.epsilon.common.parse.problem.ParseProblem;
import org.eclipse.epsilon.emc.emf.InMemoryEmfModel;
import org.eclipse.epsilon.eol.EolModule;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.eclipse.epsilon.flexmi.FlexmiParseException;
import org.eclipse.epsilon.flexmi.FlexmiResource;
import org.eclipse.gymnast.runtime.core.parser.ParseContext;
import org.eclipse.gymnast.runtime.core.parser.ParseMessage;
import org.teavm.jso.JSExport;

/**
 * Browser entry point: compiled to JavaScript by TeaVM. The page calls the exported
 * {@link #runEol(String, String, String)} function.
 */
public class Main {

    public static void main(String[] args) {
        // Nothing to do on load; the page drives execution through runEol().
    }

    /**
     * Parses and executes an EOL program, optionally against a model.
     *
     * @param program the EOL program
     * @param emfatic the model's metamodel in Emfatic, or "" (for no model, or a model of Ecore itself)
     * @param flexmi the model in Flexmi (XML or YAML syntax), conforming to the metamodel ("" for an empty model)
     * @return a 3-element array: [stdout, stderr, string value of the program's return statement or ""]
     */
    @JSExport
    public static String[] runEol(String program, String emfatic, String flexmi) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream outStream = new PrintStream(out, true);
        PrintStream errStream = new PrintStream(err, true);
        String result = "";

        EolModule module = new EolModule();
        module.getContext().setOutputStream(outStream);
        module.getContext().setErrorStream(errStream);
        try {
            if (!module.parse(program)) {
                for (ParseProblem problem : module.getParseProblems()) {
                    errStream.println("Parse error: " + problem);
                }
            } else if (emfatic.trim().isEmpty() && flexmi.trim().isEmpty() || loadModel(module, emfatic, flexmi, errStream)) {
                Object value = module.execute();
                if (value != null) {
                    result = module.getContext().getPrettyPrinterManager().toString(value);
                }
            }
        } catch (EolRuntimeException e) {
            errStream.println(e.getMessage());
        } catch (Throwable t) {
            errStream.println("Internal error: " + t);
        } finally {
            module.getContext().dispose();
        }
        outStream.flush();
        errStream.flush();
        return new String[] {
            new String(out.toByteArray(), StandardCharsets.UTF_8),
            new String(err.toByteArray(), StandardCharsets.UTF_8),
            result
        };
    }

    /**
     * Builds the metamodel from Emfatic, loads the Flexmi model against it and adds it to the
     * module as model "M". Reports problems to err and returns false if there were any.
     */
    private static boolean loadModel(EolModule module, String emfatic, String flexmi, PrintStream err) throws Exception {
        List<EPackage> metamodel = emfatic.trim().isEmpty() ? new ArrayList<>() : parseEmfatic(emfatic, err);
        if (metamodel == null) {
            return false;
        }

        // A fresh resource set per run, so that metamodels from earlier runs are not visible
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
        for (EPackage ePackage : metamodel) {
            registerPackages(ePackage, resourceSet);
        }
        // An absolute URI: Flexmi passes it to java.io.File when it runs EOL and EGL templates
        Resource model = new FlexmiResource(URI.createFileURI("/model.flexmi"));
        resourceSet.getResources().add(model);
        if (!flexmi.trim().isEmpty()) {
            try {
                model.load(new ByteArrayInputStream(flexmi.getBytes(StandardCharsets.UTF_8)), null);
            } catch (FlexmiParseException e) {
                Throwable cause = e;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                err.println("Flexmi error (line " + e.getLineNumber() + "): " + cause.getMessage());
                return false;
            }
        }

        boolean ok = true;
        for (Resource.Diagnostic d : model.getErrors()) {
            err.println("Flexmi error (line " + d.getLine() + "): " + d.getMessage());
            ok = false;
        }
        for (Resource.Diagnostic d : model.getWarnings()) {
            err.println("Flexmi warning (line " + d.getLine() + "): " + d.getMessage());
        }
        if (ok) {
            module.getContext().getModelRepository().addModel(new InMemoryEmfModel("M", model, metamodel));
        }
        return ok;
    }

    /** Builds Ecore packages from Emfatic source, or reports the problems and returns null. */
    private static List<EPackage> parseEmfatic(String emfatic, PrintStream err) throws Exception {
        ParseContext parseContext = new EmfaticParserDriver(URI.createURI("metamodel.emf"))
                .parse(new StringReader(emfatic));
        Resource resource = new ResourceImpl(URI.createURI("metamodel.ecore"));
        NullProgressMonitor monitor = new NullProgressMonitor();
        Builder builder = new Builder();
        builder.build(parseContext, resource, monitor);
        if (!parseContext.hasErrors()) {
            new Connector(builder).connect(parseContext, resource, monitor);
        }
        if (parseContext.hasErrors()) {
            for (ParseMessage message : parseContext.getMessages()) {
                err.println("Emfatic error (line " + line(emfatic, message.getOffset()) + "): " + message.getMessage());
            }
            return null;
        }
        List<EPackage> packages = new ArrayList<>();
        for (EObject root : resource.getContents()) {
            packages.add((EPackage) root);
        }
        return packages;
    }

    private static void registerPackages(EPackage ePackage, ResourceSet resourceSet) {
        resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
        for (EPackage sub : ePackage.getESubpackages()) {
            registerPackages(sub, resourceSet);
        }
    }

    private static int line(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
