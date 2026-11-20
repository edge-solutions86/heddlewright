package io.github.rkaushikdev.heddlewright.loom;

import java.util.List;
import java.util.Optional;

/**
 * What a style asks of a loom: a width, a shedding capacity in shafts or hooks, and a box for every
 * weft colour. A style quoted as a weft pattern carries the pattern too, because the order the
 * colours are picked in decides whether a loom boxed at one end can pick them at all.
 */
public record Requirements(
        double widthCm, double shafts, double hooks, int weftColours, Optional<List<String>> weftPattern) {

    /** Copies the pattern. */
    public Requirements {
        weftPattern = weftPattern.map(List::copyOf);
    }
}
