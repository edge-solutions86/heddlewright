package io.github.rkaushikdev.heddlewright.util;

/** A write would break an invariant the store guarantees. */
public class ConflictException extends HeddleException {

    /** The error with no detail. */
    public ConflictException(String message) {
        this(message, JsonObject.EMPTY);
    }

    /** The error with structured detail. */
    public ConflictException(String message, JsonObject detail) {
        super(message, "conflict", detail);
    }
}
