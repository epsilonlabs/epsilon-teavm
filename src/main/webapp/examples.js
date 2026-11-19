// Example programs for every Epsilon language, run against JSON and CSV models.
// Each entry is a request for runEpsilon(): {language, program, models, comparison?, files?}
// plus a title and a one-line description for the page.
(function (global) {
  const library = JSON.stringify({
    name: "Department Library",
    books: [
      { isbn: "978-0321712943", title: "Domain-Specific Languages", year: 2010, pages: 640, authors: ["Martin Fowler", "Rebecca Parsons"] },
      { isbn: "978-1627057080", title: "Model-Driven Software Engineering in Practice", year: 2017, pages: 207, authors: ["Marco Brambilla", "Jordi Cabot", "Manuel Wimmer"] },
      { isbn: "978-0321331885", title: "EMF: Eclipse Modeling Framework", year: 2008, pages: 744, authors: ["Dave Steinberg", "Frank Budinsky", "Marcelo Paternostro", "Ed Merks"] },
      { isbn: "978-0134757599", title: "Refactoring", year: 2018, pages: 448, authors: ["Martin Fowler"] },
      { isbn: "", title: "Untitled Draft", year: 2031, pages: 0, authors: [] }
    ]
  }, null, 2);

  const wishlist = JSON.stringify({
    owner: "Reading Group",
    books: [
      { isbn: "978-0134757599", title: "Refactoring (2nd edition)", priority: "high" },
      { isbn: "978-0201633610", title: "Design Patterns", priority: "medium" },
      { isbn: "978-1627057080", title: "MDSE in Practice", priority: "low" }
    ]
  }, null, 2);

  const members = [
    "id,name,joined,loans",
    "m1,Ada Lovelace,2019,4",
    "m2,Alan Turing,2021,0",
    "m3,Grace Hopper,2018,7",
    "m4,Edsger Dijkstra,2023,2"
  ].join("\n");

  const lib = (output) => ({ name: "Lib", kind: "json", content: library, output: !!output });
  const wish = { name: "Wish", kind: "json", content: wishlist };
  const people = { name: "Members", kind: "csv", content: members };
  const empty = (name) => ({ name, kind: "json", content: "", output: true });

  global.EPSILON_EXAMPLES = [
    {
      id: "eol-hello", language: "eol", title: "EOL: hello",
      description: "Prints to the console; println() works on any value.",
      models: [],
      program:
`// println() is available on every object
"Hello from Epsilon, running in your browser!".println();

var langs = Sequence{"EOL", "ETL", "EGL", "EVL"};
("Epsilon has " + langs.size() + " languages listed here: " + langs.concat(", ")).println();`
    },
    {
      id: "eol-collections", language: "eol", title: "EOL: collections",
      description: "select, reject, collect, sortBy and friends on a sequence of numbers.",
      models: [],
      program:
`var scores = Sequence{72, 95, 58, 81, 64, 99, 47};

("Passed:  " + scores.select(s | s >= 60)).println();
("Failed:  " + scores.reject(s | s >= 60)).println();
("Curved:  " + scores.collect(s | (s * 1.05).round())).println();
("Sorted:  " + scores.sortBy(s | -s)).println();
("Top:     " + scores.max() + ", mean " + (scores.sum() / scores.size().asReal()).format("%.1f")).println();
("Any 100? " + scores.exists(s | s = 100)).println();
("By band: " + scores.mapBy(s | (s / 10) * 10)).println();

// The value of a top-level return is shown in the console
return scores.select(s | s > 90).size();`
    },
    {
      id: "eol-operations", language: "eol", title: "EOL: operations and recursion",
      description: "Recursive operations, and an operation that extends Integer.",
      models: [],
      program:
`for (n in 1.to(12)) {
  (n.toString().pad(3, " ", false) + "  fib=" + fib(n) + "  " + n.collatzSteps() + " Collatz steps").println();
}

operation fib(n : Integer) : Integer {
  if (n < 2) { return n; }
  return fib(n - 1) + fib(n - 2);
}

// An operation with a context type extends Integer itself
operation Integer collatzSteps() : Integer {
  var x = self;
  var steps = 0;
  while (x <> 1) {
    if (x.mod(2) == 0) { x = x / 2; } else { x = 3 * x + 1; }
    steps++;
  }
  return steps;
}`
    },
    {
      id: "eol-tuples", language: "eol", title: "EOL: maps and tuples",
      description: "Maps and tuples standing in for model elements.",
      models: [],
      program:
`// Without a modelling framework, maps and tuples stand in for model elements
var library = Sequence{
  new Tuple(title = "Domain-Specific Languages", author = "Fowler", year = 2010, tags = Set{"dsl", "design"}),
  new Tuple(title = "Model-Driven Software Engineering", author = "Brambilla", year = 2017, tags = Set{"mde"}),
  new Tuple(title = "Software Language Engineering", author = "Kleppe", year = 2008, tags = Set{"dsl", "mde"}),
  new Tuple(title = "EMF: Eclipse Modeling Framework", author = "Steinberg", year = 2008, tags = Set{"mde", "eclipse"})
};

var byTag = new Map;
for (book in library) {
  for (tag in book.tags) {
    if (not byTag.containsKey(tag)) { byTag.put(tag, new Sequence); }
    byTag.get(tag).add(book.title);
  }
}

for (tag in byTag.keySet().sortBy(t | t)) {
  ("#" + tag).println();
  for (title in byTag.get(tag)) { ("  - " + title).println(); }
}

var oldest = library.sortBy(b | b.year).first();
return oldest.title + " (" + oldest.year + ")";`
    },
    {
      id: "eol-errors", language: "eol", title: "EOL: errors and stack traces",
      description: "A runtime error reported with its location in the program.",
      models: [],
      program:
`var shapes = Sequence{"circle", "square", "triangle"};
for (s in shapes) {
  s.describe().println();
}

operation String describe() : String {
  if (self = "triangle") {
    // Calling an operation that does not exist raises an EOL runtime error
    return self.sides();
  }
  return "A " + self + " has " + self.length() + " letters";
}`
    },
    {
      id: "eol-json", language: "eol", title: "EOL: query JSON and CSV models",
      description: "Navigates the book catalogue and a CSV member list with first-order operations.",
      models: [lib(), people],
      program:
`// Every JSON object is a JSONObject; its keys are properties
var books = Lib!JSONObject.all.select(o | o.containsKey("isbn"));
("Books in " + Lib.root.name + ": " + books.size()).println();

for (b in books.sortBy(b | -b.year)) {
  (b.year + "  " + b.title + " (" + b.authors.size() + " authors)").println();
}

var authors = books.collect(b | b.authors).flatten().asSet();
("Distinct authors: " + authors.size()).println();

// CSV rows have the type Row, and columns are properties
var busiest = Members!Row.all.sortBy(m | -m.loans.asInteger()).first();
("Most loans: " + busiest.name + " with " + busiest.loans).println();

return books.collect(b | b.pages).max();`
    },
    {
      id: "evl", language: "evl", title: "EVL: validate the catalogue",
      description: "Constraints and critiques over JSON objects, reported as errors and warnings.",
      models: [lib()],
      program:
`context JSONObject {
  guard: self.containsKey("isbn")

  // "title" is an EVL keyword, so the JSON driver's p_ prefix is used to read it
  constraint HasIsbn {
    check: self.isbn.length() > 0
    message: "'" + self.p_title + "' has no ISBN"
  }

  constraint NotFromTheFuture {
    check: self.year <= 2026
    message: "'" + self.p_title + "' is dated " + self.year
  }

  constraint HasPages {
    guard: self.satisfies("HasIsbn")
    check: self.pages > 0
    message: "'" + self.p_title + "' has no page count"
  }

  critique HasAuthors {
    check: self.authors.size() > 0
    message: "'" + self.p_title + "' lists no authors"
  }

  critique NotTooLong {
    check: self.pages < 700
    message: "'" + self.p_title + "' is " + self.pages + " pages long"
  }
}`
    },
    {
      id: "etl", language: "etl", title: "ETL: build a reading list",
      description: "Transforms catalogue books into a new JSON model, shown under Output files.",
      models: [lib(), empty("ReadingList")],
      program:
`rule BookToEntry
  transform b : Lib!JSONObject
  to e : ReadingList!JSONObject {

  guard: b.containsKey("isbn") and b.isbn.length() > 0

  e.title = b.title;
  e.byline = b.authors.concat(", ");
  e.hours = (b.pages / 40.0).ceiling();
}

post {
  var root = new ReadingList!JSONObject;
  root.source = Lib.root.name;
  root.entries = Lib!JSONObject.all.equivalent().sortBy(e | e.hours);
  ReadingList.root = root;
  ("Created " + root.entries.size() + " entries").println();
}`
    },
    {
      id: "egl", language: "egl", title: "EGL: generate Markdown",
      description: "A template whose output is the generated text.",
      models: [lib()],
      program:
`# [%=Lib.root.name%]

| Year | Title | Authors |
|------|-------|---------|
[% for (b in Lib.root.books.sortBy(b | b.year)) { %]
| [%=b.year%] | [%=b.title%] | [%=b.authors.concat(", ")%] |
[% } %]

[%=Lib.root.books.size()%] books, [%=Lib.root.books.collect(b | b.pages).sum()%] pages in total.`
    },
    {
      id: "egx", language: "egx", title: "EGX: one file per book",
      description: "A generation rule that runs an EGL template for each book and writes the files.",
      models: [lib()],
      files: {
        "book.egl":
`# [%=b.title%]

- ISBN: [%=b.isbn%]
- Published: [%=b.year%]
- Authors: [%=b.authors.concat(", ")%]
[% if (b.pages > 500) { %]

This is a long one: [%=b.pages%] pages.
[% } %]`
      },
      program:
`rule BookToPage
  transform b : Lib!JSONObject {

  guard: b.containsKey("isbn") and b.isbn.length() > 0

  template: "book.egl"
  target: "books/" + b.isbn + ".md"
}`
    },
    {
      id: "ecl", language: "ecl", title: "ECL: compare two models",
      description: "Matches catalogue books with wishlist entries by ISBN.",
      models: [lib(), wish],
      program:
`rule MatchBooks
  match l : Lib!JSONObject
  with w : Wish!JSONObject {

  guard: l.containsKey("isbn") and w.containsKey("isbn")

  compare: l.isbn = w.isbn
}

post {
  for (m in matchTrace.matches.select(m | m.matching)) {
    (m.left.isbn + ": '" + m.left.title + "' matches wishlist entry '" + m.right.title + "'").println();
  }
}`
    },
    {
      id: "eml", language: "eml", title: "EML: merge two models",
      description: "Uses the ECL matches to merge the catalogue and the wishlist into a new model.",
      models: [lib(), wish, empty("Merged")],
      comparison:
`rule MatchBooks
  match l : Lib!JSONObject
  with w : Wish!JSONObject {
  guard: l.containsKey("isbn") and w.containsKey("isbn")
  compare: l.isbn = w.isbn
}`,
      program:
`rule MergeBooks
  merge l : Lib!JSONObject
  with w : Wish!JSONObject
  into m : Merged!JSONObject {

  m.isbn = l.isbn;
  m.title = l.title;
  m.owned = true;
  m.priority = w.priority;
}

rule CopyOwned
  transform l : Lib!JSONObject
  to m : Merged!JSONObject {

  guard: l.containsKey("isbn") and l.isbn.length() > 0
  m.isbn = l.isbn;
  m.title = l.title;
  m.owned = true;
}

rule CopyWished
  transform w : Wish!JSONObject
  to m : Merged!JSONObject {

  guard: w.containsKey("isbn")
  m.isbn = w.isbn;
  m.title = w.title;
  m.owned = false;
  m.priority = w.priority;
}

post {
  var books = Lib!JSONObject.all.includingAll(Wish!JSONObject.all).equivalent().asSet();
  var root = new Merged!JSONObject;
  root.books = books.sortBy(b | b.isbn);
  Merged.root = root;
  ("Merged into " + books.size() + " books").println();
}`
    },
    {
      id: "epl", language: "epl", title: "EPL: find patterns",
      description: "Finds pairs of books that share an author.",
      models: [lib()],
      program:
`pattern SharedAuthor
  first : Lib!JSONObject
    guard: first.containsKey("authors"),
  second : Lib!JSONObject
    guard: second.containsKey("authors") and first.isbn.compareTo(second.isbn) < 0 {

  match: first.authors.exists(a | second.authors.includes(a))

  onmatch {
    var shared = first.authors.select(a | second.authors.includes(a));
    (shared.first() + " wrote '" + first.title + "' and '" + second.title + "'").println();
  }
}`
    },
    {
      id: "ewl", language: "ewl", title: "EWL: apply wizards",
      description: "In-place transformations, applied here to every element they match.",
      models: [lib(true)],
      program:
`// "title" is an EWL keyword, so the JSON driver's p_ prefix is used to read it
wizard AssignPlaceholderIsbn {
  guard: self.isKindOf(Lib!JSONObject) and self.containsKey("isbn") and self.isbn = ""
  title: "Assign a placeholder ISBN to " + self.p_title
  do {
    self.isbn = "TBD";
  }
}

wizard AddShelfMark {
  guard: self.isKindOf(Lib!JSONObject) and self.containsKey("authors") and self.authors.notEmpty()
  title: "Add a shelf mark to " + self.p_title
  do {
    self.shelf = self.authors.first().split(" ").last().substring(0, 3).toUpperCase();
  }
}`
    },
    {
      id: "pinset", language: "pinset", title: "Pinset: extract a dataset",
      description: "Turns the catalogue into a CSV dataset, shown under Output files.",
      models: [lib()],
      program:
`dataset books over b : Lib!JSONObject {
  guard: b.containsKey("isbn") and b.isbn.length() > 0
  properties [title, year, pages]
  column authors : b.authors.size()
  column firstAuthor : b.authors.first()
  column pagesPerAuthor : (b.pages / b.authors.size()).floor()
}`
    },
    {
      id: "emg", language: "emg", title: "EMG: generate a random model",
      description: "Creates random JSON objects with a fixed seed.",
      models: [empty("Gen")],
      program:
`pre {
  var root = new Gen!JSONObject;
  root.members = new Sequence;
  Gen.root = root;
}

$instances 5
operation Gen!JSONObject create() {
  self.name = nextFromCollection(Sequence{"Ada", "Alan", "Grace", "Edsger", "Barbara"})
    + " " + nextCapitalisedString("LETTER_LOWER", 6);
  self.age = nextInteger(18, 80);
  self.website = nextHttpURI(false, true, false, false);
  self.subscribed = nextBoolean();
  Gen.root.members.add(self);
}

post {
  ("Generated " + Gen.root.members.size() + " members").println();
}`
    },
    {
      id: "eunit", language: "eunit", title: "EUnit: test the model",
      description: "Unit tests over the catalogue; one of them fails on purpose.",
      models: [lib()],
      program:
`@test
operation everyBookHasATitle() {
  for (b in Lib!JSONObject.all.select(o | o.containsKey("isbn"))) {
    assertTrue(b.title.length() > 0);
  }
}

@test
operation fowlerWroteTwoBooks() {
  var fowler = Lib!JSONObject.all.select(o | o.containsKey("authors") and o.authors.includes("Martin Fowler"));
  assertEquals(2, fowler.size());
}

@test
operation noBookFromTheFuture() {
  var future = Lib!JSONObject.all.select(o | o.containsKey("year") and o.year > 2026);
  assertEquals(0, future.size());
}`
    }
  ];
})(typeof window !== "undefined" ? window : globalThis);
