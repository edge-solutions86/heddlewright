package io.github.rkaushikdev.heddlewright.util;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Argument checks.
 *
 * <p>Every one of these either returns the value it was given, so it can sit inline in a
 * constructor, or throws {@link InvalidArgumentException} naming the field and what it actually
 * got. The checks take any value, because the records they guard arrive as JSON from a body, a
 * file or the journal, where a count can turn out to be a string; the typed overloads are for
 * values the compiler has already vouched for.
 */
public final class Check {

    private Check() {}

    /** Refuse null and absent values; anything else passes, including zero and the empty string. */
    public static <T> T required(T value, String field) {
        if (value == null || value instanceof Json json && json.isNullish()) {
            boolean missing = value instanceof Json json && json.isMissing();
            throw new InvalidArgumentException(
                    field + " is required", Json.obj("field", field, "got", missing ? "undefined" : "null"));
        }
        return value;
    }

    /** Whether a value is a number with no fractional part. */
    public static boolean isInteger(Object value) {
        Double number = numeric(value);
        return number != null && Double.isFinite(number) && number == Math.rint(number);
    }

    /** An integer of any size. */
    public static int integer(Object value, String field) {
        return integer(value, field, Bounds.NONE);
    }

    /** An integer inside inclusive bounds. */
    public static int integer(Object value, String field, Bounds bounds) {
        if (!isInteger(value)) {
            throw new InvalidArgumentException(
                    field + " must be an integer", Json.obj("field", field, "got", describe(value)));
        }
        double number = numeric(value);
        inside(number, field, bounds);
        return (int) number;
    }

    /** An integer the compiler has already checked. */
    public static int integer(int value, String field) {
        return value;
    }

    /** An integer the compiler has already checked, inside inclusive bounds. */
    public static int integer(int value, String field, Bounds bounds) {
        inside(value, field, bounds);
        return value;
    }

    /** An integer of at least one. */
    public static int positiveInteger(Object value, String field) {
        return integer(value, field, Bounds.atLeast(1));
    }

    /** An integer of at least one. */
    public static int positiveInteger(int value, String field) {
        return integer(value, field, Bounds.atLeast(1));
    }

    /** A finite number. */
    public static double number(Object value, String field) {
        return number(value, field, Bounds.NONE);
    }

    /** A finite number inside inclusive bounds. */
    public static double number(Object value, String field, Bounds bounds) {
        Double number = numeric(value);
        if (number == null) {
            throw new InvalidArgumentException(
                    field + " must be a finite number", Json.obj("field", field, "got", describe(value)));
        }
        return number(number.doubleValue(), field, bounds);
    }

    /** A finite number the compiler has already typed, inside inclusive bounds. */
    public static double number(double value, String field, Bounds bounds) {
        if (!Double.isFinite(value)) {
            throw new InvalidArgumentException(
                    field + " must be a finite number", Json.obj("field", field, "got", describe(value)));
        }
        inside(value, field, bounds);
        return value;
    }

    /** A finite number above zero. */
    public static double positiveNumber(Object value, String field) {
        double number = number(value, field);
        if (number <= 0) {
            throw new InvalidArgumentException(
                    field + " must be greater than zero", Json.obj("field", field, "got", number));
        }
        return number;
    }

    /** A finite number above zero. */
    public static double positiveNumber(double value, String field) {
        return positiveNumber(Double.valueOf(value), field);
    }

    /** A string with something other than white space in it, returned untrimmed. */
    public static String nonEmptyString(Object value, String field) {
        String text = value instanceof Json.Str json ? json.value() : value instanceof String plain ? plain : null;
        if (text == null || Text.trim(text).isEmpty()) {
            throw new InvalidArgumentException(
                    field + " must be a non-empty string", Json.obj("field", field, "got", describe(value)));
        }
        return text;
    }

    /** A value that is one of an allowed list, compared as JavaScript's {@code includes} would. */
    public static <T> T oneOf(T value, String field, Collection<?> allowed) {
        List<?> list = List.copyOf(allowed);
        Object plain = value instanceof Json json ? unwrap(json) : value;
        for (Object candidate : list) {
            if (sameValue(candidate, plain)) {
                return value;
            }
        }
        throw notOneOf(value, field, list);
    }

    /**
     * The member of an enum whose text is {@code value}, refusing anything else the way
     * {@link #oneOf} does. Enums in this package print as the word a weaver writes.
     */
    public static <E extends Enum<E>> E member(Object value, String field, E[] members) {
        Object plain = value instanceof Json json ? unwrap(json) : value;
        for (E member : members) {
            if (member == plain || member.toString().equals(plain)) {
                return member;
            }
        }
        throw notOneOf(
                value, field, java.util.Arrays.stream(members).map(E::toString).toList());
    }

    private static InvalidArgumentException notOneOf(Object value, String field, List<?> list) {
        return new InvalidArgumentException(
                field + " must be one of "
                        + String.join(", ", list.stream().map(Text::string).toList()),
                Json.obj("field", field, "got", describe(value), "allowed", list));
    }

    /** A list, of any length. */
    public static List<Object> array(Object value, String field) {
        return array(value, field, 0, Integer.MAX_VALUE);
    }

    /** A list holding at least {@code min} entries. */
    public static List<Object> array(Object value, String field, int min) {
        return array(value, field, min, Integer.MAX_VALUE);
    }

    /** A list holding between {@code min} and {@code max} entries. */
    public static List<Object> array(Object value, String field, int min, int max) {
        List<Object> list = listOf(value);
        if (list == null) {
            throw new InvalidArgumentException(
                    field + " must be an array", Json.obj("field", field, "got", describe(value)));
        }
        if (list.size() < min) {
            throw new InvalidArgumentException(
                    field + " needs at least " + min + " entries",
                    Json.obj("field", field, "got", list.size(), "min", min));
        }
        if (list.size() > max) {
            throw new InvalidArgumentException(
                    field + " takes at most " + max + " entries",
                    Json.obj("field", field, "got", list.size(), "max", max));
        }
        return list;
    }

    /** An object: a JSON object or a map, never an array and never null. */
    public static JsonObject plainObject(Object value, String field) {
        if (value instanceof JsonObject object) {
            return object;
        }
        if (value instanceof Map<?, ?> map) {
            return (JsonObject) Json.of(map);
        }
        throw new InvalidArgumentException(
                field + " must be an object", Json.obj("field", field, "got", describe(value)));
    }

    /** A short, safe description of any value, for error detail. */
    public static String describe(Object value) {
        if (value instanceof Json json) {
            return switch (json) {
                case Json.Missing ignored -> "undefined";
                case Json.Null ignored -> "null";
                case JsonArray array -> "array(" + array.size() + ")";
                case Json.Str text -> describe(text.value());
                case Json.Num number -> Text.number(number.value());
                case Json.Bool bool -> String.valueOf(bool.value());
                case JsonObject ignored -> "object";
            };
        }
        if (value == null) {
            return "null";
        }
        List<Object> list = listOf(value);
        if (list != null) {
            return "array(" + list.size() + ")";
        }
        if (value instanceof String text) {
            return text.length() > 24 ? "string(" + text.length() + ")" : "\"" + text + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return Text.string(value);
        }
        return typeOf(value);
    }

    /** JavaScript's {@code typeof} for a value. */
    public static String typeOf(Object value) {
        if (value instanceof Json json) {
            return switch (json) {
                case Json.Missing ignored -> "undefined";
                case Json.Num ignored -> "number";
                case Json.Str ignored -> "string";
                case Json.Bool ignored -> "boolean";
                default -> "object";
            };
        }
        if (value instanceof BigInteger) {
            return "bigint";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof String || value instanceof Character) {
            return "string";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        return isFunction(value) ? "function" : "object";
    }

    /** Whether a value is a lambda or a method reference rather than data. */
    public static boolean isFunction(Object value) {
        if (value == null) {
            return false;
        }
        Class<?> type = value.getClass();
        return type.isSynthetic() || type.isHidden() || type.getName().contains("$$Lambda");
    }

    private static void inside(double value, String field, Bounds bounds) {
        if (bounds.min().isPresent() && value < bounds.min().getAsDouble()) {
            double min = bounds.min().getAsDouble();
            throw new InvalidArgumentException(
                    field + " must be at least " + Text.number(min),
                    Json.obj("field", field, "got", value, "min", min));
        }
        if (bounds.max().isPresent() && value > bounds.max().getAsDouble()) {
            double max = bounds.max().getAsDouble();
            throw new InvalidArgumentException(
                    field + " must be at most " + Text.number(max), Json.obj("field", field, "got", value, "max", max));
        }
    }

    private static Double numeric(Object value) {
        if (value instanceof Json.Num number) {
            return Double.isFinite(number.value()) ? number.value() : null;
        }
        if (value instanceof Number number && !(value instanceof BigInteger)) {
            double plain = number.doubleValue();
            return Double.isFinite(plain) ? plain : null;
        }
        return null;
    }

    private static List<Object> listOf(Object value) {
        if (value instanceof JsonArray array) {
            return new ArrayList<>(array.items());
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> out = new ArrayList<>(length);
            for (int i = 0; i < length; i += 1) {
                out.add(Array.get(value, i));
            }
            return out;
        }
        return null;
    }

    private static Object unwrap(Json json) {
        return switch (json) {
            case Json.Str text -> text.value();
            case Json.Num number -> number.value();
            case Json.Bool bool -> bool.value();
            default -> json;
        };
    }

    private static boolean sameValue(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return x.doubleValue() == y.doubleValue();
        }
        return a == null ? b == null : a.equals(b);
    }
}
