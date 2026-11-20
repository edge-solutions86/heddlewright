package io.github.rkaushikdev.heddlewright.util;

import java.util.OptionalDouble;

/**
 * The inclusive limits a figure has to sit between, either of which may be absent.
 *
 * <p>Used by the argument checks, which name the bound a value broke in their message and detail.
 */
public record Bounds(OptionalDouble min, OptionalDouble max) {

    /** No limits at all. */
    public static final Bounds NONE = new Bounds(OptionalDouble.empty(), OptionalDouble.empty());

    /** A floor only. */
    public static Bounds atLeast(double min) {
        return new Bounds(OptionalDouble.of(min), OptionalDouble.empty());
    }

    /** A ceiling only. */
    public static Bounds atMost(double max) {
        return new Bounds(OptionalDouble.empty(), OptionalDouble.of(max));
    }

    /** A floor and a ceiling, both inclusive. */
    public static Bounds between(double min, double max) {
        return new Bounds(OptionalDouble.of(min), OptionalDouble.of(max));
    }
}
