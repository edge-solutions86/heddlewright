package io.github.rkaushikdev.heddlewright.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The files the repository tools read.
 *
 * <p>The tools run from the project directory, which is where the build runs its tests from, and
 * read the Java sources under {@code src}. A directory whose name starts with a dot or is the build's
 * {@code target} is never entered, and only regular files are taken, so a directory that happens to
 * end in {@code .java} is not read as one.
 */
public final class Sources {

    /** The package every class lives under, as a path. */
    public static final String PACKAGE_PATH = "io/github/rkaushikdev/heddlewright";

    /** A source file: its path from the project directory, with forward slashes, and its text. */
    public record SourceFile(String name, String text) {}

    private Sources() {}

    /** The project directory the tools read from. */
    public static Path root() {
        return Path.of("").toAbsolutePath();
    }

    /** The directory holding the shipped classes' sources. */
    public static Path mainSources() {
        return root().resolve("src/main/java").resolve(PACKAGE_PATH);
    }

    /** The directory holding the tests' sources. */
    public static Path testSources() {
        return root().resolve("src/test/java").resolve(PACKAGE_PATH);
    }

    /** Every regular file under a directory whose name ends with one of the extensions, sorted. */
    public static List<Path> walk(Path directory, List<String> extensions) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return out;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            for (Path entry : entries.sorted().toList()) {
                String name = entry.getFileName().toString();
                if (name.startsWith(".") || name.equals("target")) {
                    continue;
                }
                if (Files.isDirectory(entry)) {
                    out.addAll(walk(entry, extensions));
                } else if (Files.isRegularFile(entry) && extensions.stream().anyMatch(name::endsWith)) {
                    out.add(entry);
                }
            }
        } catch (IOException err) {
            throw new UncheckedIOException(err);
        }
        return out;
    }

    /** Every Java source file the checks read: the shipped classes and the tests. */
    public static List<Path> sourceFiles() {
        List<Path> out = new ArrayList<>(walk(root().resolve("src/main/java"), List.of(".java")));
        out.addAll(walk(root().resolve("src/test/java"), List.of(".java")));
        return out;
    }

    /** A file's path from the project directory, with forward slashes. */
    public static String name(Path file) {
        return root().relativize(file.toAbsolutePath()).toString().replace('\\', '/');
    }

    /** A file's path from a directory, with forward slashes. */
    public static String name(Path base, Path file) {
        return base.relativize(file).toString().replace('\\', '/');
    }

    /** A file's text, read as UTF-8. */
    public static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException err) {
            throw new UncheckedIOException(err);
        }
    }

    /** Every source file with its text. */
    public static List<SourceFile> readAll() {
        return sourceFiles().stream()
                .map(file -> new SourceFile(name(file), read(file)))
                .toList();
    }

    /** List the files the checks read. */
    public static void main(String[] args) {
        for (Path file : sourceFiles()) {
            System.out.println(name(file));
        }
    }
}
