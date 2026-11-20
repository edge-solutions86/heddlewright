package io.github.rkaushikdev.heddlewright.util;

/**
 * A counter that hands out zero-padded references, the way a shed numbers card decks and warp
 * beams: {@code CD0001}, {@code CD0002} and on. A number wider than the pad is written in full.
 */
public final class ReferenceCounter {

    private final String prefix;
    private final int width;
    private final int start;
    private int next;

    ReferenceCounter(String prefix, int width, int start) {
        this.prefix = Check.nonEmptyString(prefix, "prefix");
        this.width = Check.integer(width, "width", Bounds.between(1, 12));
        this.start = Check.integer(start, "start", Bounds.atLeast(0));
        this.next = start;
    }

    /** The next reference, advancing the counter. */
    public String next() {
        String value = format(next);
        next += 1;
        return value;
    }

    /** The reference {@link #next()} would hand out, without advancing. */
    public String peek() {
        return format(next);
    }

    /** How many references have been handed out. */
    public int count() {
        return next - start;
    }

    private String format(int value) {
        return prefix + Text.padStart(Integer.toString(value), width, '0');
    }
}
