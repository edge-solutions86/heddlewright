package io.github.rkaushikdev.heddlewright.util;

/** A value handed in by a caller is the wrong shape, type or range. */
public class InvalidArgumentException extends HeddleException {

    /** The error with no detail. */
    public InvalidArgumentException(String message) {
        this(message, JsonObject.EMPTY);
    }

    /** The error with structured detail. */
    public InvalidArgumentException(String message, JsonObject detail) {
        super(message, "invalid_argument", detail);
    }
}
