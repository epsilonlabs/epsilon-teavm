# EOL in the browser with TeaVM

Runs [Eclipse Epsilon](https://eclipse.dev/epsilon/)'s EOL engine entirely in the browser, by compiling
it to JavaScript with [TeaVM](https://teavm.org/) 0.16.0. Programs can query a model written in
[Flexmi](https://eclipse.dev/epsilon/doc/flexmi/), against a metamodel written in
[Emfatic](https://eclipse.dev/emfatic/).

> [!NOTE]
> Try it at https://epsilonlabs.github.io/epsilon-teavm/

To build and run it locally:

```sh
mvn package
cd target/webapp && python3 -m http.server 8000   # then open http://localhost:8000
```
<img width="1459" height="887" alt="image" src="https://github.com/user-attachments/assets/a9aa7839-3a81-44fe-80b4-db8c9a027464" />

The page (`src/main/webapp/index.html`) follows the [Epsilon Playground](https://eclipse.dev/epsilon/playground/):
the same editor layout and its "Query Project Plan" example. It calls `runEol(program, emfatic, flexmi)`, exported from
`demo.Main`, which returns `[stdout, stderr, returnValue]`. All three arguments must be strings:

- `emfatic` builds the metamodel's packages, which the model refers to by namespace URI.
- `flexmi` is the model, in Flexmi's XML or YAML syntax. The program sees it as model `M`.
  Ecore itself is also available as a metamodel.
- If both are empty, the program runs without a model.

Load `classes.js` as UTF-8 (`<meta charset="utf-8">` or `<script charset="utf-8">`): the parser's
tables are stored in non-ASCII string literals, and parsing fails with
`StringIndexOutOfBoundsException` if they are decoded with another encoding.

Epsilon 2.10.0 or later is needed (until its release, `2.10.0-SNAPSHOT` from Maven Central's snapshot
repository). `classes.js` is about 9.5 MB (1.6 MB gzipped); without the model support it would be 2.6 MB.

## How it works

TeaVM compiles only the code reachable from `demo.Main`, and everything it reaches must use Java APIs
that TeaVM's class library provides. These pieces cover what EOL, EMF, Emfatic, Flexmi and Xerces
(Flexmi's XML parser) need beyond that:

| Piece | What it does |
| --- | --- |
| `src/main/java/org/teavm/classlib/java/**` | Supplies JDK classes TeaVM lacks. `Scanner` reads program sources. The concurrency classes (queues, executors, locks, `AtomicReferenceArray`) are single-threaded versions, used by EOL's parallel operations, EMF and Guava. A few classes are only referenced: `ObjectInputStream`/`ObjectOutputStream` (which throw), `CharConversionException` and `RuntimePermission`. TeaVM uses a class named `org.teavm.classlib.java.x.TY` as its implementation of `java.x.Y`. |
| `demo.teavm.BrowserCompatPlugin` | A compiler plugin with two tables. `METHODS` replaces the bodies of library methods. Some features a browser cannot support (native processes, the Eclipse workspace) throw "... is not available in the browser", so their dependencies are never compiled. Other methods call replacements in `demo.teavm.Replacements` (see below). `CALLS` redirects calls to methods TeaVM lacks, such as `ClassLoader.loadClass`, and to Eclipse's `Platform`, which is not on the classpath. |
| `demo.teavm.Replacements` | The replacements. JAXP and Xerces normally look up their implementation classes by name, which TeaVM cannot do, so the replacements create them directly. Flexmi builds its DOM with an XSLT identity transformation, and Xerces has no XSLT engine, so the replacement builds the same DOM from SAX events with `demo.teavm.DomBuilder`. |
| `demo.teavm.EpsilonReflectionPolicy` | EOL calls Java methods reflectively, for example `"abc".toUpperCase()` or `book.eClass()`. TeaVM only keeps reflection data for members it is told about. The policy keeps the public methods of Epsilon's classes, of EMF's `ecore`, `ecore.impl` and `common.util` packages, and of `java.lang`, `java.lang.ref`, `java.util` and `java.math` types. |
| `org.eclipse.core.runtime.Plugin` | An empty placeholder. EMF has a subclass that is only used inside Eclipse, and TeaVM needs its superclass to exist. |
| `META-INF/services/java.util.ResourceBundle` | Lists Xerces' error messages, so that TeaVM embeds them; XML syntax errors are reported with them. |

The two TeaVM extensions are registered in `src/main/resources/META-INF/services`.

## Differences from the JVM

- Parallel operations (`parallelSelect`, ...) run sequentially.
- Integer division by zero yields `0` instead of an `/ by zero` error.
- Exceptions from TeaVM's class library often have no message, for example
  `StringIndexOutOfBoundsException`.
- `new Native("...")` only finds classes that are compiled into the JavaScript (such as `java.util.ArrayList`).
- `escapeXml()` and `System.executeNative(...)` are not available.
- The model is a single Flexmi document: `<?import?>`, `<?include?>` and `<?eol?>` refer to other files,
  which do not exist in the browser.
- When Flexmi cannot tell which of several equally similar classes an element names, it picks the first
  it finds in a `HashSet`. That order can differ from the JVM's.

The Flexmi test models that do not refer to other files (`org.eclipse.epsilon.flexmi.test`: 20 in XML,
16 in YAML) load to the same model in JavaScript as on the JVM. The exceptions are two YAML files: one
reports the same warnings in a different order, and the other overflows the stack on both platforms.
