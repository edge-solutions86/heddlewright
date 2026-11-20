package io.github.rkaushikdev.heddlewright.analysis;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Numeric;

/**
 * Crimp and take-up.
 *
 * <p>A thread does not lie straight in cloth: it rides over and under its neighbours, so a yard of
 * cloth eats more than a yard of yarn. Crimp measures that against the cloth, take-up measures it
 * against the yarn, and the two are different numbers for the same fact, which is where warp
 * calculations most often go wrong.
 */
public final class Crimp {

    /** A full warp length: the finished cloth, the length woven, and the total with loom waste. */
    public record WarpLength(double finished, double woven, double total, double loomWaste) {}

    private Crimp() {}

    private static void yarnCoversCloth(double yarnLength, double clothLength) {
        Check.positiveNumber(yarnLength, "yarnLength");
        Check.positiveNumber(clothLength, "clothLength");
        if (yarnLength < clothLength) {
            throw new InvalidArgumentException(
                    "the yarn cannot be shorter than the cloth it wove",
                    Json.obj("yarnLength", yarnLength, "clothLength", clothLength));
        }
    }

    /** Crimp as a percentage of the cloth length. */
    public static double crimpPercent(double yarnLength, double clothLength) {
        yarnCoversCloth(yarnLength, clothLength);
        return Numeric.roundTo(((yarnLength - clothLength) / clothLength) * 100, 4);
    }

    /** Take-up as a percentage of the yarn length. */
    public static double takeUpPercent(double yarnLength, double clothLength) {
        yarnCoversCloth(yarnLength, clothLength);
        return Numeric.roundTo(((yarnLength - clothLength) / yarnLength) * 100, 4);
    }

    /** Take-up from crimp, which is the conversion people get backwards. */
    public static double takeUpFromCrimp(double crimp) {
        Check.number(crimp, "crimp", Bounds.atLeast(0));
        return Numeric.roundTo((crimp / (100 + crimp)) * 100, 4);
    }

    /** Crimp from take-up, the other way round. */
    public static double crimpFromTakeUp(double takeUp) {
        Check.number(takeUp, "takeUp", Bounds.between(0, 99.999));
        return Numeric.roundTo((takeUp / (100 - takeUp)) * 100, 4);
    }

    /** The yarn a length of cloth needs at a given crimp. */
    public static double yarnForCloth(double clothLength, double crimp) {
        Check.positiveNumber(clothLength, "clothLength");
        Check.number(crimp, "crimp", Bounds.atLeast(0));
        return Numeric.roundTo(clothLength * (1 + crimp / 100), 4);
    }

    /** The cloth a length of yarn makes at a given crimp. */
    public static double clothFromYarn(double yarnLength, double crimp) {
        Check.positiveNumber(yarnLength, "yarnLength");
        Check.number(crimp, "crimp", Bounds.atLeast(0));
        return Numeric.roundTo(yarnLength / (1 + crimp / 100), 4);
    }

    /** The warp length for a cloth with no crimp, loom waste or shrinkage. */
    public static WarpLength warpLength(double clothLength) {
        return warpLength(clothLength, 0, 0, 0);
    }

    /**
     * A full warp length: the cloth, plus crimp, plus the loom waste that never becomes cloth at all,
     * plus a shrinkage allowance for wet finishing.
     */
    public static WarpLength warpLength(double clothLength, double crimp, double loomWaste, double shrinkage) {
        Check.positiveNumber(clothLength, "clothLength");
        Check.number(crimp, "crimp", Bounds.atLeast(0));
        Check.number(loomWaste, "loomWaste", Bounds.atLeast(0));
        Check.number(shrinkage, "shrinkage", Bounds.between(0, 99));
        double finished = clothLength / (1 - shrinkage / 100);
        double onLoom = finished * (1 + crimp / 100);
        return new WarpLength(
                Numeric.roundTo(finished, 4),
                Numeric.roundTo(onLoom, 4),
                Numeric.roundTo(onLoom + loomWaste, 4),
                Numeric.roundTo(loomWaste, 4));
    }

    /**
     * Crimp splits between the two directions: the more one system is set, the more the other has to
     * bend round it. This is the usual working split, given the total crimp a cloth shows and the
     * ratio of the two setts.
     */
    public static WarpWeft splitCrimp(double totalCrimp, double endsPerInch, double picksPerInch) {
        Check.number(totalCrimp, "totalCrimp", Bounds.atLeast(0));
        Check.positiveNumber(endsPerInch, "endsPerInch");
        Check.positiveNumber(picksPerInch, "picksPerInch");
        double total = endsPerInch + picksPerInch;
        return new WarpWeft(
                Numeric.roundTo((totalCrimp * picksPerInch) / total, 4),
                Numeric.roundTo((totalCrimp * endsPerInch) / total, 4));
    }

    /** How much yarn a whole warp needs at no crimp, in metres. */
    public static double warpYarnMetres(double ends, double lengthMetres) {
        return warpYarnMetres(ends, lengthMetres, 0);
    }

    /** How much yarn a whole warp needs, in metres, ends counted. */
    public static double warpYarnMetres(double ends, double lengthMetres, double crimp) {
        Check.positiveNumber(ends, "ends");
        Check.positiveNumber(lengthMetres, "lengthMetres");
        return Numeric.roundTo(ends * yarnForCloth(lengthMetres, crimp), 3);
    }

    /** How much weft a piece needs at no crimp, in metres. */
    public static double weftYarnMetres(double picksPerMetre, double lengthMetres, double widthMetres) {
        return weftYarnMetres(picksPerMetre, lengthMetres, widthMetres, 0);
    }

    /** How much weft a piece needs, in metres. */
    public static double weftYarnMetres(double picksPerMetre, double lengthMetres, double widthMetres, double crimp) {
        Check.positiveNumber(picksPerMetre, "picksPerMetre");
        Check.positiveNumber(lengthMetres, "lengthMetres");
        Check.positiveNumber(widthMetres, "widthMetres");
        double picks = picksPerMetre * lengthMetres;
        return Numeric.roundTo(picks * yarnForCloth(widthMetres, crimp), 3);
    }
}
