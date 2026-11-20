package io.github.rkaushikdev.heddlewright.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Rotation and period work over a repeating sequence.
 *
 * <p>A treadling, a threading and a card chain all repeat, so "how long is the real repeat" and
 * "are these two the same pattern started in a different place" are questions this package asks
 * constantly.
 */
public final class Cycle {

    private Cycle() {}

    /** Rotate left by {@code n}; a negative {@code n} rotates right. */
    public static <T> List<T> rotate(List<T> sequence, int n) {
        Check.array(sequence, "sequence");
        if (sequence.isEmpty()) {
            return List.of();
        }
        int k = Numeric.mod(n, sequence.size());
        List<T> out = new ArrayList<>(sequence.size());
        out.addAll(sequence.subList(k, sequence.size()));
        out.addAll(sequence.subList(0, k));
        return List.copyOf(out);
    }

    /** Every distinct rotation, in rotation order starting from the input. */
    public static <T> List<List<T>> rotations(List<T> sequence) {
        Check.array(sequence, "sequence");
        Set<List<T>> seen = new LinkedHashSet<>();
        for (int n = 0; n < sequence.size(); n += 1) {
            seen.add(rotate(sequence, n));
        }
        return List.copyOf(seen);
    }

    /**
     * The shortest prefix that, repeated, rebuilds the whole sequence. A treadling written out four
     * times has a period of a quarter of its length.
     */
    public static int period(List<?> sequence) {
        Check.array(sequence, "sequence");
        int n = sequence.size();
        if (n == 0) {
            return 0;
        }
        for (int p = 1; p <= n; p += 1) {
            if (n % p != 0) {
                continue;
            }
            boolean matches = true;
            for (int i = p; i < n && matches; i += 1) {
                if (!Objects.equals(sequence.get(i), sequence.get(i % p))) {
                    matches = false;
                }
            }
            if (matches) {
                return p;
            }
        }
        return n;
    }

    /** The sequence cut down to one period. */
    public static <T> List<T> minimalRepeat(List<T> sequence) {
        return List.copyOf(sequence.subList(0, period(sequence)));
    }

    /** True when b is a rotation of a, so both weave the same cloth started elsewhere. */
    public static <T> boolean isRotationOf(List<T> a, List<T> b) {
        Check.array(a, "a");
        Check.array(b, "b");
        if (a.size() != b.size()) {
            return false;
        }
        if (a.isEmpty()) {
            return true;
        }
        return rotations(a).stream().anyMatch(r -> r.equals(b));
    }

    /** The offset that turns a into b, or -1 when no rotation does. */
    public static <T> int rotationOffset(List<T> a, List<T> b) {
        Check.array(a, "a");
        Check.array(b, "b");
        if (a.size() != b.size()) {
            return -1;
        }
        for (int n = 0; n < a.size(); n += 1) {
            if (rotate(a, n).equals(b)) {
                return n;
            }
        }
        return a.isEmpty() ? 0 : -1;
    }

    /**
     * The rotation that sorts smallest by its JSON text, used as a stable key so two chains cut at
     * different lags hash the same.
     */
    public static <T> List<T> canonicalRotation(List<T> sequence) {
        Check.array(sequence, "sequence");
        if (sequence.isEmpty()) {
            return List.of();
        }
        List<T> best = null;
        String bestKey = null;
        for (List<T> r : rotations(sequence)) {
            String key = Json.stringify(r);
            if (bestKey == null || key.compareTo(bestKey) < 0) {
                bestKey = key;
                best = r;
            }
        }
        return best;
    }

    /** Start at 0 and add {@code step} {@code size} times, modulo {@code size}. */
    public static List<Integer> stepSequence(int size, int step) {
        return stepSequence(size, step, size);
    }

    /**
     * A step sequence: start at 0 and add {@code step} {@code count} times, modulo {@code size}.
     * Satin move numbers, sectional warping and card lag advances all use it.
     */
    public static List<Integer> stepSequence(int size, int step, int count) {
        Check.positiveInteger(size, "size");
        Check.positiveInteger(count, "count");
        List<Integer> out = new ArrayList<>(count);
        for (int i = 0; i < count; i += 1) {
            out.add(Numeric.mod(i * step, size));
        }
        return List.copyOf(out);
    }

    /** How many steps of {@code step} in a ring of {@code size} before the start comes back. */
    public static int stepCycleLength(int size, int step) {
        Check.positiveInteger(size, "size");
        int s = Numeric.mod(step, size);
        if (s == 0) {
            return 1;
        }
        return size / Numeric.gcd(size, s);
    }

    /** True when stepping by {@code step} visits every position of the ring exactly once. */
    public static boolean stepCoversRing(int size, int step) {
        return stepCycleLength(size, step) == size;
    }
}
