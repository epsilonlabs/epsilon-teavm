# EOL in the browser with TeaVM

Runs the [Eclipse Epsilon](https://eclipse.dev/epsilon/) Object Language (EOL) interpreter
(`org.eclipse.epsilon.eol.engine` 2.9.0 from Maven Central) entirely in the browser, by compiling
the Java engine to JavaScript with [TeaVM](https://teavm.org/) 0.16.0.

```sh
mvn package
cd target/webapp && python3 -m http.server 8000   # then open http://localhost:8000
```

`demo.Main.runEol(String)` is exported to JavaScript as `runEol(source)` and returns
`[stdout, stderr, returnValue]`.

## What it took to make Epsilon compile under TeaVM

| Problem | Fix |
| --- | --- |
| JDK classes TeaVM's classlib lacks: `Scanner`, `ConcurrentLinkedDeque/Queue`, `ExecutorService`, `ThreadPoolExecutor`, `Future`, `LinkedBlockingQueue`, ... | Minimal emulations in `src/main/java/org/teavm/classlib/java/util/**` (TeaVM maps `java.x.Y` to `org.teavm.classlib.java.x.TY`). The executor runs tasks synchronously, so parallel EOL operations run sequentially. |
| Epsilon methods calling JDK APIs absent from TeaVM (`Method.getParameters`, `ClassLoader.loadClass`, `parallelStream`, `Arrays.parallelSort`, `ConcurrentHashMap.keySet(V)`) | Patched copies of 7 Epsilon classes under `src/main/java/org/eclipse/epsilon/**`; each change is marked `TeaVM:`. |
| TeaVM puts dependency jars before `target/classes` on its classpath, so the patched classes were ignored | `maven-dependency-plugin` unpacks Epsilon into `target/classes` (skipping the patched classes) and TeaVM is limited to `provided`-scope jars (`compileScopes`). |
| EOL resolves every operation call (and the parser's entry rule) through Java reflection, which TeaVM only supports for members it is told about | `demo.teavm.EpsilonReflectionPolicy`, a TeaVM 0.16 `ReflectionPolicy` registered in `META-INF/services`, exposes public methods of Epsilon classes and of `java.lang`, `java.util` and `java.math` types. Native intrinsics, `eol.tools` (native processes) and `escapeXml` (needs `javax.xml`) are excluded. |

## Known differences from the JVM

- Integer division by zero yields `0` instead of an `/ by zero` error.
- Parallel operations (`parallelSelect`, ...) run sequentially.
- Java native types (`new Native("java.io.File")`), XML escaping and `System` native-process helpers are unavailable.
- Only the plain EOL engine is included: no EMF or other model drivers.
