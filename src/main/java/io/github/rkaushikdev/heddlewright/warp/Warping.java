package io.github.rkaushikdev.heddlewright.warp;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Warping plans.
 *
 * <p>A creel holds a few hundred packages; a warp needs a few thousand ends. The two ways round that
 * are to warp the full width several times onto back beams and beam them together, or to build the
 * warp in sections across a drum and wind the whole thing off at once.
 *
 * <p>Sectional warping is where the arithmetic bites: the sections have to be the same width or the
 * beam builds up unevenly, so the ends are spread rather than simply divided, and the last section
 * is never allowed to be the thin one.
 */
public final class Warping {

    /** A direct warping onto back beams. */
    public record BeamPlan(
            int beams,
            List<Integer> endsPerBeam,
            int creelCapacity,
            double warpLengthMetres,
            int creelFillsPerBeam,
            int totalEnds) {
        /** Copies the list. */
        public BeamPlan {
            endsPerBeam = List.copyOf(endsPerBeam);
        }
    }

    /** A sectional warping: the sections, their ends and widths, and whether they are even. */
    public record SectionalPlan(
            int sections,
            List<Integer> endsPerSection,
            List<Double> widthPerSectionInches,
            double totalWidthInches,
            double warpLengthMetres,
            boolean evenSections) {
        /** Copies the lists. */
        public SectionalPlan {
            endsPerSection = List.copyOf(endsPerSection);
            widthPerSectionInches = List.copyOf(widthPerSectionInches);
        }
    }

    /** How much the sections of a plan differ, in ends. */
    public record Spread(int smallest, int largest, int spread) {}

    /** The yarn a warping run uses, useful and wasted, in metres. */
    public record Yarn(double useful, double waste, double total, double wasteShare) {}

    /** How long a warping run takes. */
    public record Time(double runningMinutes, double changeMinutes, double hours) {}

    private Warping() {}

    /** How many back beams a direct warping needs, and how the ends divide. */
    public static BeamPlan beamWarpingPlan(int totalEnds, int creelCapacity, double warpLengthMetres) {
        Check.positiveInteger(totalEnds, "totalEnds");
        Check.positiveInteger(creelCapacity, "creelCapacity");
        Check.positiveNumber(warpLengthMetres, "warpLengthMetres");
        int beams = (int) Math.ceil((double) totalEnds / creelCapacity);
        return new BeamPlan(beams, balanceEnds(totalEnds, beams), creelCapacity, warpLengthMetres, 1, totalEnds);
    }

    /**
     * Spread {@code totalEnds} over {@code groups} as evenly as whole ends allow, giving the extra ends
     * to the earliest groups so no group ends up thin.
     */
    public static List<Integer> balanceEnds(int totalEnds, int groups) {
        Check.positiveInteger(totalEnds, "totalEnds");
        Check.positiveInteger(groups, "groups");
        if (groups > totalEnds) {
            throw new InvalidArgumentException(
                    "there are fewer ends than groups to spread them over",
                    Json.obj("totalEnds", totalEnds, "groups", groups));
        }
        int base = totalEnds / groups;
        int extra = totalEnds % groups;
        List<Integer> out = new ArrayList<>(groups);
        for (int index = 0; index < groups; index += 1) {
            out.add(base + (index < extra ? 1 : 0));
        }
        return List.copyOf(out);
    }

    /** A sectional warping plan: how many sections, how wide, and how many ends. */
    public static SectionalPlan sectionalPlan(
            int totalEnds, int creelCapacity, double endsPerInch, double warpLengthMetres) {
        Check.positiveInteger(totalEnds, "totalEnds");
        Check.positiveInteger(creelCapacity, "creelCapacity");
        Check.positiveNumber(endsPerInch, "endsPerInch");
        Check.positiveNumber(warpLengthMetres, "warpLengthMetres");
        int sections = (int) Math.ceil((double) totalEnds / creelCapacity);
        List<Integer> endsPerSection = balanceEnds(totalEnds, sections);
        return new SectionalPlan(
                sections,
                endsPerSection,
                endsPerSection.stream()
                        .map(ends -> Numeric.roundTo(ends / endsPerInch, 3))
                        .toList(),
                Numeric.roundTo(totalEnds / endsPerInch, 3),
                warpLengthMetres,
                new HashSet<>(endsPerSection).size() == 1);
    }

    /**
     * How much the section widths vary. A beam wound from sections that differ by more than a few ends
     * builds up unevenly and the warp tension follows it.
     */
    public static Spread sectionSpread(SectionalPlan plan) {
        int smallest = Collections.min(plan.endsPerSection());
        int largest = Collections.max(plan.endsPerSection());
        return new Spread(smallest, largest, largest - smallest);
    }

    /** Refuse a plan whose sections differ by more than one end. */
    public static SectionalPlan assertSectionsEven(SectionalPlan plan) {
        return assertSectionsEven(plan, 1);
    }

    /** Refuse a plan whose sections differ by more than the shed will accept. */
    public static SectionalPlan assertSectionsEven(SectionalPlan plan, double maxSpread) {
        Check.number(maxSpread, "maxSpread", Bounds.atLeast(0));
        Spread spread = sectionSpread(plan);
        if (spread.spread() > maxSpread) {
            throw new InvalidArgumentException(
                    "the sections are too uneven to wind a level beam",
                    ((JsonObject) Json.of(spread)).with("maxSpread", maxSpread));
        }
        return plan;
    }

    /** The yarn a single pass uses, with two metres a creel position left as waste. */
    public static Yarn warpingYarnMetres(int totalEnds, double warpLengthMetres) {
        return warpingYarnMetres(totalEnds, warpLengthMetres, 2, 1);
    }

    /**
     * The yarn a warping run uses, in metres, including the tail left in the creel and the length
     * lost at the change-over between sections.
     */
    public static Yarn warpingYarnMetres(
            int totalEnds, double warpLengthMetres, double creelWasteMetres, int sections) {
        Check.positiveInteger(totalEnds, "totalEnds");
        Check.positiveNumber(warpLengthMetres, "warpLengthMetres");
        Check.number(creelWasteMetres, "creelWasteMetres", Bounds.atLeast(0));
        Check.positiveInteger(sections, "sections");
        double useful = totalEnds * warpLengthMetres;
        double waste = totalEnds * creelWasteMetres * sections;
        return new Yarn(
                Numeric.roundTo(useful, 3),
                Numeric.roundTo(waste, 3),
                Numeric.roundTo(useful + waste, 3),
                Numeric.roundTo(waste / (useful + waste), 4));
    }

    /** How long a warping run takes with eight minutes at each section change. */
    public static Time warpingHours(double warpLengthMetres, int sections, double metresPerMinute) {
        return warpingHours(warpLengthMetres, sections, metresPerMinute, 8);
    }

    /** How long a warping run takes at a given warping speed. */
    public static Time warpingHours(
            double warpLengthMetres, int sections, double metresPerMinute, double changeMinutes) {
        Check.positiveNumber(warpLengthMetres, "warpLengthMetres");
        Check.positiveInteger(sections, "sections");
        Check.positiveNumber(metresPerMinute, "metresPerMinute");
        Check.number(changeMinutes, "changeMinutes", Bounds.atLeast(0));
        double running = (warpLengthMetres * sections) / metresPerMinute;
        double changes = changeMinutes * Math.max(0, sections - 1);
        return new Time(
                Numeric.roundTo(running, 2), Numeric.roundTo(changes, 2), Numeric.roundTo((running + changes) / 60, 3));
    }
}
