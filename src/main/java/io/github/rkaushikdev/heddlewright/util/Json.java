package io.github.rkaushikdev.heddlewright.util;

import java.util.Optional;

/**
 * A JSON value, and canonical JSON.
 *
 * <p>Plain records are what heddlewright stores, sends over http and reads from files, and this is
 * their type: null, a boolean, a number, a string, an array or an object, plus {@link #MISSING}
 * for a field that is not there at all, which a reader has to tell apart from one that is null.
 * Every value is immutable, so a record handed out of the store cannot be edited in place.
 *
 * <p>Two records that hold the same information have to serialise to the same bytes, otherwise a
 * content id changes because a key moved and the journal starts reporting differences that are
 * not there. {@link #canonicalStringify} is that form: object keys sorted, arrays left in order,
 * no whitespace.
 */
public sealed interface Json permits Json.Null, Json.Bool, Json.Num, Json.Str, Json.Missing, JsonArray, JsonObject {

    /** JSON null. */
    Json NULL = new Null();

    /** A field that is not there at all. Never stored inside an object or an array. */
    Json MISSING = new Missing();

    /** JSON {@code true}. */
    Json TRUE = new Bool(true);

    /** JSON {@code false}. */
    Json FALSE = new Bool(false);

    /** The null value. */
    record Null() implements Json {
        @Override
        public String toString() {
            return "null";
        }
    }

    /** An absent value, the reading of a key an object does not hold. */
    record Missing() implements Json {
        @Override
        public String toString() {
            return "undefined";
        }
    }

    /** A boolean. */
    record Bool(boolean value) implements Json {
        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    /** A number. Every number is a double, as it is in the format; negative zero folds to zero. */
    record Num(double value) implements Json {
        /** Fold negative zero, so a rounded figure cannot fork an id. */
        public Num {
            value = value == 0 ? 0 : value;
        }

        @Override
        public String toString() {
            return Text.number(value);
        }
    }

    /** A string. */
    record Str(String value) implements Json {
        @Override
        public String toString() {
            return JsonWriter.quote(value);
        }
    }

    /** Whether this is the absent value. */
    default boolean isMissing() {
        return this instanceof Missing;
    }

    /** Whether this is null or absent, the two readings that leave a field to its default. */
    default boolean isNullish() {
        return this instanceof Missing || this instanceof Null;
    }

    /** The value of a field when this is an object, or {@link #MISSING} for anything else. */
    default Json get(String key) {
        return MISSING;
    }

    /** The value at an index when this is an array, or {@link #MISSING} for anything else. */
    default Json at(int index) {
        return MISSING;
    }

    /** This value when it is present, or empty when it is null or absent. */
    default Optional<Json> present() {
        return isNullish() ? Optional.empty() : Optional.of(this);
    }

    /** The string inside a string value. */
    default String asText() {
        if (this instanceof Str text) {
            return text.value();
        }
        throw new IllegalStateException("not a string: " + this);
    }

    /** The double inside a number value. */
    default double asDouble() {
        if (this instanceof Num number) {
            return number.value();
        }
        throw new IllegalStateException("not a number: " + this);
    }

    /** The number inside a number value, as an int. */
    default int asInt() {
        return (int) asDouble();
    }

    /** The boolean inside a boolean value. */
    default boolean asBoolean() {
        if (this instanceof Bool bool) {
            return bool.value();
        }
        throw new IllegalStateException("not a boolean: " + this);
    }

    /** Whether this is a number. */
    default boolean isNumber() {
        return this instanceof Num;
    }

    /** Whether this is a string. */
    default boolean isText() {
        return this instanceof Str;
    }

    /**
     * Convert a Java value to JSON: maps, records and {@link Writable}s become objects, collections
     * and arrays become arrays, an empty {@code Optional} becomes {@link #MISSING}, and an enum
     * becomes its {@code toString()}.
     */
    static Json of(Object value) {
        return JsonEncoder.encode(value);
    }

    /** A number value. */
    static Json of(double value) {
        return new Num(value);
    }

    /** A string value. */
    static Json of(String value) {
        return value == null ? NULL : new Str(value);
    }

    /** An object from alternating keys and values. A {@link #MISSING} value leaves its key out. */
    static JsonObject obj(Object... keysAndValues) {
        return JsonObject.of(keysAndValues);
    }

    /** An array of the converted items. */
    static JsonArray arr(Object... items) {
        return JsonArray.of(items);
    }

    /** The value as compact JSON text, keys in the order the object holds them. */
    static String stringify(Object value) {
        return JsonWriter.write(of(value), 0);
    }

    /** The value as JSON text indented by that many spaces a level. */
    static String stringify(Object value, int indent) {
        return JsonWriter.write(of(value), indent);
    }

    /** Stable stringify: object keys sorted, arrays left in order, no whitespace. */
    static String canonicalStringify(Object value) {
        return JsonWriter.canonical(of(value));
    }

    /** A structured copy that goes through the canonical form, so it is also a check. */
    static Json canonicalClone(Object value) {
        return parse(canonicalStringify(value));
    }

    /** True when two values have the same canonical form. */
    static boolean canonicalEquals(Object a, Object b) {
        return canonicalStringify(a).equals(canonicalStringify(b));
    }

    /** Parse text that is known to be JSON. */
    static Json parse(String text) {
        return parseJson(text, "body");
    }

    /** Parse, with this package's error type instead of a bare syntax error. */
    static Json parseJson(Object text, String field) {
        if (!(text instanceof String source)) {
            throw new InvalidArgumentException(
                    field + " must be a string", Json.obj("field", field, "got", Check.typeOf(text)));
        }
        try {
            return new JsonParser(source).parseDocument();
        } catch (JsonParser.SyntaxException err) {
            throw new InvalidArgumentException(
                    field + " is not valid JSON", Json.obj("field", field, "reason", err.getMessage()));
        }
    }

    /** A type that knows its own JSON form. */
    interface Writable {
        /** The JSON form. */
        Json toJson();
    }
}
