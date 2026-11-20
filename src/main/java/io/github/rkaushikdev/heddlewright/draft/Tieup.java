package io.github.rkaushikdev.heddlewright.draft;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Indexing;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The tie-up: which shafts each treadle lifts.
 *
 * <p>Held as a list of sorted, deduplicated shaft numbers per treadle, because that is how a weaver
 * writes it on paper, with a matrix view available for the drawdown to consume.
 */
public final class Tieup {

    /** A tie-up that opens a shed, with the treadles it leaves untied. */
    public record Usable(List<List<Integer>> tieup, List<Integer> untied) {
        /** Copies the lists. */
        public Usable {
            tieup = List.copyOf(tieup);
            untied = List.copyOf(untied);
        }
    }

    private Tieup() {}

    /** Check a tie-up against a shaft count, returning a normalised copy. */
    public static List<List<Integer>> normaliseTieup(Object tieup, int shafts) {
        List<Object> entries = Check.array(tieup, "tieup", 1);
        Check.positiveInteger(shafts, "shafts");
        List<List<Integer>> out = new ArrayList<>(entries.size());
        for (int t = 0; t < entries.size(); t += 1) {
            List<Object> entry = Check.array(entries.get(t), "tieup[" + t + "]");
            out.add(sortedUnique(entry, "tieup[" + t + "]", shafts));
        }
        return List.copyOf(out);
    }

    /** Sort and deduplicate a list of numbers, checking each sits between one and {@code max}. */
    static List<Integer> sortedUnique(List<Object> entry, String field, int max) {
        Set<Object> seen = new HashSet<>();
        List<Object> unique = new ArrayList<>();
        for (Object value : entry) {
            OptionalDouble number = numberOf(value);
            if (seen.add(number.isPresent() ? (Object) number.getAsDouble() : value)) {
                unique.add(value);
            }
        }
        unique.sort(Comparator.comparingDouble(value -> numberOf(value).orElse(Double.MAX_VALUE)));
        List<Integer> out = new ArrayList<>(unique.size());
        for (int i = 0; i < unique.size(); i += 1) {
            out.add(Check.integer(unique.get(i), field + "[" + i + "]", Bounds.between(1, max)));
        }
        return List.copyOf(out);
    }

    private static OptionalDouble numberOf(Object value) {
        if (value instanceof Json.Num number) {
            return OptionalDouble.of(number.value());
        }
        return value instanceof Number plain ? OptionalDouble.of(plain.doubleValue()) : OptionalDouble.empty();
    }

    /** A tie-up with no shaft tied to any treadle would open no shed at all. */
    public static Usable assertTieupUsable(Object tieup, int shafts) {
        List<List<Integer>> normalised = normaliseTieup(tieup, shafts);
        List<Integer> empty = Indexing.positionsWhere(normalised, List::isEmpty);
        if (empty.size() == normalised.size()) {
            throw new DraftException(
                    "every treadle is untied, so no shed can open", Json.obj("treadles", normalised.size()));
        }
        return new Usable(normalised, empty);
    }

    /** The tie-up as a treadle-by-shaft binary matrix. */
    public static Matrix tieupMatrix(Object tieup, int shafts) {
        List<List<Integer>> normalised = normaliseTieup(tieup, shafts);
        int[][] cells = new int[normalised.size()][shafts];
        for (int t = 0; t < normalised.size(); t += 1) {
            for (int shaft : normalised.get(t)) {
                cells[t][shaft - 1] = 1;
            }
        }
        return Matrix.of(cells);
    }

    /** Read a treadle-by-shaft matrix back into the list form. */
    public static List<List<Integer>> tieupFromMatrix(Matrix matrix) {
        return matrix.allRows().stream().map(Indexing::setPositions).toList();
    }

    /** The shaft-by-treadle view, which is how a countermarch loom is corded. */
    public static Matrix shaftMatrix(Object tieup, int shafts) {
        return tieupMatrix(tieup, shafts).transpose();
    }

    /** Convert between rising and sinking sheds: the same cloth needs the complementary tie-up. */
    public static List<List<Integer>> convertShed(Object tieup, int shafts, Shed from, Shed to) {
        List<List<Integer>> normalised = normaliseTieup(tieup, shafts);
        if (from == to) {
            return normalised;
        }
        List<List<Integer>> out = new ArrayList<>(normalised.size());
        for (List<Integer> entry : normalised) {
            List<Integer> complement = new ArrayList<>();
            for (int shaft = 1; shaft <= shafts; shaft += 1) {
                if (!entry.contains(shaft)) {
                    complement.add(shaft);
                }
            }
            out.add(List.copyOf(complement));
        }
        return List.copyOf(out);
    }

    /** Treadles tied to nothing, which a weaver has left spare on the lamms. */
    public static List<Integer> untiedTreadles(Object tieup, int shafts) {
        return Indexing.positionsWhere(normaliseTieup(tieup, shafts), List::isEmpty);
    }

    /** Treadles tied identically, which are interchangeable at the loom. */
    public static List<List<Integer>> duplicateTreadles(Object tieup, int shafts) {
        return Indexing.duplicateGroups(
                normaliseTieup(tieup, shafts),
                (List<Integer> entry) -> entry.stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    /** How many shafts each treadle lifts, which is the weight on the foot. */
    public static List<Integer> treadleLoad(Object tieup, int shafts) {
        return normaliseTieup(tieup, shafts).stream().map(List::size).toList();
    }

    /**
     * A standard twill tie-up: treadle t lifts {@code raise} consecutive shafts starting at t. The
     * 2/2 twill tie-up over four shafts is the canonical case.
     */
    public static List<List<Integer>> twillTieup(int shafts, int raise) {
        Check.positiveInteger(shafts, "shafts");
        Check.integer(raise, "raise", Bounds.between(1, shafts - 1));
        List<List<Integer>> out = new ArrayList<>(shafts);
        for (int t = 0; t < shafts; t += 1) {
            List<Integer> lifted = new ArrayList<>(raise);
            for (int r = 0; r < raise; r += 1) {
                lifted.add(((t + r) % shafts) + 1);
            }
            lifted.sort(null);
            out.add(List.copyOf(lifted));
        }
        return List.copyOf(out);
    }

    /** The tie-up in which treadle t lifts only shaft t, the direct tie of a dobby. */
    public static List<List<Integer>> directTieup(int shafts) {
        Check.positiveInteger(shafts, "shafts");
        List<List<Integer>> out = new ArrayList<>(shafts);
        for (int t = 0; t < shafts; t += 1) {
            out.add(List.of(t + 1));
        }
        return List.copyOf(out);
    }
}
