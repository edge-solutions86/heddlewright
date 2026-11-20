package io.github.rkaushikdev.heddlewright.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Units.
 *
 * <p>A weaving shed runs on both systems at once and will not be talked out of it: the loom is
 * quoted in centimetres, the cloth is sold in yards, the yarn is bought in kilograms and the sett
 * is set in ends an inch. Every conversion here is exact by definition rather than rounded, and
 * everything goes through one base unit per dimension so a round trip cannot drift.
 */
public final class Units {

    /** What a unit measures. */
    public enum Dimension {
        /** Metres and the rest. */
        LENGTH,
        /** Kilograms and the rest. */
        MASS,
        /** Square metres and the rest. */
        AREA;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final Map<String, Double> LENGTH =
            table("mm", 0.001, "cm", 0.01, "m", 1.0, "km", 1000.0, "in", 0.0254, "ft", 0.3048, "yd", 0.9144);

    private static final Map<String, Double> MASS =
            table("g", 0.001, "kg", 1.0, "oz", 0.028349523125, "lb", 0.45359237);

    private static final Map<String, Double> AREA =
            table("m2", 1.0, "cm2", 0.0001, "in2", 0.0254 * 0.0254, "yd2", 0.9144 * 0.9144);

    private static final Map<Dimension, Map<String, Double>> DIMENSIONS =
            Map.of(Dimension.LENGTH, LENGTH, Dimension.MASS, MASS, Dimension.AREA, AREA);

    private Units() {}

    private static Map<String, Double> table(Object... pairs) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Double) pairs[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(map);
    }

    /** Which dimension a unit belongs to. */
    public static Dimension dimensionOf(String unit) {
        Check.nonEmptyString(unit, "unit");
        for (Dimension dimension : Dimension.values()) {
            if (DIMENSIONS.get(dimension).containsKey(unit)) {
                return dimension;
            }
        }
        throw new InvalidArgumentException(
                "unknown unit \"" + unit + "\"", Json.obj("unit", unit, "known", knownUnits()));
    }

    /** Every unit the package converts between, sorted. */
    public static List<String> knownUnits() {
        return Stream.of(LENGTH, MASS, AREA)
                .flatMap(table -> table.keySet().stream())
                .sorted()
                .toList();
    }

    /** Convert between two units of the same dimension, to six places. */
    public static double convert(double value, String from, String to) {
        return convert(value, from, to, 6);
    }

    /** Convert between two units of the same dimension, to the places given. */
    public static double convert(double value, String from, String to, int places) {
        Check.number(value, "value", Bounds.NONE);
        Dimension dimension = dimensionOf(from);
        if (dimensionOf(to) != dimension) {
            throw new InvalidArgumentException(
                    from + " and " + to + " do not measure the same thing", Json.obj("from", from, "to", to));
        }
        Map<String, Double> table = DIMENSIONS.get(dimension);
        return Numeric.roundTo((value * table.get(from)) / table.get(to), places);
    }

    /** True when two units can be converted between. */
    public static boolean comparable(String from, String to) {
        try {
            return dimensionOf(from) == dimensionOf(to);
        } catch (InvalidArgumentException err) {
            return false;
        }
    }

    /** Threads per inch to threads per centimetre. */
    public static double perInchToPerCm(double value) {
        Check.number(value, "value", Bounds.atLeast(0));
        return Numeric.roundTo(value / 2.54, 4);
    }

    /** Threads per centimetre to threads per inch. */
    public static double perCmToPerInch(double value) {
        Check.number(value, "value", Bounds.atLeast(0));
        return Numeric.roundTo(value * 2.54, 4);
    }

    /**
     * A density conversion: grams a square metre to ounces a square yard. It is not a plain unit
     * conversion because both parts change, which is where it usually goes wrong.
     */
    public static double gsmToOzYd2(double value) {
        Check.number(value, "value", Bounds.atLeast(0));
        return Numeric.roundTo(convert(value, "g", "oz") / convert(1, "m2", "yd2"), 4);
    }

    /** Ounces a square yard to grams a square metre. */
    public static double ozYd2ToGsm(double value) {
        Check.number(value, "value", Bounds.atLeast(0));
        return Numeric.roundTo(convert(value, "oz", "g") * convert(1, "m2", "yd2"), 4);
    }

    /** A value with its unit, to two places, for a docket. */
    public static String format(double value, String unit) {
        return format(value, unit, 2);
    }

    /** A value with its unit, to the places given. */
    public static String format(double value, String unit, int places) {
        dimensionOf(unit);
        Check.number(value, "value", Bounds.NONE);
        return Text.number(Numeric.roundTo(value, places)) + " " + unit;
    }
}
