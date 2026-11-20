package io.github.rkaushikdev.heddlewright.analysis;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.yarn.Count;

/**
 * Sett: how many threads to the inch a cloth can carry.
 *
 * <p>Ashenhurst's rule starts from the number of yarn diameters that fit in an inch and takes off an
 * allowance for the crossings, because every crossing lifts one thread over another and uses up
 * room. The more a weave interlaces, the more room it needs, so plain weave sets the most openly and
 * a satin the closest for the same yarn:
 *
 * <pre>sett = threads in repeat / (threads in repeat + intersections) x diameters</pre>
 */
public final class Sett {

    /** Ashenhurst's sett: the diameters an inch holds, and the ends and picks the weave allows. */
    public record Ashenhurst(double diameters, double warp, double weft) {}

    /** Peirce's cover factor for each direction and for the cloth. */
    public record Cover(double warp, double weft, double cloth) {}

    private Sett() {}

    /** The most threads of a cotton count yarn that fit in an inch lying side by side. */
    public static double maximumSett(double count) {
        return Count.threadsPerInch(count);
    }

    /** The most threads of a yarn that fit in an inch lying side by side. */
    public static double maximumSett(double count, String system) {
        return Count.threadsPerInch(count, system);
    }

    /** The same on a diameter constant for another fibre. */
    public static double maximumSett(double count, String system, double constant) {
        return Count.threadsPerInch(count, system, constant);
    }

    /** Ashenhurst's sett for a cloth in a cotton count yarn, read off its own drawdown. */
    public static Ashenhurst ashenhurstSett(Matrix drawdown, double count) {
        return ashenhurstSett(drawdown, count, "ne", 28);
    }

    /** Ashenhurst's sett for a cloth in a yarn counted in any system. */
    public static Ashenhurst ashenhurstSett(Matrix drawdown, double count, String system) {
        return ashenhurstSett(drawdown, count, system, 28);
    }

    /** Ashenhurst's sett on a diameter constant for another fibre. */
    public static Ashenhurst ashenhurstSett(Matrix drawdown, double count, String system, double constant) {
        int rows = drawdown.rows();
        int cols = drawdown.cols();
        double diameters = Count.threadsPerInch(count, system, constant);
        double warpIntersections = Numeric.mean(Interlacement.intersectionsPerEnd(drawdown));
        double weftIntersections = Numeric.mean(Interlacement.intersectionsPerPick(drawdown));
        return new Ashenhurst(
                diameters,
                Numeric.roundTo((cols / (cols + weftIntersections)) * diameters, 2),
                Numeric.roundTo((rows / (rows + warpIntersections)) * diameters, 2));
    }

    /**
     * The share of the maximum a weave may be set at, which is the part of the rule that depends only
     * on the interlacement and not on the yarn.
     */
    public static WarpWeft settFactor(Matrix drawdown) {
        int rows = drawdown.rows();
        int cols = drawdown.cols();
        double warpIntersections = Numeric.mean(Interlacement.intersectionsPerEnd(drawdown));
        double weftIntersections = Numeric.mean(Interlacement.intersectionsPerPick(drawdown));
        return new WarpWeft(
                Numeric.roundTo(cols / (cols + weftIntersections), 4),
                Numeric.roundTo(rows / (rows + warpIntersections), 4));
    }

    /** A working sett at two thirds of the theoretical one, the usual start for a cloth meant to drape. */
    public static double workingSett(double theoretical) {
        return workingSett(theoretical, 0.67);
    }

    /** A working sett, which a designer takes as a share of the theoretical figure. */
    public static double workingSett(double theoretical, double share) {
        Check.positiveNumber(theoretical, "theoretical");
        Check.positiveNumber(share, "share");
        return Numeric.roundTo(theoretical * share, 1);
    }

    /**
     * Peirce's cloth cover factor. Each direction is threads per inch over the square root of the
     * cotton count, and the cloth figure takes off the overlap where warp and weft cover the same
     * ground.
     */
    public static Cover coverFactor(double endsPerInch, double picksPerInch, double warpCount, double weftCount) {
        Check.positiveNumber(endsPerInch, "endsPerInch");
        Check.positiveNumber(picksPerInch, "picksPerInch");
        Check.positiveNumber(warpCount, "warpCount");
        Check.positiveNumber(weftCount, "weftCount");
        double warp = endsPerInch / Math.sqrt(warpCount);
        double weft = picksPerInch / Math.sqrt(weftCount);
        return new Cover(
                Numeric.roundTo(warp, 2),
                Numeric.roundTo(weft, 2),
                Numeric.roundTo(warp + weft - (warp * weft) / 28, 2));
    }

    /** A cover factor at or above 28 in one direction means the cloth is jammed. */
    public static boolean isJammed(Cover cover) {
        return cover.warp() >= 28 || cover.weft() >= 28;
    }

    /** How many ends a cloth width takes at a given sett. */
    public static long endsForWidth(double widthInches, double endsPerInch) {
        Check.positiveNumber(widthInches, "widthInches");
        Check.positiveNumber(endsPerInch, "endsPerInch");
        return Math.round(widthInches * endsPerInch);
    }

    /** The width a given end count occupies at a sett, in inches. */
    public static double widthForEnds(int ends, double endsPerInch) {
        Check.positiveInteger(ends, "ends");
        Check.positiveNumber(endsPerInch, "endsPerInch");
        return Numeric.roundTo(ends / endsPerInch, 3);
    }
}
