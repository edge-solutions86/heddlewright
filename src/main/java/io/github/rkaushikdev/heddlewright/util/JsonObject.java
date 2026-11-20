package io.github.rkaushikdev.heddlewright.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A JSON object: an immutable, ordered map from keys to values.
 *
 * <p>Keys keep the order they were added in, except that keys reading as array indices (0 up to
 * 2^32 - 2, written without leading zeros) come first in ascending order. That is the order a
 * record is written out in, so the same record always prints the same text.
 *
 * <p>Equality ignores key order, as a deep comparison of two plain records does.
 */
public final class JsonObject implements Json, Iterable<Map.Entry<String, Json>> {

    /** The empty object. */
    public static final JsonObject EMPTY = new JsonObject(Map.of());

    private final Map<String, Json> fields;

    private JsonObject(Map<String, Json> fields) {
        this.fields = fields;
    }

    /** An object over a map of already converted values, in the key order described above. */
    static JsonObject wrap(Map<String, Json> source) {
        if (source.isEmpty()) {
            return EMPTY;
        }
        Map<String, Json> ordered = new LinkedHashMap<>();
        List<String> indices = new ArrayList<>();
        for (String key : source.keySet()) {
            if (isIndex(key)) {
                indices.add(key);
            }
        }
        indices.sort((a, b) -> Long.compare(Long.parseLong(a), Long.parseLong(b)));
        for (String key : indices) {
            ordered.put(key, source.get(key));
        }
        for (Map.Entry<String, Json> entry : source.entrySet()) {
            if (!isIndex(entry.getKey())) {
                ordered.put(entry.getKey(), entry.getValue());
            }
        }
        return new JsonObject(Collections.unmodifiableMap(ordered));
    }

    /** An object from alternating keys and values. A {@link Json#MISSING} value leaves its key out. */
    public static JsonObject of(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("an object needs a value for every key");
        }
        Map<String, Json> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            Json value = Json.of(keysAndValues[i + 1]);
            String key = String.valueOf(keysAndValues[i]);
            if (value.isMissing()) {
                map.remove(key);
            } else {
                map.put(key, value);
            }
        }
        return wrap(map);
    }

    private static boolean isIndex(String key) {
        if (key.isEmpty() || key.length() > 10) {
            return false;
        }
        for (int i = 0; i < key.length(); i += 1) {
            if (key.charAt(i) < '0' || key.charAt(i) > '9') {
                return false;
            }
        }
        if (key.length() > 1 && key.charAt(0) == '0') {
            return false;
        }
        return Long.parseLong(key) < 4_294_967_295L;
    }

    @Override
    public Json get(String key) {
        Json found = fields.get(key);
        return found == null ? Json.MISSING : found;
    }

    /** Whether the object holds the key at all, null or not. */
    public boolean has(String key) {
        return fields.containsKey(key);
    }

    /** The keys, in order. */
    public List<String> keys() {
        return List.copyOf(fields.keySet());
    }

    /** The entries, in order. */
    public Set<Map.Entry<String, Json>> entries() {
        return fields.entrySet();
    }

    /** The fields as a read-only map, in order. */
    public Map<String, Json> asMap() {
        return fields;
    }

    /** How many keys the object holds. */
    public int size() {
        return fields.size();
    }

    /** Whether the object holds no keys. */
    public boolean isEmpty() {
        return fields.isEmpty();
    }

    /** A copy with one field set, or removed when the value is {@link Json#MISSING}. */
    public JsonObject with(String key, Object value) {
        Map<String, Json> copy = new LinkedHashMap<>(fields);
        Json converted = Json.of(value);
        if (converted.isMissing()) {
            copy.remove(key);
        } else {
            copy.put(key, converted);
        }
        return wrap(copy);
    }

    /** A copy without some fields. */
    public JsonObject without(String... keys) {
        Map<String, Json> copy = new LinkedHashMap<>(fields);
        for (String key : keys) {
            copy.remove(key);
        }
        return wrap(copy);
    }

    /** A copy with another object's fields laid over this one's, as an object spread does. */
    public JsonObject merge(JsonObject other) {
        Map<String, Json> copy = new LinkedHashMap<>(fields);
        copy.putAll(other.fields);
        return wrap(copy);
    }

    /** A copy with alternating keys and values laid over this one's fields. */
    public JsonObject with(Object... keysAndValues) {
        JsonObject result = this;
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            result = result.with(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return result;
    }

    @Override
    public java.util.Iterator<Map.Entry<String, Json>> iterator() {
        return fields.entrySet().iterator();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JsonObject object && fields.equals(object.fields);
    }

    @Override
    public int hashCode() {
        return fields.hashCode();
    }

    /** The compact JSON text. */
    @Override
    public String toString() {
        return JsonWriter.write(this, 0);
    }
}
