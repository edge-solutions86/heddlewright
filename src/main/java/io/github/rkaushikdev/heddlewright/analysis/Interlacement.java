package io.github.rkaushikdev.heddlewright.analysis;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Indexing;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.util.Runs;
import java.util.List;
import java.util.Locale;

/**
 * Interlacement.
 *
 * <p>How often a thread changes from face to back decides how firm the cloth is and how closely it
 * can be set. Plain weave changes on every intersection and is the firmest cloth two threads can
 * make; a satin changes once per repeat and can be set far closer for the same yarn.
 */
public final class Interlacement {

    /** A one word reading of how firm a cloth is. */
    public enum Firmness {
        /** Interlacing on nine in ten intersections or more. */
        FIRM,
        /** At least half. */
        MEDIUM,
        /** At least a quarter. */
        OPEN,
        /** Less than that. */
        LOOSE;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private Interlacement() {}

    /** The number of times each end crosses from face to back, round the repeat. */
    public static List<Integer> intersectionsPerEnd(Matrix drawdown) {
        return drawdown.columns().stream()
                .map(column -> Runs.countRuns(column, 1) + Runs.countRuns(column, 0))
                .toList();
    }

    /** The same reading taken across each pick. */
    public static List<Integer> intersectionsPerPick(Matrix drawdown) {
        return drawdown.allRows().stream()
                .map(row -> Runs.countRuns(row, 1) + Runs.countRuns(row, 0))
                .toList();
    }

    /**
     * The interlacement factor: intersections counted over the whole repeat as a share of the most a
     * repeat that size could have. Plain weave reads 1.
     */
    public static double interlacementFactor(Matrix drawdown) {
        double warp = Numeric.sum(intersectionsPerEnd(drawdown));
        double weft = Numeric.sum(intersectionsPerPick(drawdown));
        double most = (double) drawdown.rows() * drawdown.cols() * 2;
        return Numeric.roundTo((warp + weft) / most, 4);
    }

    /** The mean float length implied by the intersection count, in each direction. */
    public static WarpWeft averageFloatFromIntersections(Matrix drawdown) {
        // A thread that never crosses still counts as one run, so the divisor is never zero and a
        // constant end reports a float of the whole repeat.
        List<Double> warp = intersectionsPerEnd(drawdown).stream()
                .map(count -> (double) drawdown.rows() / count)
                .toList();
        List<Double> weft = intersectionsPerPick(drawdown).stream()
                .map(count -> (double) drawdown.cols() / count)
                .toList();
        return new WarpWeft(Numeric.roundTo(Numeric.mean(warp), 4), Numeric.roundTo(Numeric.mean(weft), 4));
    }

    /**
     * Ashenhurst's intersection count for a repeat: the number of threads in the repeat divided by
     * the number of intersections one thread makes, which is the multiplier his sett rule works from.
     */
    public static WarpWeft ashenhurstDivisor(Matrix drawdown) {
        double warpIntersections = Numeric.mean(intersectionsPerEnd(drawdown));
        double weftIntersections = Numeric.mean(intersectionsPerPick(drawdown));
        return new WarpWeft(
                Numeric.roundTo(drawdown.rows() / warpIntersections, 4),
                Numeric.roundTo(drawdown.cols() / weftIntersections, 4));
    }

    /**
     * Ends that are lying on the cloth rather than woven into it. A woven end changes state at least
     * twice round the repeat, once each way; anything under that is a single unbroken run and is not
     * bound in at all.
     */
    public static List<Integer> looseEnds(Matrix drawdown) {
        return Indexing.positionsWhere(intersectionsPerEnd(drawdown), count -> count < 2);
    }

    /** A coarse reading of how firm a cloth is, for a report that has to say something in one word. */
    public static Firmness firmnessClass(Matrix drawdown) {
        double factor = interlacementFactor(drawdown);
        if (factor >= 0.9) {
            return Firmness.FIRM;
        }
        if (factor >= 0.5) {
            return Firmness.MEDIUM;
        }
        if (factor >= 0.25) {
            return Firmness.OPEN;
        }
        return Firmness.LOOSE;
    }

    /**
     * How much closer one cloth can be set than another for the same yarn, taken as the ratio of
     * their Ashenhurst divisors.
     */
    public static WarpWeft settRatio(Matrix loose, Matrix firm) {
        WarpWeft a = ashenhurstDivisor(loose);
        WarpWeft b = ashenhurstDivisor(firm);
        Check.positiveNumber(b.warp(), "firm warp divisor");
        return new WarpWeft(Numeric.roundTo(a.warp() / b.warp(), 4), Numeric.roundTo(a.weft() / b.weft(), 4));
    }
}
