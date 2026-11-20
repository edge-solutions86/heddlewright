package io.github.rkaushikdev.heddlewright.analysis;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.util.Runs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * Float measurement.
 *
 * <p>A warp float is a run of raised cells down one end; a weft float is a run of lowered cells
 * across one pick. Both are measured round the repeat, because the cloth does not stop at the edge
 * of the diagram, and both are what decide whether a cloth will snag, how it will drape and how
 * closely it can be set.
 */
public final class Floats {

    /** A warp float: the 1-based end, the pick it starts on, its length, and whether it wraps. */
    public record WarpFloat(int end, int start, int length, boolean wraps) {}

    /** A weft float: the 1-based pick, the end it starts on, its length, and whether it wraps. */
    public record WeftFloat(int pick, int start, int length, boolean wraps) {}

    /** A float past a limit, tagged with the direction it runs in. */
    public record Over(String direction, int thread, int start, int length, boolean wraps) {}

    /** How many floats of each length run in each direction. */
    public record Histogram(Map<Integer, Integer> warp, Map<Integer, Integer> weft) {}

    /**
     * The longest float either way, and which thread carries it. A direction with no float at all
     * reads zero, and names no thread.
     */
    public record Longest(int warp, int weft, int longest, OptionalInt onEnd, OptionalInt onPick)
            implements Json.Writable {

        /** Written with a null thread where a direction never floats, as a report prints it. */
        @Override
        public Json toJson() {
            return Json.obj(
                    "warp", warp,
                    "weft", weft,
                    "longest", longest,
                    "onEnd", onEnd.isPresent() ? onEnd.getAsInt() : Json.NULL,
                    "onPick", onPick.isPresent() ? onPick.getAsInt() : Json.NULL);
        }
    }

    private Floats() {}

    /** Every warp float in the repeat. */
    public static List<WarpFloat> warpFloats(Matrix drawdown) {
        List<WarpFloat> out = new ArrayList<>();
        List<List<Integer>> columns = drawdown.columns();
        for (int e = 0; e < columns.size(); e += 1) {
            for (Runs.Run<Integer> run : Runs.cyclicRuns(columns.get(e))) {
                if (run.value() == 1) {
                    out.add(new WarpFloat(e + 1, run.start() + 1, run.length(), run.wraps()));
                }
            }
        }
        return List.copyOf(out);
    }

    /** Every weft float in the repeat. */
    public static List<WeftFloat> weftFloats(Matrix drawdown) {
        List<WeftFloat> out = new ArrayList<>();
        for (int p = 0; p < drawdown.rows(); p += 1) {
            for (Runs.Run<Integer> run : Runs.cyclicRuns(drawdown.row(p))) {
                if (run.value() == 0) {
                    out.add(new WeftFloat(p + 1, run.start() + 1, run.length(), run.wraps()));
                }
            }
        }
        return List.copyOf(out);
    }

    /** The longest float either way, and which thread carries it. */
    public static Longest longestFloat(Matrix drawdown) {
        WarpFloat warp = null;
        for (WarpFloat found : warpFloats(drawdown)) {
            if (warp == null || found.length() > warp.length()) {
                warp = found;
            }
        }
        WeftFloat weft = null;
        for (WeftFloat found : weftFloats(drawdown)) {
            if (weft == null || found.length() > weft.length()) {
                weft = found;
            }
        }
        int warpLength = warp == null ? 0 : warp.length();
        int weftLength = weft == null ? 0 : weft.length();
        return new Longest(
                warpLength,
                weftLength,
                Math.max(warpLength, weftLength),
                warp == null ? OptionalInt.empty() : OptionalInt.of(warp.end()),
                weft == null ? OptionalInt.empty() : OptionalInt.of(weft.pick()));
    }

    /** Floats longer than the limit, both ways, ready to print in a report. */
    public static List<Over> floatsOver(Matrix drawdown, int limit) {
        Check.positiveInteger(limit, "limit");
        List<Over> out = new ArrayList<>();
        for (WarpFloat found : warpFloats(drawdown)) {
            if (found.length() > limit) {
                out.add(new Over("warp", found.end(), found.start(), found.length(), found.wraps()));
            }
        }
        for (WeftFloat found : weftFloats(drawdown)) {
            if (found.length() > limit) {
                out.add(new Over("weft", found.pick(), found.start(), found.length(), found.wraps()));
            }
        }
        return List.copyOf(out);
    }

    /** A count of floats by length, for each direction. */
    public static Histogram floatHistogram(Matrix drawdown) {
        Map<Integer, Integer> warp = new TreeMap<>();
        Map<Integer, Integer> weft = new TreeMap<>();
        warpFloats(drawdown).forEach(found -> warp.merge(found.length(), 1, Integer::sum));
        weftFloats(drawdown).forEach(found -> weft.merge(found.length(), 1, Integer::sum));
        return new Histogram(java.util.Collections.unmodifiableMap(warp), java.util.Collections.unmodifiableMap(weft));
    }

    /** The mean float length in each direction, which reads as cloth firmness. */
    public static WarpWeft meanFloatLength(Matrix drawdown) {
        return new WarpWeft(
                Numeric.meanBy(warpFloats(drawdown), WarpFloat::length, "floats"),
                Numeric.meanBy(weftFloats(drawdown), WeftFloat::length, "floats"));
    }

    /** The float limit for a sett, taking a tenth of an inch as the longest a float may span. */
    public static int floatLimitForSett(int threadsPerInch) {
        return floatLimitForSett(threadsPerInch, 0.1);
    }

    /**
     * The float limit a cloth can carry before it snags, taken from the sett: the usual shed rule is
     * that a float should not span more than about a tenth of an inch, so the limit falls as the
     * cloth is set closer.
     */
    public static int floatLimitForSett(int threadsPerInch, double spanInches) {
        Check.positiveInteger(threadsPerInch, "threadsPerInch");
        return Math.max(1, (int) Math.floor(threadsPerInch * spanInches));
    }

    /** True when nothing in the cloth floats further than the limit. */
    public static boolean withinFloatLimit(Matrix drawdown, int limit) {
        Check.integer(limit, "limit", Bounds.atLeast(1));
        return longestFloat(drawdown).longest() <= limit;
    }

    /** The longest float down a single named end, 1-based. */
    public static int floatOnEnd(Matrix drawdown, int end) {
        Check.positiveInteger(end, "end");
        return Runs.longestRun(drawdown.column(end - 1), 1);
    }

    /** The longest float across a single named pick, 1-based. */
    public static int floatOnPick(Matrix drawdown, int pick) {
        Check.positiveInteger(pick, "pick");
        return Runs.longestRun(drawdown.row(pick - 1), 0);
    }
}
