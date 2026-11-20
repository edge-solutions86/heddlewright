package io.github.rkaushikdev.heddlewright.warp;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Limits;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.util.Problem;
import java.util.ArrayList;
import java.util.List;

/**
 * The reed and the denting plan.
 *
 * <p>A reed is a comb of dents, counted per inch, and the warp is sleyed through it so many ends to
 * a dent. The reed sets the width and spaces the warp, and an uneven or over-full denting shows in
 * the cloth as a reed mark long after the loom has forgotten about it.
 */
public final class Reed {

    /** Reeds a shed is likely to have on the rack, in dents per inch. */
    public static final List<Integer> STOCK_REEDS = List.of(5, 6, 8, 10, 12, 14, 15, 16, 18, 20, 24, 30);

    /** The rack reed closest to a wanted count, and how far off it is. */
    public record Nearest(int reed, double off) {}

    /** One way to reach a sett: a reed and so many ends in each dent. */
    public record Option(int reed, int endsPerDent) {}

    private Reed() {}

    /** The reed a sett needs at two ends a dent. */
    public static double reedFor(double endsPerInch) {
        return reedFor(endsPerInch, 2);
    }

    /** The reed a sett needs, given how many ends go in each dent. */
    public static double reedFor(double endsPerInch, int endsPerDent) {
        Check.positiveNumber(endsPerInch, "endsPerInch");
        Check.positiveInteger(endsPerDent, "endsPerDent");
        return Numeric.roundTo(endsPerInch / endsPerDent, 4);
    }

    /** The stock reed closest to a wanted count. */
    public static Nearest nearestReed(double dentsPerInch) {
        return nearestReed(dentsPerInch, STOCK_REEDS);
    }

    /** The reed on a given rack closest to a wanted count, and how far off it is. */
    public static Nearest nearestReed(double dentsPerInch, List<Integer> reeds) {
        Check.positiveNumber(dentsPerInch, "dentsPerInch");
        Check.array(reeds, "reeds", 1);
        int best = reeds.get(0);
        for (int reed : reeds) {
            if (Math.abs(reed - dentsPerInch) < Math.abs(best - dentsPerInch)) {
                best = reed;
            }
        }
        return new Nearest(best, Numeric.roundTo(best - dentsPerInch, 4));
    }

    /** Every stock reed that reaches a sett at up to four ends a dent. */
    public static List<Option> reedOptions(double endsPerInch) {
        return reedOptions(endsPerInch, STOCK_REEDS, 4);
    }

    /** Every stock reed that reaches a sett at up to the given ends a dent. */
    public static List<Option> reedOptions(double endsPerInch, int maxEndsPerDent) {
        return reedOptions(endsPerInch, STOCK_REEDS, maxEndsPerDent);
    }

    /**
     * Every reed on a rack that reaches a sett with a whole number of ends per dent, which is what a
     * weaver actually wants to choose between.
     */
    public static List<Option> reedOptions(double endsPerInch, List<Integer> reeds, int maxEndsPerDent) {
        Check.positiveNumber(endsPerInch, "endsPerInch");
        Check.positiveInteger(maxEndsPerDent, "maxEndsPerDent");
        List<Option> out = new ArrayList<>();
        for (int reed : reeds) {
            for (int perDent = 1; perDent <= maxEndsPerDent; perDent += 1) {
                if (Numeric.roundTo((double) reed * perDent, 6) == Numeric.roundTo(endsPerInch, 6)) {
                    out.add(new Option(reed, perDent));
                }
            }
        }
        return List.copyOf(out);
    }

    /** Spread the ends two to a dent. */
    public static List<Integer> dentingPlan(int ends) {
        return dentingPlan(ends, List.of(2));
    }

    /**
     * Spread {@code ends} across dents following a repeating pattern, so a sett of two and a half to
     * the dent is written as {@code [3, 2]} rather than as a fraction.
     */
    public static List<Integer> dentingPlan(int ends, List<?> pattern) {
        Check.positiveInteger(ends, "ends");
        List<Object> groups = Check.array(pattern, "pattern", 1);
        List<Integer> sizes = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i += 1) {
            sizes.add(Check.positiveInteger(groups.get(i), "pattern[" + i + "]"));
        }
        List<Integer> dents = new ArrayList<>();
        int placed = 0;
        int step = 0;
        while (placed < ends) {
            int take = Math.min(sizes.get(step % sizes.size()), ends - placed);
            dents.add(take);
            placed += take;
            step += 1;
        }
        return List.copyOf(dents);
    }

    /** The average ends per dent a repeating pattern gives. */
    public static double averageEndsPerDent(List<Integer> pattern) {
        Check.array(pattern, "pattern", 1);
        return Numeric.roundTo(Numeric.sum(pattern) / pattern.size(), 4);
    }

    /** The width in the reed, in inches, for so many dents at a given reed count. */
    public static double reedWidth(int dents, double dentsPerInch) {
        Check.positiveInteger(dents, "dents");
        Check.positiveNumber(dentsPerInch, "dentsPerInch");
        return Numeric.roundTo(dents / dentsPerInch, 3);
    }

    /** Which dent each 1-based end falls in, two ends to a dent. */
    public static List<Integer> sleyOrder(int ends) {
        return sleyOrder(ends, List.of(2));
    }

    /** The full sley: which dent each 1-based end falls in. */
    public static List<Integer> sleyOrder(int ends, List<?> pattern) {
        List<Integer> dents = dentingPlan(ends, pattern);
        List<Integer> order = new ArrayList<>(ends);
        for (int index = 0; index < dents.size(); index += 1) {
            for (int n = 0; n < dents.get(index); n += 1) {
                order.add(index + 1);
            }
        }
        return List.copyOf(order);
    }

    /** Warn about a denting that will show, at a crowding limit of four ends a dent. */
    public static List<Problem> dentingWarnings(List<Integer> dents) {
        return dentingWarnings(dents, 4);
    }

    /**
     * Warn about a denting that will show. Too many ends in a dent crowds them into a stripe, and a
     * plan whose last dent is short leaves a thin edge.
     */
    public static List<Problem> dentingWarnings(List<Integer> dents, int maxEndsPerDent) {
        Check.array(dents, "dents", 1);
        List<Problem> warnings = new ArrayList<>();
        List<Integer> crowded = new ArrayList<>();
        for (int index = 0; index < dents.size(); index += 1) {
            if (dents.get(index) > maxEndsPerDent) {
                crowded.add(index + 1);
            }
        }
        if (!crowded.isEmpty()) {
            warnings.add(new Problem(
                    "crowded_dent",
                    crowded.size() + " dents carry more than " + maxEndsPerDent + " ends",
                    Json.obj("dents", crowded, "maxEndsPerDent", maxEndsPerDent)));
        }
        int first = dents.get(0);
        int last = dents.get(dents.size() - 1);
        if (dents.size() > 1 && last < first) {
            warnings.add(new Problem(
                    "short_last_dent",
                    "the last dent is not full, so the selvedge will pull in",
                    Json.obj("first", first, "last", last)));
        }
        return List.copyOf(warnings);
    }

    /** Refuse a denting the reed cannot physically hold. */
    public static List<Integer> assertDentingFits(List<Integer> dents, int dentsAvailable) {
        Check.array(dents, "dents", 1);
        Check.positiveInteger(dentsAvailable, "dentsAvailable");
        Limits.assertFits(
                dents.size(),
                dentsAvailable,
                "denting",
                "the denting needs more dents than the reed has",
                Json.obj("needed", dents.size(), "dentsAvailable", dentsAvailable));
        return dents;
    }
}
