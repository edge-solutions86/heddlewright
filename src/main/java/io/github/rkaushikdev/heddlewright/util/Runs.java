package io.github.rkaushikdev.heddlewright.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Run-length work over a one-dimensional sequence.
 *
 * <p>Float measurement is the reason this exists: a warp float is a run of raised cells down a
 * column of the drawdown, and a weft float is a run of lowered cells across a row. Both are the same
 * question asked of different slices, and both have to understand that a repeat wraps round.
 */
public final class Runs {

    /** One maximal block of equal values: where it starts, how long it is, and whether it wraps. */
    public record Run<T>(T value, int start, int length, boolean wraps) {}

    /** How {@link #formatRuns} writes a sequence. */
    public enum Style {
        /** {@code 1x4 0x2}. */
        COMPACT,
        /** {@code 4 of 1, 2 of 0}. */
        LONG;

        /** Read a style by its name, refusing anything else with the list of the ones it knows. */
        public static Style parse(String text) {
            return Check.member(text, "style", values());
        }

        @Override
        public String toString() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private Runs() {}

    /** Split a sequence into runs, left to right. */
    public static <T> List<Run<T>> runs(List<T> sequence) {
        Check.array(sequence, "sequence");
        List<Run<T>> out = new ArrayList<>();
        int start = 0;
        for (int i = 1; i <= sequence.size(); i += 1) {
            if (i == sequence.size() || !Objects.equals(sequence.get(i), sequence.get(start))) {
                out.add(new Run<>(sequence.get(start), start, i - start, false));
                start = i;
            }
        }
        return List.copyOf(out);
    }

    /**
     * Runs over a sequence read as a closed loop, so a run touching both ends is reported once,
     * anchored at its true start, with a start index that may be larger than its end.
     */
    public static <T> List<Run<T>> cyclicRuns(List<T> sequence) {
        Check.array(sequence, "sequence");
        if (sequence.isEmpty()) {
            return List.of();
        }
        List<Run<T>> flat = runs(sequence);
        if (flat.size() == 1) {
            return List.of(new Run<>(sequence.get(0), 0, sequence.size(), false));
        }
        Run<T> first = flat.get(0);
        Run<T> last = flat.get(flat.size() - 1);
        if (!Objects.equals(first.value(), last.value())) {
            return flat;
        }
        List<Run<T>> merged = new ArrayList<>(flat.subList(1, flat.size() - 1));
        merged.add(new Run<>(last.value(), last.start(), last.length() + first.length(), true));
        return List.copyOf(merged);
    }

    private static <T> List<Run<T>> select(List<T> sequence, boolean cyclic) {
        return cyclic ? cyclicRuns(sequence) : runs(sequence);
    }

    /** The longest run of {@code value}, counting the sequence as a loop. */
    public static <T> int longestRun(List<T> sequence, T value) {
        return longestRun(sequence, value, true);
    }

    /** The longest run of {@code value}, as a loop or not. */
    public static <T> int longestRun(List<T> sequence, T value, boolean cyclic) {
        int best = 0;
        for (Run<T> run : select(sequence, cyclic)) {
            if (Objects.equals(run.value(), value)) {
                best = Math.max(best, run.length());
            }
        }
        return best;
    }

    /** How many runs of {@code value} there are in the loop, which is the interlacement count for a column. */
    public static <T> int countRuns(List<T> sequence, T value) {
        return countRuns(sequence, value, true);
    }

    /** How many runs of {@code value} there are, as a loop or not. */
    public static <T> int countRuns(List<T> sequence, T value, boolean cyclic) {
        return (int) select(sequence, cyclic).stream()
                .filter(run -> Objects.equals(run.value(), value))
                .count();
    }

    /** A map from run length to how many runs of that length carry {@code value}, in the loop. */
    public static <T> Map<Integer, Integer> runHistogram(List<T> sequence, T value) {
        return runHistogram(sequence, value, true);
    }

    /** A map from run length to how many runs of that length carry {@code value}. */
    public static <T> Map<Integer, Integer> runHistogram(List<T> sequence, T value, boolean cyclic) {
        Map<Integer, Integer> hist = new TreeMap<>();
        for (Run<T> run : select(sequence, cyclic)) {
            if (Objects.equals(run.value(), value)) {
                hist.merge(run.length(), 1, Integer::sum);
            }
        }
        return hist;
    }

    /** Every run of {@code value} in the loop longer than {@code limit}, for float-length reporting. */
    public static <T> List<Run<T>> runsOver(List<T> sequence, T value, int limit) {
        return runsOver(sequence, value, limit, true);
    }

    /** Every run of {@code value} longer than {@code limit}. */
    public static <T> List<Run<T>> runsOver(List<T> sequence, T value, int limit, boolean cyclic) {
        Check.integer(limit, "limit", Bounds.atLeast(0));
        return select(sequence, cyclic).stream()
                .filter(run -> Objects.equals(run.value(), value) && run.length() > limit)
                .toList();
    }

    /** Rebuild the original sequence from its runs, for round-trip checking. */
    public static <T> List<T> expandRuns(List<Run<T>> list) {
        Check.array(list, "list");
        List<T> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += 1) {
            Run<T> run = list.get(i);
            Check.integer(run.length(), "list[" + i + "].length", Bounds.atLeast(1));
            for (int n = 0; n < run.length(); n += 1) {
                out.add(run.value());
            }
        }
        return out;
    }

    /** Compact run notation, {@code 1x4 0x2}, useful in a card deck listing. */
    public static String formatRuns(List<?> sequence) {
        return formatRuns(sequence, Style.COMPACT);
    }

    /** Run notation in either style. */
    public static String formatRuns(List<?> sequence, Style style) {
        return runs(sequence).stream()
                .map(r -> style == Style.COMPACT
                        ? Text.string(r.value()) + "x" + r.length()
                        : r.length() + " of " + Text.string(r.value()))
                .collect(Collectors.joining(style == Style.COMPACT ? " " : ", "));
    }
}
