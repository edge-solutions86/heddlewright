package io.github.rkaushikdev.heddlewright.util;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Integer range notation, {@code 1-4,7,9-11}.
 *
 * <p>Card selections, shaft groups, denting plans and defect positions are all written this way on
 * a shed floor docket, so both directions are needed.
 */
public final class Range {

    private static final Pattern PART = Pattern.compile("^(-?\\d+)(?:\\s*-\\s*(-?\\d+))?$");

    private Range() {}

    /** Parse range notation into a sorted, deduplicated list of integers. */
    public static List<Integer> parseRange(String text) {
        return parseRange(text, Bounds.NONE);
    }

    /** Parse range notation, refusing any value outside the bounds. */
    public static List<Integer> parseRange(String text, Bounds bounds) {
        Check.nonEmptyString(text, "text");
        TreeSet<Integer> found = new TreeSet<>();
        for (String rawPart : Text.split(text, ",")) {
            String part = Text.trim(rawPart);
            if (part.isEmpty()) {
                throw new InvalidArgumentException("range has an empty part", Json.obj("text", text));
            }
            Matcher match = PART.matcher(part);
            if (!match.matches()) {
                throw new InvalidArgumentException(
                        "range part \"" + part + "\" is not a number or a span", Json.obj("part", part, "text", text));
            }
            int from = Integer.parseInt(match.group(1));
            int to = match.group(2) == null ? from : Integer.parseInt(match.group(2));
            if (to < from) {
                throw new InvalidArgumentException(
                        "range part \"" + part + "\" counts backwards", Json.obj("part", part, "from", from, "to", to));
            }
            for (int n = from; n <= to; n += 1) {
                found.add(n);
            }
        }
        for (int value : found) {
            if (bounds.min().isPresent() && value < bounds.min().getAsDouble()) {
                double min = bounds.min().getAsDouble();
                throw new InvalidArgumentException(
                        "range value " + value + " is below " + Text.number(min), Json.obj("value", value, "min", min));
            }
            if (bounds.max().isPresent() && value > bounds.max().getAsDouble()) {
                double max = bounds.max().getAsDouble();
                throw new InvalidArgumentException(
                        "range value " + value + " is above " + Text.number(max), Json.obj("value", value, "max", max));
            }
        }
        return List.copyOf(found);
    }

    /** Fold a list of integers back into the shortest range notation. */
    public static String formatRange(List<?> values) {
        List<Object> list = Check.array(values, "values");
        TreeSet<Integer> sorted = new TreeSet<>();
        for (int i = 0; i < list.size(); i += 1) {
            sorted.add(Check.integer(list.get(i), "values[" + i + "]"));
        }
        if (sorted.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        int start = sorted.first();
        int previous = start;
        for (int value : sorted.tailSet(start, false)) {
            if (value == previous + 1) {
                previous = value;
                continue;
            }
            parts.add(start == previous ? Integer.toString(start) : start + "-" + previous);
            start = value;
            previous = value;
        }
        parts.add(start == previous ? Integer.toString(start) : start + "-" + previous);
        return String.join(",", parts);
    }

    /** The count a range covers. */
    public static int rangeSize(String text) {
        return parseRange(text).size();
    }

    /** True when the value falls inside the range notation. */
    public static boolean rangeIncludes(String text, int value) {
        return parseRange(text).contains(value);
    }

    /** Every integer from {@code from} to {@code to} inclusive, counting either way. */
    public static List<Integer> inclusive(int from, int to) {
        int step = to >= from ? 1 : -1;
        List<Integer> out = new ArrayList<>();
        for (int n = from; step > 0 ? n <= to : n >= to; n += step) {
            out.add(n);
        }
        return List.copyOf(out);
    }
}
