package io.github.rkaushikdev.heddlewright.tools;

import io.github.rkaushikdev.heddlewright.util.Collation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The layer map, read off the code rather than written down.
 *
 * <p>Every class belongs to a layer, and the layers are ordered. A class may use classes from its
 * own layer or from any layer below it and from nowhere else. That one rule is what keeps the weaving
 * arithmetic free of the service and the store, so a reader can pick up {@code weave/Twill.java}
 * without meeting a router on the way.
 *
 * <p>A class uses another when it imports it, or, inside one package, when it names it. The tools
 * here read the code and are not part of it, so they sit in no layer and are not read.
 *
 * <p>The document this writes is generated for the same reason the surface document is: a hand-kept
 * diagram is out of date the week after it is drawn, and nobody finds out until they trust it.
 */
public final class Architecture {

    /** How the document is regenerated, which the document says at its top. */
    public static final String COMMAND = "java -cp target/classes " + Architecture.class.getName();

    /** The file the barrel lives in, which uses everything on purpose and sits in no layer. */
    public static final String BARREL = "Heddlewright.java";

    /** One layer: its name, the packages in it, and what it is for. */
    public record Layer(String name, List<String> areas, String summary) {
        /** Copies the areas. */
        public Layer {
            areas = List.copyOf(areas);
        }
    }

    /**
     * The layers, lowest first. A class may use classes downwards and sideways, never up. {@code util}
     * is the floor: it knows about numbers, matrices and errors and nothing about cloth.
     */
    public static final List<Layer> LAYERS = List.of(
            new Layer("util", List.of("util"), "numbers, matrices, runs, errors, dates, tables"),
            new Layer(
                    "craft",
                    List.of("draft", "weave", "analysis", "yarn", "warp"),
                    "drafting, weave construction, cloth analysis and yarn arithmetic"),
            new Layer(
                    "plant",
                    List.of("loom", "jacquard", "cloth"),
                    "the machines, their card decks and the cloth that comes off them"),
            new Layer("works", List.of("works"), "orders, scheduling, production, costing and despatch"),
            new Layer("store", List.of("store"), "the journal and the record projection"),
            new Layer("edge", List.of("service", "cli"), "the http service and the command line"));

    /** One class as the map reads it: where it is, its layer if it has one, and what it uses. */
    public record Entry(String file, Optional<String> layer, List<String> imports) {
        /** Copies the imports. */
        public Entry {
            imports = List.copyOf(imports);
        }
    }

    /** How often one layer uses another. */
    public record Edge(String edge, int count) {}

    /** How many classes a layer holds and how many uses they make. */
    public record Size(String layer, int files, int imports, String summary) {}

    /** Whether the layering holds and the document still matches the code, and if not why. */
    public record Check(boolean matches, String reason) {}

    private static final Pattern IMPORT =
            Pattern.compile("(?m)^import\\s+(static\\s+)?io\\.github\\.rkaushikdev\\.heddlewright\\.([\\w.]+)\\s*;");

    private Architecture() {}

    /** The document's path. */
    public static Path output() {
        return Sources.root().resolve("docs/architecture.md");
    }

    /** Every class the map reads, as a path from the package root with forward slashes. */
    public static List<String> sourceFiles() {
        return Sources.walk(Sources.mainSources(), List.of(".java")).stream()
                .map(file -> Sources.name(Sources.mainSources(), file))
                .filter(file -> !file.startsWith("tools/"))
                .toList();
    }

    /** The layer a class belongs to, or nothing for the barrel at the package root. */
    public static Optional<String> layerOf(String file) {
        int slash = file.indexOf('/');
        if (slash < 0) {
            return Optional.empty();
        }
        String area = file.substring(0, slash);
        return LAYERS.stream()
                .filter(layer -> layer.areas().contains(area))
                .map(Layer::name)
                .findFirst();
    }

    /** The rank of a layer, lowest first, or -1 for a name that is no layer. */
    public static int rankOf(String name) {
        for (int index = 0; index < LAYERS.size(); index += 1) {
            if (LAYERS.get(index).name().equals(name)) {
                return index;
            }
        }
        return -1;
    }

    /** What one class uses, with every class the map knows to resolve against. */
    public static List<String> importsOf(String file, String text) {
        return importsOf(file, text, new HashSet<>(sourceFiles()));
    }

    /**
     * What one class uses: the project classes it imports, each named once, then the classes of its
     * own package it names in its code (its comments and strings aside), as paths from the package
     * root. An import of a nested type or a static member is the file that holds it.
     */
    public static List<String> importsOf(String file, String text, Set<String> known) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = IMPORT.matcher(text);
        while (matcher.find()) {
            String[] parts = matcher.group(2).split("\\.", -1);
            for (int end = parts.length; end > 0; end -= 1) {
                String candidate = String.join("/", List.of(parts).subList(0, end)) + ".java";
                if (known.contains(candidate)) {
                    found.add(candidate);
                    break;
                }
            }
        }
        int slash = file.lastIndexOf('/');
        String folder = file.substring(0, slash + 1);
        String code = codeOf(text);
        for (String other : known.stream().sorted().toList()) {
            if (other.equals(file)
                    || !other.startsWith(folder)
                    || other.substring(folder.length()).contains("/")) {
                continue;
            }
            String simple = other.substring(folder.length(), other.length() - ".java".length());
            if (Pattern.compile("\\b" + Pattern.quote(simple) + "\\b")
                    .matcher(code)
                    .find()) {
                found.add(other);
            }
        }
        return List.copyOf(found);
    }

    /** The code of a source file with its comments and its string and character literals taken out. */
    static String codeOf(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                while (i < text.length() && text.charAt(i) != '\n') {
                    i += 1;
                }
            } else if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                int close = text.indexOf("*/", i + 2);
                i = close < 0 ? text.length() : close + 2;
            } else if (c == '"' || c == '\'') {
                i += 1;
                while (i < text.length() && text.charAt(i) != c) {
                    i += text.charAt(i) == '\\' ? 2 : 1;
                }
                i += 1;
                out.append(' ');
            } else {
                out.append(c);
                i += 1;
            }
        }
        return out.toString();
    }

    /** Read the whole tree into one entry per class. */
    public static List<Entry> readTree() {
        List<String> files = sourceFiles();
        Set<String> known = new HashSet<>(files);
        List<Entry> out = new ArrayList<>(files.size());
        for (String file : files) {
            String text = Sources.read(Sources.mainSources().resolve(file));
            out.add(new Entry(file, layerOf(file), importsOf(file, text, known)));
        }
        return List.copyOf(out);
    }

    /**
     * Every use that goes the wrong way. The barrel is left out because it exists to use everything,
     * and a class in no layer cannot break an order it is not in.
     */
    public static List<String> violations(List<Entry> tree) {
        List<String> problems = new ArrayList<>();
        for (Entry entry : tree) {
            if (entry.layer().isEmpty()) {
                continue;
            }
            for (String target : entry.imports()) {
                Optional<String> targetLayer = layerOf(target);
                if (targetLayer.isPresent()
                        && rankOf(targetLayer.get()) > rankOf(entry.layer().get())) {
                    problems.add(entry.file() + " imports " + target + ", which is a layer up");
                }
            }
        }
        return List.copyOf(problems);
    }

    /** How many times each layer reaches into each other layer, the busiest first. */
    public static List<Edge> layerGraph(List<Entry> tree) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Entry entry : tree) {
            if (entry.layer().isEmpty()) {
                continue;
            }
            for (String target : entry.imports()) {
                Optional<String> targetLayer = layerOf(target);
                if (targetLayer.isEmpty() || targetLayer.equals(entry.layer())) {
                    continue;
                }
                counts.merge(entry.layer().get() + " -> " + targetLayer.get(), 1, Integer::sum);
            }
        }
        List<Edge> out = new ArrayList<>();
        counts.forEach((edge, count) -> out.add(new Edge(edge, count)));
        out.sort(Comparator.comparingInt(Edge::count).reversed().thenComparing(Edge::edge, Collation.ORDER));
        return List.copyOf(out);
    }

    /** How many classes sit in each layer, and how many uses they make. */
    public static List<Size> layerSizes(List<Entry> tree) {
        List<Size> out = new ArrayList<>();
        for (Layer layer : LAYERS) {
            List<Entry> files = tree.stream()
                    .filter(entry -> entry.layer().equals(Optional.of(layer.name())))
                    .toList();
            int imports =
                    files.stream().mapToInt(entry -> entry.imports().size()).sum();
            out.add(new Size(layer.name(), files.size(), imports, layer.summary()));
        }
        return List.copyOf(out);
    }

    /** The classes nothing else uses, which is where a reader starts. */
    public static List<String> unimported(List<Entry> tree) {
        Set<String> wanted = new HashSet<>();
        for (Entry entry : tree) {
            wanted.addAll(entry.imports());
        }
        return tree.stream()
                .map(Entry::file)
                .filter(file -> !wanted.contains(file) && !file.equals(BARREL))
                .toList();
    }

    /** The whole document for the tree as it stands. */
    public static String render() {
        return render(readTree());
    }

    /** The whole document. */
    public static String render(List<Entry> tree) {
        List<String> lines = new ArrayList<>(List.of(
                "# How heddlewright is put together",
                "",
                "This file is generated by `" + COMMAND + "` after a build. Do not edit it by hand.",
                "",
                "## The layers",
                "",
                "Lowest first. A class may use classes from its own layer or from any layer",
                "below it, and never from one above.",
                ""));
        for (Size row : layerSizes(tree)) {
            lines.add("- **" + row.layer() + "** (" + row.files() + " files) - " + row.summary());
        }
        lines.addAll(List.of("", "## What reaches into what", ""));
        for (Edge edge : layerGraph(tree)) {
            lines.add("- `" + edge.edge() + "` " + edge.count() + " imports");
        }
        lines.addAll(List.of("", "## Nothing in src uses these", ""));
        lines.addAll(List.of("They are the ways in, so they are where a reader starts.", ""));
        for (String file : unimported(tree)) {
            lines.add("- `src/main/java/" + Sources.PACKAGE_PATH + "/" + file + "`");
        }
        lines.add("");
        return String.join("\n", lines);
    }

    /** Write the document out. */
    public static Path write() {
        try {
            Files.createDirectories(output().getParent());
            Files.writeString(output(), render(), StandardCharsets.UTF_8);
        } catch (IOException err) {
            throw new UncheckedIOException(err);
        }
        return output();
    }

    /** Whether the layering holds and the document on disk still matches the code. */
    public static Check check() {
        List<Entry> tree = readTree();
        List<String> broken = violations(tree);
        if (!broken.isEmpty()) {
            return new Check(false, String.join("; ", broken));
        }
        if (!Files.isRegularFile(output())) {
            return new Check(false, "the architecture document has not been written yet");
        }
        if (Sources.read(output()).equals(render(tree))) {
            return new Check(true, "");
        }
        return new Check(false, "the architecture document is out of date");
    }

    /** Write the document, or with {@code --check} say whether it is current and exit non-zero if not. */
    public static void main(String[] args) {
        if (List.of(args).contains("--check")) {
            Check result = check();
            if (!result.matches()) {
                System.err.println(result.reason());
            }
            System.exit(result.matches() ? 0 : 1);
        }
        System.out.println("wrote " + write());
    }
}
