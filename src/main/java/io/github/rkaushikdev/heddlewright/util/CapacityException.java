package io.github.rkaushikdev.heddlewright.util;

/** A loom, machine or harness cannot physically do what was asked of it. */
public class CapacityException extends HeddleException {

    /** The error with no detail. */
    public CapacityException(String message) {
        this(message, JsonObject.EMPTY);
    }

    /** The error with structured detail. */
    public CapacityException(String message, JsonObject detail) {
        super(message, "capacity_error", detail);
    }
}
