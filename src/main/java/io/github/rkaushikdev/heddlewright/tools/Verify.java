package io.github.rkaushikdev.heddlewright.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The checks that are not tests.
 *
 * <p>Each stage can be run on its own, and nothing runs as the class loads: a tool only does work
 * when its {@code main} is run. A tool that acted as it loaded would rewrite the very thing its tests
 * are reading.
 */
public final class Verify {

    /** One stage: what it is called, how many files it read, and what it found. */
    public record Stage(String stage, int files, List<String> problems) {
        /** Copies the problems. */
        public Stage {
            problems = List.copyOf(problems);
        }
    }

    /** Every stage together, and every problem they found. */
    public record Report(List<Stage> stages, List<String> problems) {
        /** Copies the lists. */
        public Report {
            stages = List.copyOf(stages);
            problems = List.copyOf(problems);
        }
    }

    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([\\w.]+(?:\\.\\*)?)\\s*;");

    private static final Pattern MAIN =
            Pattern.compile("public\\s+static\\s+void\\s+main\\s*\\(\\s*String\\[\\]\\s+args\\s*\\)");

    private Verify() {}

    /** Every source file the checks read. */
    public static List<Path> sourceFiles() {
        return Sources.sourceFiles();
    }

    /** No file may carry a carriage return, a byte order mark or a tab, and every file ends with a newline. */
    public static Stage checkBytes() {
        List<Path> files = sourceFiles();
        if (files.isEmpty()) {
            throw new IllegalStateException("the byte check found no files to read");
        }
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            String text = Sources.read(file);
            String name = Sources.name(file);
            if (text.indexOf('\r') >= 0) {
                problems.add(name + " carries a carriage return");
            }
            if (!text.isEmpty() && text.charAt(0) == 0xfeff) {
                problems.add(name + " starts with a byte order mark");
            }
            if (text.indexOf('\t') >= 0) {
                problems.add(name + " carries a tab");
            }
            if (!text.endsWith("\n")) {
                problems.add(name + " does not end with a newline");
            }
        }
        return new Stage("bytes", files.size(), problems);
    }

    /** The class name a shipped source file declares, from its path. */
    static String className(Path file) {
        String relative = Sources.name(Sources.root().resolve("src/main/java"), file);
        return relative.substring(0, relative.length() - ".java".length()).replace('/', '.');
    }

    /** Every shipped class must load and initialise on its own. */
    public static Stage checkImports() {
        List<Path> files = Sources.walk(Sources.root().resolve("src/main/java"), List.of(".java"));
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            try {
                Class.forName(className(file), true, Verify.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError err) {
                problems.add(Sources.name(file) + " will not load: " + err);
            }
        }
        return new Stage("imports", files.size(), problems);
    }

    /** Nothing shipped may import the test libraries or the tests, and no import may be a wildcard. */
    public static Stage checkDependencies() {
        List<Path> files = Sources.walk(Sources.root().resolve("src/main/java"), List.of(".java"));
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            String name = Sources.name(file);
            Matcher found = IMPORT.matcher(Sources.read(file));
            while (found.find()) {
                String target = found.group(1);
                if (target.startsWith("org.junit") || target.startsWith("org.assertj")) {
                    problems.add(name + " imports from the tests");
                }
                if (target.endsWith(".*")) {
                    problems.add(name + " imports \"" + target + "\" without naming a class");
                }
            }
        }
        return new Stage("dependencies", files.size(), problems);
    }

    /**
     * Every test class must sit beside something it tests: a class in the same package whose name
     * the test's name starts with. That is a shipped class, or a support class among the tests, such
     * as the shared fixtures, which are tested where they live.
     */
    public static Stage checkTestsPaired() {
        Set<String> named = new HashSet<>();
        for (Path file : Sources.walk(Sources.mainSources(), List.of(".java"))) {
            named.add(stem(Sources.name(Sources.mainSources(), file)));
        }
        List<Path> tests = new ArrayList<>();
        for (Path file : Sources.walk(Sources.testSources(), List.of(".java"))) {
            String key = stem(Sources.name(Sources.testSources(), file));
            if (key.endsWith("Test")) {
                tests.add(file);
            } else {
                named.add(key);
            }
        }
        List<String> problems = new ArrayList<>();
        for (Path file : tests) {
            String key = stem(Sources.name(Sources.testSources(), file));
            String subject = key.substring(0, key.length() - "Test".length());
            if (!pairs(subject, named)) {
                problems.add(key + " has a test but nothing it tests");
            }
        }
        return new Stage("pairing", tests.size(), problems);
    }

    private static String stem(String relative) {
        return relative.substring(0, relative.length() - ".java".length());
    }

    private static boolean pairs(String subject, Set<String> named) {
        int slash = subject.lastIndexOf('/');
        String folder = subject.substring(0, slash + 1);
        String simple = subject.substring(slash + 1);
        // The subject is the name itself, or the name cut at a word: JournalFile reads as Journal.
        for (int end = simple.length(); end > 0; end -= 1) {
            boolean atWord = end == simple.length() || Character.isUpperCase(simple.charAt(end));
            if (atWord && named.contains(folder + simple.substring(0, end))) {
                return true;
            }
        }
        return false;
    }

    /** A tool runs only when it is run: each has a main method and none does work as it loads. */
    public static Stage checkScriptGuards() {
        List<Path> files = Sources.walk(Sources.mainSources().resolve("tools"), List.of(".java"));
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            String text = Sources.read(file);
            String name = Sources.name(file);
            if (!MAIN.matcher(text).find()) {
                problems.add(name + " has no main method to run it by");
            }
            if (Pattern.compile("(?m)^\\s*static\\s*\\{").matcher(text).find()) {
                problems.add(name + " does work as it loads");
            }
        }
        return new Stage("guards", files.size(), problems);
    }

    /** Run every stage and report them together. */
    public static Report verify() {
        List<Stage> stages =
                List.of(checkBytes(), checkImports(), checkDependencies(), checkTestsPaired(), checkScriptGuards());
        return new Report(
                stages,
                stages.stream().flatMap(stage -> stage.problems().stream()).toList());
    }

    /** Run every stage, print what each found, and exit non-zero if anything was wrong. */
    public static void main(String[] args) {
        Report result = verify();
        for (Stage stage : result.stages()) {
            System.out.println(stage.stage() + ": " + stage.files() + " files, "
                    + stage.problems().size() + " problems");
        }
        for (String problem : result.problems()) {
            System.err.println("  " + problem);
        }
        System.exit(result.problems().isEmpty() ? 0 : 1);
    }
}
