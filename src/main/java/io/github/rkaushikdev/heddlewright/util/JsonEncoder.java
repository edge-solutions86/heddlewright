package io.github.rkaushikdev.heddlewright.util;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Turns Java values into {@link Json}.
 *
 * <p>A record becomes an object with one field per component, in declaration order, which is how
 * every result in this package reaches the service and the command line without a hand written
 * mapping per type.
 */
final class JsonEncoder {

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final Set<Object> open = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    private JsonEncoder() {}

    static Json encode(Object value) {
        return new JsonEncoder().convert(value);
    }

    private Json convert(Object value) {
        return switch (value) {
            case null -> Json.NULL;
            case Json json -> json;
            case Boolean bool -> bool ? Json.TRUE : Json.FALSE;
            case BigInteger big ->
                throw new InvalidArgumentException("canonical JSON cannot hold a bigint", JsonObject.EMPTY);
            case BigDecimal decimal -> new Json.Num(decimal.doubleValue());
            case Number number -> new Json.Num(number.doubleValue());
            case CharSequence text -> new Json.Str(text.toString());
            case Character character -> new Json.Str(character.toString());
            case Enum<?> member -> new Json.Str(member.toString());
            case Optional<?> optional -> optional.isPresent() ? convert(optional.get()) : Json.MISSING;
            case OptionalInt optional -> optional.isPresent() ? new Json.Num(optional.getAsInt()) : Json.MISSING;
            case OptionalLong optional -> optional.isPresent() ? new Json.Num(optional.getAsLong()) : Json.MISSING;
            case OptionalDouble optional -> optional.isPresent() ? new Json.Num(optional.getAsDouble()) : Json.MISSING;
            case Instant instant -> new Json.Str(ISO.format(instant));
            case LocalDate day -> new Json.Str(day.toString());
            default -> nested(value);
        };
    }

    private Json nested(Object value) {
        if (Check.isFunction(value)) {
            throw new InvalidArgumentException("canonical JSON cannot hold a function", JsonObject.EMPTY);
        }
        if (!open.add(value)) {
            throw new InvalidArgumentException("canonical JSON cannot hold a cycle", JsonObject.EMPTY);
        }
        try {
            return switch (value) {
                case Json.Writable writable -> writable.toJson();
                case Map<?, ?> map -> fromMap(map);
                case Iterable<?> items -> fromIterable(items);
                case Record record -> fromRecord(record);
                default -> {
                    if (value.getClass().isArray()) {
                        yield fromArray(value);
                    }
                    throw new InvalidArgumentException(
                            "canonical JSON cannot hold a " + value.getClass().getSimpleName(), JsonObject.EMPTY);
                }
            };
        } finally {
            open.remove(value);
        }
    }

    private JsonObject fromMap(Map<?, ?> map) {
        Map<String, Json> fields = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Json converted = convert(entry.getValue());
            if (!converted.isMissing()) {
                fields.put(String.valueOf(entry.getKey()), converted);
            }
        }
        return JsonObject.wrap(fields);
    }

    private JsonArray fromIterable(Iterable<?> items) {
        List<Json> converted = new ArrayList<>();
        for (Object item : items) {
            converted.add(convert(item));
        }
        return JsonArray.wrap(converted);
    }

    private JsonArray fromArray(Object array) {
        int length = Array.getLength(array);
        List<Json> converted = new ArrayList<>(length);
        for (int i = 0; i < length; i += 1) {
            converted.add(convert(Array.get(array, i)));
        }
        return JsonArray.wrap(converted);
    }

    private JsonObject fromRecord(Record record) {
        Map<String, Json> fields = new LinkedHashMap<>();
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            Object read;
            try {
                java.lang.reflect.Method accessor = component.getAccessor();
                accessor.setAccessible(true);
                read = accessor.invoke(record);
            } catch (IllegalAccessException | InvocationTargetException err) {
                throw new IllegalStateException("cannot read " + component.getName(), err);
            }
            Json converted = convert(read);
            if (!converted.isMissing()) {
                fields.put(component.getName(), converted);
            }
        }
        return JsonObject.wrap(fields);
    }
}
