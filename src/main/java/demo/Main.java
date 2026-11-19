package demo;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.epsilon.common.parse.problem.ParseProblem;
import org.eclipse.epsilon.eol.EolModule;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.teavm.jso.JSExport;

/**
 * Browser entry point: compiled to JavaScript by TeaVM. The page calls the exported
 * {@link #runEol(String)} function with the EOL source typed by the user.
 */
public class Main {

    public static void main(String[] args) {
        // Nothing to do on load; the page drives execution through runEol().
    }

    /**
     * Runs a program in any Epsilon language. See {@link EpsilonRunner#run(String)}.
     *
     * @param requestJson {"language": "evl", "program": "...", "models": [{"name", "kind", "content", "output"}], "comparison": "ECL program for EML"}
     * @return [stdout, stderr, returned value, JSON object of output files]
     */
    @JSExport
    public static String[] runEpsilon(String requestJson) {
        try {
            return EpsilonRunner.run(requestJson);
        } catch (Throwable t) {
            return new String[] {"", "Internal error: " + t + "\n", "", "{}"};
        }
    }

    /**
     * Parses and executes an EOL program.
     *
     * @return a 3-element array: [stdout, stderr, string value of the program's return statement or ""]
     */
    @JSExport
    public static String[] runEol(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream outStream = new PrintStream(out, true);
        PrintStream errStream = new PrintStream(err, true);
        String result = "";

        EolModule module = new EolModule();
        module.getContext().setOutputStream(outStream);
        module.getContext().setErrorStream(errStream);
        try {
            if (!module.parse(source)) {
                for (ParseProblem problem : module.getParseProblems()) {
                    errStream.println("Parse error: " + problem);
                }
            } else {
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
}
