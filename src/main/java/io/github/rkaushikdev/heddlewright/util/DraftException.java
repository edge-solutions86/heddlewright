package io.github.rkaushikdev.heddlewright.util;

/** A draft, tie-up or card deck is internally inconsistent. */
public class DraftException extends HeddleException {

    /** The error with no detail. */
    public DraftException(String message) {
        this(message, JsonObject.EMPTY);
    }

    /** The error with structured detail. */
    public DraftException(String message, JsonObject detail) {
        super(message, "draft_error", detail);
    }
}
