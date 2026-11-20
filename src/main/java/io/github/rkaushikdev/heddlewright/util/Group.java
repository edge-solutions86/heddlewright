package io.github.rkaushikdev.heddlewright.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Grouping and totalling.
 *
 * <p>Six places in this package were writing the same twenty lines: build a map keyed on
 * something, add a few numbers into it, turn it back into a list and sort. They differed in two
 * ways that matter and no others. Either the keys are whatever the data happens to hold, and the
 * answer is ranked; or the keys are a fixed list and the answer has to include the ones that scored
 * nothing, because a report that silently drops an empty category is a report that cannot be read
 * as a total.
 *
 * <p>A tally row is a {@link JsonObject}: the key under the label the caller names, the member
 * count, and one total per field, which is the shape the reports print and the service sends.
 */
public final class Group {

    /**
     * What a tally groups on and adds up: the key, the label the key is written under, the fields
     * to total, the places they are rounded to, and the field to rank by, if any.
     */
    public record Tally<T>(
            BiFunction<? super T, Integer, ?> key,
            String label,
            Map<String, Function<? super T, ?>> fields,
            int places,
            Optional<String> sortBy) {

        /** Copies the fields, keeping their order. */
        public Tally {
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }

        /** Add a field to total. */
        public Tally<T> field(String name, Function<? super T, ?> read) {
            Map<String, Function<? super T, ?>> more = new LinkedHashMap<>(fields);
            more.put(name, read);
            return new Tally<>(key, label, more, places, sortBy);
        }

        /** Write the key under a different label. */
        public Tally<T> label(String name) {
            return new Tally<>(key, name, fields, places, sortBy);
        }

        /** Round the totals to a different number of places. */
        public Tally<T> places(int count) {
            return new Tally<>(key, label, fields, count, sortBy);
        }

        /** Rank the rows by a field, highest first. */
        public Tally<T> sortBy(String field) {
            return new Tally<>(key, label, fields, places, Optional.of(field));
        }
    }

    private Group() {}

    /** A tally keyed on {@code key}, labelled {@code key}, with no fields and three places. */
    public static <T> Tally<T> by(Function<? super T, ?> key) {
        return new Tally<>((item, index) -> key.apply(item), "key", Map.of(), 3, Optional.empty());
    }

    /** Group items by a key, keeping first-seen order. */
    public static <T, K> Map<K, List<T>> groupBy(List<T> items, Function<? super T, K> keyOf) {
        return groupBy(items, (item, index) -> keyOf.apply(item));
    }

    /** Group items by a key that may read the index, keeping first-seen order. */
    public static <T, K> Map<K, List<T>> groupBy(List<T> items, BiFunction<? super T, Integer, K> keyOf) {
        Check.array(items, "items");
        Map<K, List<T>> groups = new LinkedHashMap<>();
        for (int index = 0; index < items.size(); index += 1) {
            T item = items.get(index);
            groups.computeIfAbsent(keyOf.apply(item, index), key -> new ArrayList<>())
                    .add(item);
        }
        Map<K, List<T>> frozen = new LinkedHashMap<>();
        groups.forEach((key, members) -> frozen.put(key, List.copyOf(members)));
        return Collections.unmodifiableMap(frozen);
    }

    /** Total a few numeric fields per group; {@code count} is always added. */
    public static <T> List<JsonObject> tally(List<T> items, Tally<T> spec) {
        Check.nonEmptyString(spec.label(), "label");
        Map<Object, List<T>> groups =
                groupBy(items, (T item, Integer index) -> (Object) spec.key().apply(item, index));
        List<JsonObject> rows = new ArrayList<>();
        for (Map.Entry<Object, List<T>> group : groups.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(spec.label(), group.getKey());
            row.put("count", group.getValue().size());
            for (Map.Entry<String, Function<? super T, ?>> field : spec.fields().entrySet()) {
                double total = Numeric.sumBy(group.getValue(), field.getValue(), "members");
                row.put(field.getKey(), Numeric.roundTo(total, spec.places()));
            }
            rows.add((JsonObject) Json.of(row));
        }
        return spec.sortBy().isPresent()
                ? sortRows(rows, spec.label(), spec.sortBy().get())
                : List.copyOf(rows);
    }

    /**
     * The same, over a fixed set of keys, so a category that scored nothing is still in the answer
     * and in the order the list gave.
     */
    public static <T> List<JsonObject> tallyOver(List<?> keys, List<T> items, Tally<T> spec) {
        Check.array(keys, "keys", 1);
        Tally<T> unsorted = new Tally<>(spec.key(), spec.label(), spec.fields(), spec.places(), Optional.empty());
        Map<Json, JsonObject> byKey = new LinkedHashMap<>();
        for (JsonObject row : tally(items, unsorted)) {
            byKey.put(row.get(spec.label()), row);
        }
        List<JsonObject> out = new ArrayList<>(keys.size());
        for (Object value : keys) {
            JsonObject row = byKey.get(Json.of(value));
            if (row != null) {
                out.add(row);
                continue;
            }
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put(spec.label(), value);
            empty.put("count", 0);
            for (String name : spec.fields().keySet()) {
                empty.put(name, 0);
            }
            out.add((JsonObject) Json.of(empty));
        }
        return List.copyOf(out);
    }

    /** The rows as they are, when no field is named to sort by. */
    public static List<JsonObject> sortRows(List<JsonObject> rows, String label) {
        return rows;
    }

    /** Sort rows by a named field descending, breaking a tie on the label. */
    public static List<JsonObject> sortRows(List<JsonObject> rows, String label, String sortBy) {
        List<JsonObject> sorted = new ArrayList<>(rows);
        sorted.sort((a, b) -> {
            double difference = b.get(sortBy).asDouble() - a.get(sortBy).asDouble();
            if (difference != 0) {
                return difference > 0 ? 1 : -1;
            }
            return Collation.compare(Text.string(a.get(label)), Text.string(b.get(label)));
        });
        return List.copyOf(sorted);
    }

    /** The row that scored highest on a field, or empty when there are none. */
    public static Optional<JsonObject> topOf(List<JsonObject> rows, String field) {
        Check.array(rows, "rows");
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        JsonObject best = rows.get(0);
        for (JsonObject row : rows) {
            if (row.get(field).asDouble() > best.get(field).asDouble()) {
                best = row;
            }
        }
        return Optional.of(best);
    }
}
