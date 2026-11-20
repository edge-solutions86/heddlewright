package io.github.rkaushikdev.heddlewright.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Small integer and statistics helpers.
 *
 * <p>The number theory in here is what decides whether a satin move number is legal and how long a
 * card deck has to be before a pattern comes back round, so it is kept separate and tested hard.
 */
public final class Numeric {

    private Numeric() {}

    /** The greatest common divisor, ignoring sign; {@code gcd(0, 0)} is zero. */
    public static int gcd(int a, int b) {
        int x = Math.abs(a);
        int y = Math.abs(b);
        while (y != 0) {
            int t = y;
            y = x % y;
            x = t;
        }
        return x;
    }

    /** The least common multiple, which is the repeat length of two cycles run together. */
    public static int lcm(int a, int b) {
        int g = gcd(a, b);
        if (g == 0) {
            return 0;
        }
        return Math.abs((a / g) * b);
    }

    /** Whether two numbers share no factor but one. */
    public static boolean areCoprime(int a, int b) {
        return gcd(a, b) == 1;
    }

    /** Every m in 1..n-1 that is coprime with n, in ascending order. */
    public static List<Integer> coprimesBelow(int n) {
        Check.integer(n, "n", Bounds.atLeast(1));
        List<Integer> out = new ArrayList<>();
        for (int m = 1; m < n; m += 1) {
            if (gcd(m, n) == 1) {
                out.add(m);
            }
        }
        return List.copyOf(out);
    }

    /** Non-negative remainder, unlike the {@code %} operator on negative operands. */
    public static int mod(int value, int modulus) {
        Check.integer(modulus, "modulus", Bounds.atLeast(1));
        return Math.floorMod(value, modulus);
    }

    /** Hold a value inside an inclusive range. */
    public static double clamp(double value, double min, double max) {
        Check.number(value, "value", Bounds.NONE);
        Check.number(min, "min", Bounds.NONE);
        Check.number(max, "max", Bounds.NONE);
        if (min > max) {
            throw new InvalidArgumentException("clamp needs min <= max", Json.obj("min", min, "max", max));
        }
        return Math.min(Math.max(value, min), max);
    }

    /** Round to two decimal places, away from zero on a tie. */
    public static double roundTo(double value) {
        return roundTo(value, 2);
    }

    /** Round to a fixed number of decimal places, away from zero on a tie. */
    public static double roundTo(double value, int places) {
        Check.number(value, "value", Bounds.NONE);
        Check.integer(places, "places", Bounds.between(0, 12));
        double factor = StrictMath.pow(10, places);
        double scaled = value * factor;
        double rounded = Math.signum(scaled) * Math.round(Math.abs(scaled));
        return rounded / factor;
    }

    /** The total of a list of numbers, each checked to be finite. */
    public static double sum(List<?> values) {
        List<Object> list = Check.array(values, "values");
        double total = 0;
        for (int i = 0; i < list.size(); i += 1) {
            total += Check.number(list.get(i), "values[" + i + "]");
        }
        return total;
    }

    /** The mean of a list that is not empty. */
    public static double mean(List<?> values) {
        Check.array(values, "values", 1);
        return sum(values) / values.size();
    }

    /** Add a field up over a list of records, naming the record at fault when a field is missing. */
    public static <T> double sumBy(List<T> items, Function<? super T, ?> read) {
        return sumBy(items, (item, index) -> read.apply(item), "items");
    }

    /** {@link #sumBy(List, Function)} under the caller's own name for its records. */
    public static <T> double sumBy(List<T> items, Function<? super T, ?> read, String field) {
        return sumBy(items, (item, index) -> read.apply(item), field);
    }

    /** {@link #sumBy(List, Function)} with the index handed to the reader. */
    public static <T> double sumBy(List<T> items, BiFunction<? super T, Integer, ?> read) {
        return sumBy(items, read, "items");
    }

    /** {@link #sumBy(List, BiFunction)} under the caller's own name for its records. */
    public static <T> double sumBy(List<T> items, BiFunction<? super T, Integer, ?> read, String field) {
        Check.array(items, field);
        double total = 0;
        for (int i = 0; i < items.size(); i += 1) {
            total += Check.number(read.apply(items.get(i), i), field + "[" + i + "]");
        }
        return total;
    }

    /** The mean of a field over a list of records. An empty list averages zero. */
    public static <T> double meanBy(List<T> items, Function<? super T, ?> read) {
        return meanBy(items, read, "items");
    }

    /** {@link #meanBy(List, Function)} under the caller's own name for its records. */
    public static <T> double meanBy(List<T> items, Function<? super T, ?> read, String field) {
        Check.array(items, field);
        if (items.isEmpty()) {
            return 0;
        }
        return sumBy(items, read, field) / items.size();
    }

    /** Population standard deviation, which is what a striking or pick spacing report wants. */
    public static double stdev(List<?> values) {
        Check.array(values, "values", 1);
        double m = mean(values);
        List<Double> squares = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i += 1) {
            double v = Check.number(values.get(i), "values[" + i + "]");
            squares.add((v - m) * (v - m));
        }
        return Math.sqrt(sum(squares) / values.size());
    }

    /** Linear-interpolated percentile over a sorted copy of the input. */
    public static double percentile(List<?> values, double p) {
        Check.array(values, "values", 1);
        Check.number(p, "p", Bounds.between(0, 100));
        List<Double> sorted = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i += 1) {
            sorted.add(Check.number(values.get(i), "values[" + i + "]"));
        }
        sorted.sort(null);
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        double pos = (p / 100) * (sorted.size() - 1);
        int low = (int) Math.floor(pos);
        int high = (int) Math.ceil(pos);
        if (low == high) {
            return sorted.get(low);
        }
        return sorted.get(low) + (sorted.get(high) - sorted.get(low)) * (pos - low);
    }
}
