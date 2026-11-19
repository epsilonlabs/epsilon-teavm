package demo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.epsilon.common.parse.problem.ParseProblem;
import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.ecl.EclModule;
import org.eclipse.epsilon.ecl.trace.Match;
import org.eclipse.epsilon.egl.EglModule;
import org.eclipse.epsilon.egl.EgxModule;
import org.eclipse.epsilon.emc.csv.CsvModel;
import org.eclipse.epsilon.emc.json.JsonModel;
import org.eclipse.epsilon.emg.EmgModule;
import org.eclipse.epsilon.eml.EmlModule;
import org.eclipse.epsilon.eol.EolModule;
import org.eclipse.epsilon.eol.IEolModule;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.eclipse.epsilon.eol.models.IModel;
import org.eclipse.epsilon.eol.models.Model;
import org.eclipse.epsilon.epl.EplModule;
import org.eclipse.epsilon.epl.execute.model.PatternMatchModel;
import org.eclipse.epsilon.etl.EtlModule;
import org.eclipse.epsilon.eunit.EUnitModule;
import org.eclipse.epsilon.eunit.EUnitTest;
import org.eclipse.epsilon.evl.EvlModule;
import org.eclipse.epsilon.evl.execute.UnsatisfiedConstraint;
import org.eclipse.epsilon.ewl.EwlModule;
import org.eclipse.epsilon.ewl.execute.WizardInstance;
import org.eclipse.epsilon.flock.FlockModule;
import org.eclipse.epsilon.pinset.PinsetModule;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Runs a program in any Epsilon language against JSON/CSV models. Models are written to
 * the (in-memory, under TeaVM) file system and loaded through the regular drivers, so the
 * same code runs unchanged on the JVM.
 */
public class EpsilonRunner {

    /** One model, as described by the page: {name, kind: json|csv, content, output?}. */
    public static class ModelSpec {
        public String name;
        public String kind;
        public String content;
        public boolean output;
    }

    /**
     * Request: {language, program, models: [...], comparison?: ECL program used by EML,
     * files?: {relative path: content} such as EGL templates used by EGX}.
     */
    public static class Request {
        public String language;
        public String program;
        public List<ModelSpec> models = new ArrayList<>();
        public String comparison;
        public Map<String, String> files = new LinkedHashMap<>();
    }

    /** Work directory: TeaVM's in-memory file system in the browser; set epsilon.work to run on the JVM. */
    private static final Path WORK = Paths.get(System.getProperty("epsilon.work", "/epsilon"));

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private final PrintStream outStream = new PrintStream(out, true);
    private final PrintStream errStream = new PrintStream(err, true);

    /**
     * @return [stdout, stderr, returned value, JSON object mapping output file paths to contents]
     */
    public static String[] run(String requestJson) {
        // Gson's tree API, not data binding, so that no reflection metadata is needed
        JsonObject json = JsonParser.parseString(requestJson).getAsJsonObject();
        Request request = new Request();
        request.language = json.get("language").getAsString();
        request.program = json.get("program").getAsString();
        request.comparison = json.has("comparison") ? json.get("comparison").getAsString() : null;
        if (json.has("models")) {
            for (JsonElement m : json.getAsJsonArray("models")) {
                JsonObject o = m.getAsJsonObject();
                ModelSpec spec = new ModelSpec();
                spec.name = o.get("name").getAsString();
                spec.kind = o.get("kind").getAsString();
                spec.content = o.has("content") ? o.get("content").getAsString() : "";
                spec.output = o.has("output") && o.get("output").getAsBoolean();
                request.models.add(spec);
            }
        }
        if (json.has("files")) {
            for (Map.Entry<String, JsonElement> f : json.getAsJsonObject("files").entrySet()) {
                request.files.put(f.getKey(), f.getValue().getAsString());
            }
        }
        return new EpsilonRunner().execute(request);
    }

    private String[] execute(Request request) {
        String result = "";
        Map<String, String> files = new LinkedHashMap<>();
        List<IModel> models = new ArrayList<>();
        IEolModule module = null;
        try {
            // Epsilon's FileUtil creates <java.io.tmpdir>/epsilon when it is first used
            mkdirs(Paths.get(System.getProperty("java.io.tmpdir", "/tmp")));
            deleteRecursively(WORK);
            mkdirs(WORK.resolve("models"));
            mkdirs(WORK.resolve("out"));
            mkdirs(WORK.resolve("src"));
            for (Map.Entry<String, String> f : request.files.entrySet()) {
                write(WORK.resolve("src").resolve(f.getKey()), f.getValue());
            }

            for (ModelSpec spec : request.models) {
                models.add(loadModel(spec));
            }

            module = createModule(request.language);
            module.getContext().setOutputStream(outStream);
            module.getContext().setErrorStream(errStream);
            if (!parse(module, "program." + request.language, request.program)) {
                return finish(result, files);
            }
            module.getContext().getModelRepository().addModels(models);

            if (module instanceof EmlModule) {
                EclModule ecl = new EclModule();
                ecl.getContext().setOutputStream(outStream);
                ecl.getContext().setErrorStream(errStream);
                if (!parse(ecl, "comparison.ecl", request.comparison == null ? "" : request.comparison)) {
                    return finish(result, files);
                }
                ecl.getContext().getModelRepository().addModels(models);
                ecl.execute();
                ((EmlModule) module).getContext().setMatchTrace(ecl.getContext().getMatchTrace().getReduced());
            }

            Object value = module instanceof FlockModule && models.size() >= 2
                    ? ((FlockModule) module).execute(models.get(0), models.get(1))
                    : module.execute();
            result = report(module, value);

            for (int i = 0; i < models.size(); i++) {
                if (request.models.get(i).output) {
                    Path target = WORK.resolve("out").resolve(request.models.get(i).name + "." + request.models.get(i).kind);
                    models.get(i).store(target.toString());
                }
            }
            collectFiles(WORK.resolve("out"), files);
        } catch (EolRuntimeException e) {
            errStream.println(e.getMessage());
        } catch (Throwable t) {
            errStream.println("Internal error: " + t);
        } finally {
            if (module != null) {
                module.getContext().getModelRepository().getModels().clear();
                module.getContext().dispose();
            }
        }
        return finish(result, files);
    }

    private IEolModule createModule(String language) throws Exception {
        switch (language) {
            case "eol": return new EolModule();
            case "evl": return new EvlModule();
            case "etl": return new EtlModule();
            case "egl": return new EglModule();
            case "egx": return new EgxModule(WORK.resolve("out").toString());
            case "ecl": return new EclModule();
            case "eml": return new EmlModule();
            case "epl": return new EplModule();
            case "ewl": return new EwlModule();
            case "flock": return new FlockModule();
            case "pinset": {
                PinsetModule pinset = new PinsetModule();
                pinset.setOutputFolder(WORK.resolve("out").toString());
                return pinset;
            }
            case "emg": {
                EmgModule emg = new EmgModule();
                emg.setSeed(42); // reproducible output
                emg.setUseSeed(true);
                return emg;
            }
            case "eunit": return new EUnitModule();
            default: throw new IllegalArgumentException("Unknown language: " + language);
        }
    }

    /** Parses from a file in the work directory, so that relative paths (imports, templates) resolve. */
    private boolean parse(IEolModule module, String fileName, String source) throws Exception {
        Path file = WORK.resolve("src").resolve(fileName);
        write(file, source);
        if (module.parse(new File(file.toString()))) {
            return true;
        }
        for (ParseProblem problem : module.getParseProblems()) {
            errStream.println("Parse error: " + problem);
        }
        return false;
    }

    private IModel loadModel(ModelSpec spec) throws Exception {
        Path file = WORK.resolve("models").resolve(spec.name + "." + spec.kind);
        boolean hasContent = spec.content != null && !spec.content.trim().isEmpty();
        write(file, hasContent ? spec.content : "json".equals(spec.kind) ? "{}" : "");
        // Load through StringProperties, like Epsilon's launch configurations and Ant tasks do
        StringProperties properties = new StringProperties();
        properties.put(Model.PROPERTY_NAME, spec.name);
        properties.put(Model.PROPERTY_READONLOAD, "true");
        properties.put(Model.PROPERTY_STOREONDISPOSAL, "false");
        Model model;
        if ("json".equals(spec.kind)) {
            properties.put(JsonModel.PROPERTY_FILE, file.toString());
            model = new JsonModel();
        } else if ("csv".equals(spec.kind)) {
            properties.put(CsvModel.PROPERTY_FILE, file.toString());
            properties.put(CsvModel.PROPERTY_HAS_KNOWN_HEADERS, "true");
            model = new CsvModel();
        } else {
            throw new IllegalArgumentException("Unknown model kind: " + spec.kind);
        }
        model.load(properties, path -> path);
        return model;
    }

    /** Language-specific summary of what a module produced; also the returned value for EOL/EGL. */
    private String report(IEolModule module, Object value) throws EolRuntimeException {
        if (module instanceof EvlModule) {
            Collection<UnsatisfiedConstraint> unsatisfied = ((EvlModule) module).getContext().getUnsatisfiedConstraints();
            if (unsatisfied.isEmpty()) {
                outStream.println("All constraints are satisfied.");
            }
            for (UnsatisfiedConstraint uc : unsatisfied) {
                outStream.println((uc.getConstraint().isCritique() ? "[warning] " : "[error] ")
                        + uc.getConstraint().getName() + ": " + uc.getMessage());
            }
            return "";
        }
        if (module instanceof EclModule) {
            long matching = ((EclModule) module).getContext().getMatchTrace().getReduced().stream().filter(Match::isMatching).count();
            outStream.println(matching + " matching pair(s)");
            return "";
        }
        if (module instanceof EplModule && value instanceof PatternMatchModel) {
            outStream.println(((PatternMatchModel) value).getMatches().size() + " match(es)");
            return "";
        }
        if (module instanceof EwlModule) {
            EwlModule ewl = (EwlModule) module;
            for (IModel model : module.getContext().getModelRepository().getModels()) {
                for (Object element : model.allContents()) {
                    for (WizardInstance wizard : ewl.getWizardsFor(element)) {
                        outStream.println("Applying: " + wizard.getWizard().getTitle(element, module.getContext()));
                        wizard.process();
                    }
                }
            }
            return "";
        }
        if (module instanceof EUnitModule) {
            printTests(((EUnitModule) module).getSuiteRoot(), "");
            return "";
        }
        // Only EOL programs (return statements) and EGL templates (generated text) have a meaningful value
        boolean meaningful = module.getClass() == EolModule.class || module instanceof EglModule;
        return value == null || !meaningful ? "" : pretty(module, value);
    }

    private void printTests(EUnitTest test, String indent) {
        if (test.isLeafTest()) {
            outStream.println(indent + test.getResult() + "  " + test.getCaseName()
                    + (test.getException() != null ? "  (" + test.getException().getMessage() + ")" : ""));
        }
        for (EUnitTest child : test.getChildren()) {
            printTests(child, test.isRootTest() ? indent : indent + "  ");
        }
    }

    private static String pretty(IEolModule module, Object o) {
        return module.getContext().getPrettyPrinterManager().toString(o);
    }

    private String[] finish(String result, Map<String, String> files) {
        outStream.flush();
        errStream.flush();
        return new String[] {
            new String(out.toByteArray(), StandardCharsets.UTF_8),
            new String(err.toByteArray(), StandardCharsets.UTF_8),
            result,
            toJson(files)
        };
    }

    /** Files.createDirectories(), which in TeaVM 0.16 throws if the directory already exists. */
    private static void mkdirs(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            Files.createDirectories(dir);
        }
    }

    private static void write(Path file, String content) throws IOException {
        if (file.getParent() != null) {
            mkdirs(file.getParent());
        }
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String toJson(Map<String, String> files) {
        JsonObject json = new JsonObject();
        files.forEach(json::addProperty);
        return json.toString();
    }

    private static void collectFiles(Path root, Map<String, String> files) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : paths.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
                files.put(root.relativize(p).toString(), new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                Files.delete(p);
            }
        }
    }
}
