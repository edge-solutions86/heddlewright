package io.github.rkaushikdev.heddlewright.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/** A JSON array: an immutable list of values. */
public final class JsonArray implements Json, Iterable<Json> {

    /** The empty array. */
    public static final JsonArray EMPTY = new JsonArray(List.of());

    private final List<Json> items;

    private JsonArray(List<Json> items) {
        this.items = items;
    }

    /** An array over already converted values. An absent value is written as null, as a hole is. */
    static JsonArray wrap(List<Json> source) {
        List<Json> copy = new ArrayList<>(source.size());
        for (Json item : source) {
            copy.add(item.isMissing() ? Json.NULL : item);
        }
        return new JsonArray(List.copyOf(copy));
    }

    /** An array of the converted items. */
    public static JsonArray of(Object... values) {
        List<Json> converted = new ArrayList<>(values.length);
        for (Object value : values) {
            converted.add(Json.of(value));
        }
        return wrap(converted);
    }

    /** An array of the converted items of a list. */
    public static JsonArray from(Iterable<?> values) {
        List<Json> converted = new ArrayList<>();
        for (Object value : values) {
            converted.add(Json.of(value));
        }
        return wrap(converted);
    }

    @Override
    public Json at(int index) {
        return index >= 0 && index < items.size() ? items.get(index) : Json.MISSING;
    }

    /** How many items the array holds. */
    public int size() {
        return items.size();
    }

    /** Whether the array is empty. */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** The items, read-only. */
    public List<Json> items() {
        return items;
    }

    /** The items as a stream. */
    public Stream<Json> stream() {
        return items.stream();
    }

    @Override
    public Iterator<Json> iterator() {
        return items.iterator();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JsonArray array && items.equals(array.items);
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }

    /** The compact JSON text. */
    @Override
    public String toString() {
        return JsonWriter.write(this, 0);
    }
}
