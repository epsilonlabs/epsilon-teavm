# Eclipse Epsilon in the browser with TeaVM

Runs the [Eclipse Epsilon](https://eclipse.dev/epsilon/) 2.10.0-SNAPSHOT engines (from the Maven Central
snapshot repository) entirely in the browser, by compiling them to JavaScript with [TeaVM](https://teavm.org/) 0.16.0. The engines are
EOL, EVL, ETL, EGL/EGX, ECL, EML, EPL, EWL, Flock, Pinset, EMG and EUnit. Programs run against the
JSON and CSV model drivers.

```sh
mvn package
cd target/webapp && python3 -m http.server 8000   # then open http://localhost:8000
```

The page calls `runEpsilon(requestJson)`, exported from `demo.Main`, with:

```json
{"language": "evl", "program": "...",
 "models": [{"name": "Lib", "kind": "json", "content": "...", "output": false}],
 "files": {"book.egl": "..."}, "comparison": "ECL program run before an EML program"}
```

It returns `[stdout, stderr, returnValue, outputFilesJson]`. `demo.EpsilonRunner` writes models,
programs and templates to TeaVM's in-memory file system and loads them through the regular drivers.
The same code runs on the JVM: set `-Depsilon.work=<dir>` to point it at a real directory.

`src/main/webapp/examples.js` has examples for every language except Flock (see below).

## How it works

TeaVM compiles only the code reachable from `demo.Main`. Everything it reaches must use Java APIs that
TeaVM's class library provides. Epsilon's unmodified jars are used as regular dependencies; three
mechanisms cover the rest:

| Mechanism | Where | What it covers |
| --- | --- | --- |
| Emulations of missing JDK classes | `src/main/java/org/teavm/classlib/java/**` (TeaVM maps `java.x.Y` to `org.teavm.classlib.java.x.TY`) | `Scanner`; the concurrency classes Epsilon and Guava use (`ConcurrentLinkedQueue/Deque`, executors, locks, atomic arrays), as single-threaded versions; `java.sql` dates, `InetAddress` (for Gson); `java.lang.management` (EUnit's CPU timings) |
| Compiler plugin `demo.teavm.BrowserCompatPlugin` | registered in `META-INF/services` | **Redirects** calls to JDK methods TeaVM lacks or implements differently to `demo.teavm.JdkCompat`. **Stubs** methods for features a browser cannot support (HTTP model loading, XML reports and configuration, native processes, `escapeXml`, EMG's `nextURI`) so that their dependencies are never compiled. |
| Reflection policy `demo.teavm.EpsilonReflectionPolicy` | registered in `META-INF/services` | Epsilon programs call Java methods reflectively, and TeaVM only keeps reflection data for members it is told about. The policy exposes public methods of Epsilon classes and of `java.lang`, `java.util` and `java.math` types. |

## Upstream changes

Earlier versions of this demo patched 13 Epsilon classes on paths that every program takes. Those
changes are now part of Epsilon (commit `8ceb3ac2f`, in 2.10.0), so 2.9.0 is no longer enough. Each
change keeps the behaviour on the JVM and stays Java 8 compatible:

| Where | Before | After | Why it is equivalent |
| --- | --- | --- | --- |
| `ConcurrencyUtils` | `concurrentHashMap.keySet(Boolean.TRUE)` | `Collections.newSetFromMap(concurrentHashMap)` | Both are thread-safe sets backed by the same empty map. No caller uses the `KeySetView` type. |
| `DynamicOperation` | `method.getParameters()[i].getType()` | `method.getParameterTypes()[i]` | `Parameter.getType()` is defined as that entry of `getParameterTypes()`. |
| `EolNativeType`, `EolClasspathNativeTypeDelegate` | `loader.loadClass(name)` | `Class.forName(name, false, loader)` | Same loader, no class initialisation. `forName` also accepts array descriptors such as `[Ljava.lang.String;`. |
| `SortByOperation` | `Arrays.parallelSort(array, cmp)` | `Arrays.stream(array).parallel().sorted(cmp).toArray(...)` | A parallel `sorted()` with a comparator is implemented with `Arrays.parallelSort`, so `parallelSortBy` stays parallel and stable. Costs one extra array copy. |
| `IterableOperationContributor`, `ParallelMapByOperation`, `EolCollection`, `MatchTrace` | `collection.parallelStream()` | `collection.stream().parallel()` | The default `parallelStream()` builds the same pipeline. |
| `EglContext` | parses `DefaultConfig.xml` with a validating SAX parser for every context | new `DefaultContentTypeRepository` with the same content types in code | Same partitioners. XML configurations can still be loaded. A new test, `TestDefaultContentTypeRepository`, checks the code against `DefaultConfig.xml`. |
| Pinset `CSVFile` | `new PrintWriter(file)` | `new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file))))` | This is how the JDK implements `PrintWriter(File)`. |
| `FileUtil.copy` | copies with `FileChannel`s | copies with streams | Same result: the target is truncated and overwritten. |

On the JVM, the examples (17 programs across all languages) and a set of EOL programs aimed at the
changed code paths gave identical output with the original 2.9.0 jars and with these changes, and
2.10.0-SNAPSHOT gives the same output again.
The EOL programs cover sorting 20,000 elements with a stability check, `parallelMapBy`, `sum`, the
parallel operations, `ConcurrentSet`/`ConcurrentBag`, Java lambdas and `Native` types. The compiled
JavaScript gives the same output as the JVM.

## Findings

### What Epsilon could change

The changes above cover everything on the paths that programs take. Some further observations:

- **Optional features sit on core classes.** HTTP loading in `JsonModel` (Apache HttpClient) and
  XML reports in `EUnitModule` are referenced directly from methods every run uses. That also puts
  their dependencies on the classpath of every application that uses those drivers and engines.
  Here they are stubbed at compile time. Moving them behind small interfaces, chosen by whoever
  configures the model or module, would remove the need. Note that overriding a method in a subclass
  does *not* stop TeaVM from compiling the original: in a program this size its analysis falls back
  to class-hierarchy reasoning.
- **`FileUtil` creates `<java.io.tmpdir>/epsilon` in a static initialiser.** Any use of `FileUtil`
  has that side effect, and it prints an error if the directory cannot be created. Creating it on
  first use would be gentler.
- **Flock needs `IReflectiveModel`**, which neither the JSON nor the CSV driver implements. It
  therefore has no browser example. Implementing `IReflectiveModel` in `JsonModel` would make Flock
  usable with JSON.
- **`EolRuntimeException.getMessage()` includes EOL's stack trace as it is when the message is first
  requested**, not when the exception was raised. Output therefore depends on when callers first ask
  for the message.

### What TeaVM 0.16 gets wrong or lacks

Worth reporting to TeaVM. All are handled by the emulations and the plugin:

- `Method.invoke` and `Constructor.newInstance` do not wrap exceptions in `InvocationTargetException`.
  Epsilon relies on the wrapping for error locations and EUnit failure traces.
- `URL.openStream()` has no `file:` handler, and Epsilon reads programs and templates through `file:`
  URLs.
- `Files.createDirectories` throws `FileAlreadyExistsException` for an existing directory.
- Missing methods: `Spliterators.emptySpliterator()`, `File.toPath()`, `PrintWriter(File)`,
  `Collection.parallelStream()`, `Arrays.parallelSort`, `ConcurrentHashMap.keySet(V)`,
  `Method.getParameters()`, `ClassLoader.loadClass`.
- Missing classes: concurrency (`ConcurrentLinkedQueue/Deque`, `ExecutorService`, `ThreadPoolExecutor`,
  locks, atomic arrays), `Scanner`, `java.lang.management`, `java.sql` dates, `InetAddress`.
- Reflection data can mention types that were never compiled, and it includes native intrinsics. Both
  fail when the script loads. The policy leaves such members out.
- Exceptions from TeaVM's class library often have no message, for example
  `StringIndexOutOfBoundsException`.

## Known differences from the JVM

- Integer division by zero yields `0` instead of an `/ by zero` error.
- Parallel operations (`parallelSelect`, ...) run sequentially.
- In Node.js older than 21, parallel operations need `globalThis.navigator = { hardwareConcurrency: 1 }`,
  because TeaVM reads the CPU count from `navigator`. Browsers already provide it.
- `new Native("...")` only finds classes that are compiled into the JavaScript (such as `java.util.ArrayList`).
- The stubbed features raise "... is not available in the browser": loading models over HTTP, XML
  features, JUnit-style EUnit reports, native processes and EMG's `nextURI()`.
- No EMF or other model drivers are included. The JavaScript is about 11 MB.
