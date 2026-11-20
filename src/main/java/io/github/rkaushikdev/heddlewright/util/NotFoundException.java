package io.github.rkaushikdev.heddlewright.util;

/** A record was looked up and is not there. */
public class NotFoundException extends HeddleException {

    /** The error with no detail. */
    public NotFoundException(String message) {
        this(message, JsonObject.EMPTY);
    }

    /** The error with structured detail. */
    public NotFoundException(String message, JsonObject detail) {
        super(message, "not_found", detail);
    }
}
