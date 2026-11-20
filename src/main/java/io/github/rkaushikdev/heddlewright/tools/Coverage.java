package io.github.rkaushikdev.heddlewright.tools;

import io.github.rkaushikdev.heddlewright.util.Collation;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reading the coverage report back.
 *
 * <p>The build enforces the floor on its own. What it will not tell you is which classes are dragging
 * the figure down, and that is the only part anybody acts on, so this reads the CSV report JaCoCo
 * writes and ranks them.
 */
public final class Coverage {

    /** The report JaCoCo writes at the end of a build. */
    public static final String REPORT = "target/site/jacoco/jacoco.csv";

    /** How many classes the ranking shows when nobody says. */
    public static final int LIMIT = 10;

    /** One class's figures: lines and branches, covered and not. */
    public record ClassCoverage(
            String file,
            int lines,
            int hit,
            int branches,
            int branchHit,
            double lineShare,
            double branchShare,
            int missed) {}

    /** The figures for the whole project. */
    public record Totals(int files, int lines, int hit, double lineShare, double branchShare) {}

    private Coverage() {}

    /** The report's path. */
    public static Path report() {
        return Sources.root().resolve(REPORT);
    }

    /** Whether the report has been written. */
    public static boolean hasReport() {
        return hasReport(report());
    }

    /** Whether a report has been written at a path. */
    public static boolean hasReport(Path path) {
        return Files.isRegularFile(path);
    }

    /**
     * Parse a JaCoCo CSV report into one record per class. Only the line and branch counters are
     * read; a line that is not a row of figures, the header among them, is passed over.
     */
    public static List<ClassCoverage> parseCsv(String text) {
        List<ClassCoverage> out = new ArrayList<>();
        for (String line : text.split("\\r?\\n", -1)) {
            String[] cells = line.split(",", -1);
            if (cells.length < 9) {
                continue;
            }
            double branchMissed = Text.toNumber(cells[5]);
            double branchCovered = Text.toNumber(cells[6]);
            double lineMissed = Text.toNumber(cells[7]);
            double lineCovered = Text.toNumber(cells[8]);
            if (Double.isNaN(branchMissed + branchCovered + lineMissed + lineCovered) || cells[2].isEmpty()) {
                continue;
            }
            int lines = (int) (lineMissed + lineCovered);
            int hit = (int) lineCovered;
            int branches = (int) (branchMissed + branchCovered);
            int branchHit = (int) branchCovered;
            String file = (cells[1].isEmpty() ? "" : cells[1].replace('.', '/') + "/") + cells[2];
            out.add(new ClassCoverage(
                    file,
                    lines,
                    hit,
                    branches,
                    branchHit,
                    lines == 0 ? 1 : (double) hit / lines,
                    branches == 0 ? 1 : (double) branchHit / branches,
                    lines - hit));
        }
        return List.copyOf(out);
    }

    /** The ten classes with the most uncovered lines, worst first. */
    public static List<ClassCoverage> worstCovered(List<ClassCoverage> files) {
        return worstCovered(files, LIMIT);
    }

    /** The classes with the most uncovered lines, worst first. */
    public static List<ClassCoverage> worstCovered(List<ClassCoverage> files, int limit) {
        return files.stream()
                .filter(entry -> entry.missed() > 0)
                .sorted(Comparator.comparingInt(ClassCoverage::missed)
                        .reversed()
                        .thenComparing(ClassCoverage::file, Collation.ORDER))
                .limit(limit)
                .toList();
    }

    /** The whole-project figures, added up from the per-class ones. */
    public static Totals totals(List<ClassCoverage> files) {
        int lines = 0;
        int hit = 0;
        int branches = 0;
        int branchHit = 0;
        for (ClassCoverage entry : files) {
            lines += entry.lines();
            hit += entry.hit();
            branches += entry.branches();
            branchHit += entry.branchHit();
        }
        return new Totals(
                files.size(),
                lines,
                hit,
                lines == 0 ? 1 : (double) hit / lines,
                branches == 0 ? 1 : (double) branchHit / branches);
    }

    /** Read the report from disk, or nothing when there is none. */
    public static Optional<List<ClassCoverage>> readReport() {
        return readReport(report());
    }

    /** Read a report from a path, or nothing when there is none. */
    public static Optional<List<ClassCoverage>> readReport(Path path) {
        if (!hasReport(path)) {
            return Optional.empty();
        }
        return Optional.of(parseCsv(Sources.read(path)));
    }

    /** Print the project figure and the classes that drag it down. */
    public static void main(String[] args) {
        Optional<List<ClassCoverage>> files = readReport();
        if (files.isEmpty()) {
            System.err.println("no coverage report yet, run ./mvnw verify first");
            System.exit(1);
        }
        Totals summary = totals(files.get());
        System.out.println(summary.files() + " classes, " + Text.fixed(summary.lineShare() * 100, 2) + "% of lines");
        for (ClassCoverage entry : worstCovered(files.get())) {
            System.out.println("  " + entry.missed() + " lines uncovered in " + entry.file());
        }
    }
}
