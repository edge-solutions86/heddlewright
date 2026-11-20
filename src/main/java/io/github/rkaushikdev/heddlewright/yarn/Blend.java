package io.github.rkaushikdev.heddlewright.yarn;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fibre blends.
 *
 * <p>A blend is stated by weight, and every property of the yarn that follows from it is a weighted
 * average by weight rather than by anything else. The label rules are the part people get wrong: the
 * shares have to add to a hundred, and they are rounded to whole numbers on the label, so a blend
 * that adds to a hundred exactly can still round to ninety nine.
 */
public final class Blend {

    /** One fibre in a blend and its share by weight, in whatever units the caller states them. */
    public record Part(String fibre, double share) {}

    /** One fibre as it goes on a label, a whole percentage. */
    public record Share(String fibre, int percent) {}

    /** The weight one fibre contributes to a batch. */
    public record Weight(String fibre, double kilograms) {}

    /** A fibre that is off its target share, and by how much. */
    public record Finding(String fibre, int wanted, int got, int off) {}

    /** Whether a blend is inside its tolerance, and the fibres that are not. */
    public record Tolerance(boolean within, List<Finding> findings) {
        /** Copies the findings. */
        public Tolerance {
            findings = List.copyOf(findings);
        }
    }

    private Blend() {}

    /** Check a blend and hand back a normalised copy, shares as fractions. */
    public static List<Part> normaliseBlend(List<Part> parts) {
        Check.array(parts, "parts", 1);
        List<Part> checked = new ArrayList<>(parts.size());
        for (int index = 0; index < parts.size(); index += 1) {
            Part part = Check.object(parts.get(index), "parts[" + index + "]");
            checked.add(new Part(
                    Check.nonEmptyString(part.fibre(), "parts[" + index + "].fibre"),
                    Check.positiveNumber(part.share(), "parts[" + index + "].share")));
        }
        Set<String> named = new HashSet<>();
        for (Part part : checked) {
            if (!named.add(part.fibre())) {
                throw new InvalidArgumentException(
                        part.fibre() + " is in the blend twice", Json.obj("fibre", part.fibre()));
            }
        }
        double total = Numeric.sumBy(checked, Part::share, "parts");
        return checked.stream()
                .map(part -> new Part(part.fibre(), part.share() / total))
                .toList();
    }

    /** Whether the shares as given already add to a hundred per cent, within a thousandth. */
    public static boolean addsToWhole(List<Part> parts) {
        return addsToWhole(parts, 0.001);
    }

    /** Whether the shares as given already add to a hundred per cent. */
    public static boolean addsToWhole(List<Part> parts, double tolerance) {
        Check.array(parts, "parts", 1);
        Check.number(tolerance, "tolerance", Bounds.atLeast(0));
        return Math.abs(Numeric.sumBy(parts, Part::share, "parts") - 100) <= tolerance;
    }

    /**
     * The blend as it goes on a label: whole percentages that add to a hundred. The rounding is done
     * largest first and the remainder goes to the largest fibre, because a label that adds to ninety
     * nine is a label nobody will sign.
     */
    public static List<Share> labelShares(List<Part> parts) {
        List<Part> sorted = new ArrayList<>(normaliseBlend(parts));
        sorted.sort(Comparator.comparingDouble(Part::share).reversed());
        int[] percents = new int[sorted.size()];
        int total = 0;
        for (int i = 0; i < sorted.size(); i += 1) {
            percents[i] = (int) Math.round(sorted.get(i).share() * 100);
            total += percents[i];
        }
        if (total != 100 && percents.length > 0) {
            percents[0] += 100 - total;
        }
        List<Share> out = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i += 1) {
            out.add(new Share(sorted.get(i).fibre(), percents[i]));
        }
        return List.copyOf(out);
    }

    /** The label itself, written the way a ticket reads. */
    public static String labelText(List<Part> parts) {
        return labelShares(parts).stream()
                .map(part -> part.percent() + "% " + part.fibre())
                .collect(Collectors.joining(" "));
    }

    /**
     * A property of the blend, averaged by weight. Moisture regain, density and fibre strength all
     * work this way and none of them averages by fibre count.
     */
    public static double weightedProperty(List<Part> parts, Map<String, ? extends Number> values) {
        double total = 0;
        for (Part part : normaliseBlend(parts)) {
            Number value = values.get(part.fibre());
            if (value == null) {
                throw new InvalidArgumentException(
                        "no value given for " + part.fibre(), Json.obj("fibre", part.fibre()));
            }
            total += Check.number(value, "values." + part.fibre()) * part.share();
        }
        return Numeric.roundTo(total, 4);
    }

    /** The weight each fibre contributes to a batch. */
    public static List<Weight> batchWeights(List<Part> parts, double totalKg) {
        Check.positiveNumber(totalKg, "totalKg");
        return normaliseBlend(parts).stream()
                .map(part -> new Weight(part.fibre(), Numeric.roundTo(part.share() * totalKg, 4)))
                .toList();
    }

    /**
     * The conditioned weight of a batch, which is what it is bought and sold by. Fibre holds water,
     * and the trade weighs it at an agreed regain rather than bone dry, so the invoice weight is above
     * the oven dry one.
     */
    public static double conditionedWeight(double ovenDryKg, double regainPercent) {
        Check.positiveNumber(ovenDryKg, "ovenDryKg");
        Check.number(regainPercent, "regainPercent", Bounds.atLeast(0));
        return Numeric.roundTo(ovenDryKg * (1 + regainPercent / 100), 4);
    }

    /** The regain a blend is conditioned at, averaged by weight. */
    public static double blendRegain(List<Part> parts, Map<String, ? extends Number> regains) {
        return weightedProperty(parts, regains);
    }

    /** Whether a blend is within three percentage points of its target on every fibre. */
    public static Tolerance withinTolerance(List<Part> parts, List<Part> target) {
        return withinTolerance(parts, target, 3);
    }

    /** Whether a blend is inside a stated tolerance of its target. */
    public static Tolerance withinTolerance(List<Part> parts, List<Part> target, double tolerancePercent) {
        Map<String, Integer> found = new LinkedHashMap<>();
        labelShares(parts).forEach(part -> found.put(part.fibre(), part.percent()));
        List<Finding> findings = new ArrayList<>();
        for (Share wanted : labelShares(target)) {
            int got = found.getOrDefault(wanted.fibre(), 0);
            if (Math.abs(got - wanted.percent()) > tolerancePercent) {
                findings.add(new Finding(wanted.fibre(), wanted.percent(), got, got - wanted.percent()));
            }
        }
        return new Tolerance(findings.isEmpty(), findings);
    }
}
