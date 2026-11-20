package io.github.rkaushikdev.heddlewright.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Capacity checks.
 *
 * <p>Six modules were each writing the same four lines: compare a wanted figure against what a
 * machine has, throw if it is over, and put the shortfall in the detail. They differed only in the
 * names, and the shortfall was worked out three different ways, so two of them reported it the
 * wrong way round.
 *
 * <p>The shortfall is always what is over the limit, never what is under it.
 */
public final class Limits {

    private Limits() {}

    /** How far past a limit a figure sits, which is zero when it is inside. */
    public static double over(double value, double limit) {
        Check.number(value, "value", Bounds.NONE);
        Check.number(limit, "limit", Bounds.NONE);
        return Numeric.roundTo(Math.max(0, value - limit), 6);
    }

    /** How much room is left under a limit, which is zero when it is over. */
    public static double room(double value, double limit) {
        Check.number(value, "value", Bounds.NONE);
        Check.number(limit, "limit", Bounds.NONE);
        return Numeric.roundTo(Math.max(0, limit - value), 6);
    }

    /** Whether a figure fits inside a limit. */
    public static boolean fits(double value, double limit) {
        return over(value, limit) == 0;
    }

    /** Refuse a figure past a limit, with no detail beyond the shortfall. */
    public static double assertFits(double value, double limit, String what, String has) {
        return assertFits(value, limit, what, has, JsonObject.EMPTY);
    }

    /**
     * Refuse a figure past a limit. The message says what the machine has rather than what was asked
     * for, because the machine is the fact and the ask is the thing that has to change.
     */
    public static double assertFits(double value, double limit, String what, String has, JsonObject detail) {
        Check.nonEmptyString(what, "what");
        Check.nonEmptyString(has, "has");
        Check.plainObject(detail, "detail");
        if (fits(value, limit)) {
            return value;
        }
        throw new CapacityException(
                has, detail.with("what", what, "wanted", value, "available", limit, "over", over(value, limit)));
    }

    /** Refuse a figure below a floor, with no detail beyond the shortfall. */
    public static double assertAtLeast(double value, double floor, String what, String needs) {
        return assertAtLeast(value, floor, what, needs, JsonObject.EMPTY);
    }

    /** Refuse a figure below a floor, for the checks that run the other way. */
    public static double assertAtLeast(double value, double floor, String what, String needs, JsonObject detail) {
        Check.nonEmptyString(what, "what");
        Check.nonEmptyString(needs, "needs");
        if (value >= floor) {
            return value;
        }
        throw new CapacityException(
                needs,
                detail.with("what", what, "wanted", value, "floor", floor, "short", Numeric.roundTo(floor - value, 6)));
    }

    /** Every position over its limit, numbered from one under {@code index}. */
    public static List<JsonObject> shortfalls(List<?> values, List<?> limits) {
        return shortfalls(values, limits, "index");
    }

    /**
     * The same reading for a list of figures against a list of limits, which is what a per-shaft or
     * per-section check wants: every one of them, not the first, and not a total that hides which one
     * failed.
     */
    public static List<JsonObject> shortfalls(List<?> values, List<?> limits, String label) {
        if (values == null || limits == null) {
            throw new InvalidArgumentException("shortfalls compares two lists", JsonObject.EMPTY);
        }
        if (values.size() != limits.size()) {
            throw new InvalidArgumentException(
                    "the two lists are different lengths", Json.obj("values", values.size(), "limits", limits.size()));
        }
        List<JsonObject> out = new ArrayList<>();
        for (int index = 0; index < values.size(); index += 1) {
            double value = Check.number(values.get(index), "value");
            double limit = Check.number(limits.get(index), "limit");
            double past = over(value, limit);
            if (past > 0) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put(label, index + 1);
                entry.put("wanted", value);
                entry.put("available", limit);
                entry.put("over", past);
                out.add((JsonObject) Json.of(entry));
            }
        }
        return List.copyOf(out);
    }

    /** The worst of a list of shortfalls, or empty when there are none. */
    public static Optional<JsonObject> worstShortfall(List<JsonObject> found) {
        if (found == null || found.isEmpty()) {
            return Optional.empty();
        }
        JsonObject best = found.get(0);
        for (JsonObject entry : found) {
            if (entry.get("over").asDouble() > best.get("over").asDouble()) {
                best = entry;
            }
        }
        return Optional.of(best);
    }
}
