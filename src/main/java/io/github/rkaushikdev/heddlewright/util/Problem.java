package io.github.rkaushikdev.heddlewright.util;

/**
 * One thing wrong with something that is still usable: a stable code a report can group on, the
 * sentence a weaver reads, and the structured detail behind it.
 */
public record Problem(String code, String message, JsonObject detail) {

    /** A problem with no detail. */
    public Problem(String code, String message) {
        this(code, message, JsonObject.EMPTY);
    }
}
