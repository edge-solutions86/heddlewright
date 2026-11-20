package io.github.rkaushikdev.heddlewright.util;

/**
 * The root of every error heddlewright throws.
 *
 * <p>Everything thrown out of this package is one of these, carries a stable {@link #code()}, and
 * keeps whatever structured detail the caller needs to report the problem back to a weaver without
 * re-parsing a message string.
 */
public class HeddleException extends RuntimeException {

    private final String code;
    private final JsonObject detail;

    /** An error with a code and no detail. */
    public HeddleException(String message, String code) {
        this(message, code, JsonObject.EMPTY);
    }

    /** An error with a code and structured detail. */
    public HeddleException(String message, String code, JsonObject detail) {
        super(message);
        this.code = code;
        this.detail = detail == null ? JsonObject.EMPTY : detail;
    }

    /** The name the error reports itself by, which is the concrete class. */
    public String name() {
        return getClass().getSimpleName();
    }

    /** The stable code a caller can branch on. */
    public String code() {
        return code;
    }

    /** The structured detail, never absent: an error with none carries an empty object. */
    public JsonObject detail() {
        return detail;
    }

    /** The plain form, for transport across the service edge. */
    public JsonObject toJson() {
        return Json.obj("name", name(), "code", code, "message", getMessage(), "detail", detail);
    }
}
