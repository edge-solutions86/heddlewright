package io.github.rkaushikdev.heddlewright.draft;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Cycle;
import io.github.rkaushikdev.heddlewright.util.Ids;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * When are two drafts the same draft?
 *
 * <p>Almost never by their fields. Renumber the shafts and every list changes while the cloth does
 * not; start the threading at a different end and the same thing happens. The only honest answer
 * is to weave both and compare the cloth, and then to say which of those two things was different,
 * because a draft that weaves the same cloth on fewer shafts is a better draft.
 */
public final class Equivalence {

    /** What differs between two drafts, and whether they weave the same cloth. */
    public record Comparison(String verdict, boolean cloth) {}

    private Equivalence() {}

    /**
     * A key for the cloth a draft makes, taken round the repeat both ways so a draft cut at a
     * different end matches.
     */
    public static String clothKey(Draft draft) {
        Matrix cloth = Drawdown.drawdownFromDraft(draft);
        List<String> rows = Cycle.canonicalRotation(
                cloth.allRows().stream().map(Equivalence::bits).toList());
        List<String> columns = Cycle.canonicalRotation(
                cloth.columns().stream().map(Equivalence::bits).toList());
        String byRow = String.join("|", rows);
        String byColumn = String.join("|", columns);
        return Ids.stableHash(byRow.compareTo(byColumn) < 0 ? byRow : byColumn);
    }

    private static String bits(List<Integer> line) {
        StringBuilder out = new StringBuilder(line.size());
        for (int cell : line) {
            out.append(cell);
        }
        return out.toString();
    }

    /** Two drafts weave the same cloth, however their shafts are numbered. */
    public static boolean sameCloth(Draft left, Draft right) {
        return clothKey(left).equals(clothKey(right));
    }

    /** Two drafts are written the same way, field for field. */
    public static boolean sameWriting(Draft left, Draft right) {
        return left.shafts() == right.shafts()
                && left.treadles() == right.treadles()
                && left.threading().equals(right.threading())
                && written(left.tieup()).equals(written(right.tieup()))
                && written(left.treadling()).equals(written(right.treadling()));
    }

    private static String written(List<List<Integer>> lists) {
        return lists.stream()
                .map(entry -> entry.stream().map(String::valueOf).collect(Collectors.joining(".")))
                .collect(Collectors.joining(","));
    }

    /**
     * The permutation that turns one threading into another, or empty when no renumbering of the
     * shafts will do it.
     */
    public static Optional<Map<Integer, Integer>> shaftPermutation(List<Integer> left, List<Integer> right) {
        Check.array(left, "left", 1);
        Check.array(right, "right", 1);
        if (left.size() != right.size()) {
            return Optional.empty();
        }
        Map<Integer, Integer> mapping = new LinkedHashMap<>();
        Set<Integer> claimed = new HashSet<>();
        for (int index = 0; index < left.size(); index += 1) {
            Integer shaft = left.get(index);
            Integer wanted = right.get(index);
            if (mapping.containsKey(shaft)) {
                if (!mapping.get(shaft).equals(wanted)) {
                    return Optional.empty();
                }
                continue;
            }
            if (!claimed.add(wanted)) {
                return Optional.empty();
            }
            mapping.put(shaft, wanted);
        }
        return Optional.of(java.util.Collections.unmodifiableMap(mapping));
    }

    /** True when two threadings differ only in how the shafts are numbered. */
    public static boolean sameThreadingUpToNumbering(List<Integer> left, List<Integer> right) {
        return shaftPermutation(left, right).isPresent();
    }

    /** Compare two drafts and say what actually differs: the cloth, the writing, or only the numbering. */
    public static Comparison compareDrafts(Draft left, Draft right) {
        if (sameWriting(left, right)) {
            return new Comparison("identical", true);
        }
        if (!sameCloth(left, right)) {
            return new Comparison("different cloth", false);
        }
        if (sameThreadingUpToNumbering(left.threading(), right.threading())) {
            return new Comparison("same cloth, shafts renumbered", true);
        }
        return new Comparison("same cloth, drafted differently", true);
    }

    /** How many shafts a draft actually needs, whatever it says it has. */
    public static int shaftsNeeded(Draft draft) {
        return new HashSet<>(draft.threading()).size();
    }

    /**
     * The better of two drafts weaving the same cloth: {@code left} or {@code right} on fewer
     * shafts, then fewer treadles, or {@code neither}. Two drafts weaving different cloths cannot be
     * ranked at all.
     */
    public static Optional<String> simpler(Draft left, Draft right) {
        if (!sameCloth(left, right)) {
            return Optional.empty();
        }
        int leftShafts = shaftsNeeded(left);
        int rightShafts = shaftsNeeded(right);
        if (leftShafts != rightShafts) {
            return Optional.of(leftShafts < rightShafts ? "left" : "right");
        }
        if (left.treadles() != right.treadles()) {
            return Optional.of(left.treadles() < right.treadles() ? "left" : "right");
        }
        return Optional.of("neither");
    }

    /** Group drafts by the cloth they weave, as lists of their names. */
    public static List<List<String>> groupByCloth(List<Draft> drafts) {
        Check.array(drafts, "drafts", 1);
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (Draft draft : drafts) {
            groups.computeIfAbsent(clothKey(draft), key -> new ArrayList<>()).add(draft.name());
        }
        return groups.values().stream().map(List::copyOf).toList();
    }

    /** How many distinct cloths a pile of drafts actually holds. */
    public static int distinctCloths(List<Draft> drafts) {
        Check.positiveInteger(drafts.size(), "drafts");
        return groupByCloth(drafts).size();
    }
}
