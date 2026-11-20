package io.github.rkaushikdev.heddlewright.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One-based position work.
 *
 * <p>Weavers count ends, picks, shafts and treadles from one, and lists hold them from zero. The
 * same three shapes kept being written out by hand at every call site: positions where a test
 * holds, positions grouped by an identical value, and a bit row read back as a list of set
 * positions. They live here, so the conversion happens in one place and is tested once.
 */
public final class Indexing {

    /** Each position's label, the representative value behind each label, and how many there are. */
    public record Labelled<T>(List<Integer> labels, List<T> representatives, int count) {
        /** Copies the lists. */
        public Labelled {
            labels = List.copyOf(labels);
            representatives = List.copyOf(representatives);
        }
    }

    private Indexing() {}

    /** The 1-based positions where {@code test} holds. */
    public static <T> List<Integer> positionsWhere(List<T> list, Predicate<? super T> test) {
        return positionsWhere(list, (value, index) -> test.test(value));
    }

    /** The 1-based positions where {@code test} holds, the test seeing the zero-based index too. */
    public static <T> List<Integer> positionsWhere(List<T> list, BiPredicate<? super T, Integer> test) {
        Check.array(list, "list");
        List<Integer> out = new ArrayList<>();
        for (int index = 0; index < list.size(); index += 1) {
            if (test.test(list.get(index), index)) {
                out.add(index + 1);
            }
        }
        return List.copyOf(out);
    }

    /** The 1-based positions holding exactly {@code value}. */
    public static <T> List<Integer> positionsOf(List<T> list, Object value) {
        return positionsWhere(list, candidate -> Objects.equals(candidate, value));
    }

    /** The 1-based positions of the set bits in a row of 0s and 1s. */
    public static List<Integer> setPositions(List<Integer> row) {
        return positionsOf(row, 1);
    }

    /** Groups of 1-based positions whose entries have the same JSON form, keeping only real groups. */
    public static <T> List<List<Integer>> duplicateGroups(List<T> list) {
        return duplicateGroups(list, value -> Json.stringify(value));
    }

    /**
     * Groups of 1-based positions whose entries share a key, keeping only the groups with more than
     * one member and preserving first-seen order.
     */
    public static <T> List<List<Integer>> duplicateGroups(List<T> list, Function<? super T, ?> keyOf) {
        return duplicateGroups(list, (value, index) -> keyOf.apply(value));
    }

    /** {@link #duplicateGroups(List, Function)} with the index handed to the key function. */
    public static <T> List<List<Integer>> duplicateGroups(List<T> list, BiFunction<? super T, Integer, ?> keyOf) {
        Check.array(list, "list");
        Map<Object, List<Integer>> groups = new LinkedHashMap<>();
        for (int index = 0; index < list.size(); index += 1) {
            groups.computeIfAbsent(keyOf.apply(list.get(index), index), key -> new ArrayList<>())
                    .add(index + 1);
        }
        return groups.values().stream()
                .filter(group -> group.size() > 1)
                .map(List::copyOf)
                .toList();
    }

    /** Number each distinct entry from one in first-seen order, by its JSON form. */
    public static <T> Labelled<T> labelDistinct(List<T> list) {
        return labelDistinct(list, value -> Json.stringify(value));
    }

    /**
     * Number each distinct entry from one in first-seen order, returning the label per position and
     * the representative value for each label.
     */
    public static <T> Labelled<T> labelDistinct(List<T> list, Function<? super T, ?> keyOf) {
        Check.array(list, "list");
        Map<Object, Integer> seen = new LinkedHashMap<>();
        List<T> representatives = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        for (T value : list) {
            Object key = keyOf.apply(value);
            Integer label = seen.get(key);
            if (label == null) {
                label = representatives.size() + 1;
                seen.put(key, label);
                representatives.add(value);
            }
            labels.add(label);
        }
        return new Labelled<>(labels, representatives, representatives.size());
    }
}
