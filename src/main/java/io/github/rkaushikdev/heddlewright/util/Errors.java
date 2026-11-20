package io.github.rkaushikdev.heddlewright.util;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Reading errors back from their plain form, and asking what an error is.
 *
 * <p>The error types themselves are {@link HeddleException} and its five subclasses. This holds
 * the code table that lets an error cross the service edge as plain data and come back as the same
 * subclass.
 */
public final class Errors {

    private static final Map<String, BiFunction<String, JsonObject, HeddleException>> CODES = Map.of(
            "invalid_argument", InvalidArgumentException::new,
            "draft_error", DraftException::new,
            "capacity_error", CapacityException::new,
            "not_found", NotFoundException::new,
            "conflict", ConflictException::new);

    private Errors() {}

    /** Rebuild an error from its {@link HeddleException#toJson()} form. */
    public static HeddleException reviveError(Object plain) {
        Json value = Json.of(plain);
        if (!(value instanceof JsonObject object)) {
            throw new InvalidArgumentException("reviveError needs an object", Json.obj("got", Check.typeOf(plain)));
        }
        String message = object.get("message").isMissing() ? "" : Text.string(object.get("message"));
        JsonObject detail = object.get("detail") instanceof JsonObject found ? found : JsonObject.EMPTY;
        Json code = object.get("code");
        BiFunction<String, JsonObject, HeddleException> known =
                code instanceof Json.Str text ? CODES.get(text.value()) : null;
        if (known == null) {
            return new HeddleException(message, code.isMissing() ? "unknown" : Text.string(code), detail);
        }
        return known.apply(message, detail);
    }

    /** True when {@code value} is one of this package's errors. */
    public static boolean isHeddleError(Object value) {
        return value instanceof HeddleException;
    }

    /** True when {@code value} is one of this package's errors and carries {@code code}. */
    public static boolean isHeddleError(Object value, String code) {
        return value instanceof HeddleException error && error.code().equals(code);
    }

    /** The codes {@link #reviveError} reconstructs exactly, sorted. */
    public static List<String> knownErrorCodes() {
        return CODES.keySet().stream().sorted().toList();
    }
}
