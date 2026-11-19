# EOL in the browser with TeaVM

Runs [Eclipse Epsilon](https://eclipse.dev/epsilon/)'s EOL engine entirely in the browser, by compiling
it to JavaScript with [TeaVM](https://teavm.org/) 0.16.0.

```sh
mvn package
cd target/webapp && python3 -m http.server 8000   # then open http://localhost:8000
```

The page (`src/main/webapp/index.html`) calls `runEol(source)`, exported from `demo.Main`, which returns
`[stdout, stderr, returnValue]`. Load `classes.js` as UTF-8 (`<meta charset="utf-8">` or
`<script charset="utf-8">`): the parser's tables are stored in non-ASCII string literals, and parsing
fails with `StringIndexOutOfBoundsException` if they are decoded with another encoding.

## How it works

TeaVM compiles only the code reachable from `demo.Main`, and everything it reaches must use Java APIs
that TeaVM's class library provides. Three small pieces cover what EOL needs beyond that:

| Piece | What it does |
| --- | --- |
| `src/main/java/org/teavm/classlib/java/util/**` | Supplies JDK classes TeaVM lacks: `Scanner` (the parser reads program sources with it) and the concurrency classes behind EOL's parallel operations, as single-threaded versions. TeaVM uses a class named `org.teavm.classlib.java.x.TY` as its implementation of `java.x.Y`. |
| `demo.teavm.BrowserCompatPlugin` | A compiler plugin that replaces the few EOL methods that need native processes or an XML parser with code that throws "... is not available in the browser". The APIs they use are then never compiled. |
| `demo.teavm.EpsilonReflectionPolicy` | EOL calls Java methods reflectively (for example `"abc".toUpperCase()`), and TeaVM only keeps reflection data for members it is told about. The policy keeps the public methods of Epsilon classes and of `java.lang`, `java.util` and `java.math` types. |

Both TeaVM extensions are registered in `src/main/resources/META-INF/services`.

## Differences from the JVM

- Parallel operations (`parallelSelect`, ...) run sequentially.
- Integer division by zero yields `0` instead of an `/ by zero` error.
- Exceptions from TeaVM's class library often have no message, for example
  `StringIndexOutOfBoundsException`.
- `new Native("...")` only finds classes that are compiled into the JavaScript (such as `java.util.ArrayList`).
- `escapeXml()` and `System.executeNative(...)` are not available.