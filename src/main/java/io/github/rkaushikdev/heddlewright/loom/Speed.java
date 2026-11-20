package io.github.rkaushikdev.heddlewright.loom;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Numeric;

/**
 * Loom speed and what it turns into.
 *
 * <p>A loom is quoted in picks a minute, but nobody buys picks. What matters is cloth an hour, and
 * that depends on the pick density as much as on the speed: the same loom running the same speed
 * makes half as much cloth at twice the picks per inch. Weft insertion rate is the figure that lets
 * two looms of different widths be compared honestly.
 */
public final class Speed {

    /** A loom as speed sees it: how fast it picks and how wide the weft has to fly. */
    public record Machine(double picksMinute, double reedWidthMetres) {}

    /** Two looms compared on insertion rate: each rate, which is faster, and by what ratio. */
    public record Comparison(double left, double right, String faster, double ratio) {}

    private Speed() {}

    /** Picks a minute from the crank speed, one pick a turn. */
    public static double picksPerMinute(double crankRpm) {
        return picksPerMinute(crankRpm, 1);
    }

    /** Picks a minute from the crank speed. */
    public static double picksPerMinute(double crankRpm, double picksPerTurn) {
        Check.positiveNumber(crankRpm, "crankRpm");
        Check.positiveNumber(picksPerTurn, "picksPerTurn");
        return Numeric.roundTo(crankRpm * picksPerTurn, 2);
    }

    /**
     * Weft insertion rate in metres a minute, which is what lets a narrow fast loom be compared with
     * a wide slow one.
     */
    public static double weftInsertionRate(double picksMinute, double reedWidthMetres) {
        Check.positiveNumber(picksMinute, "picksMinute");
        Check.positiveNumber(reedWidthMetres, "reedWidthMetres");
        return Numeric.roundTo(picksMinute * reedWidthMetres, 2);
    }

    /** Cloth woven in an hour, in metres, at full running. */
    public static double clothPerHourMetres(double picksMinute, double picksPerCm) {
        return clothPerHourMetres(picksMinute, picksPerCm, 1);
    }

    /** Cloth woven in an hour, in metres, at an efficiency. */
    public static double clothPerHourMetres(double picksMinute, double picksPerCm, double efficiency) {
        Check.positiveNumber(picksMinute, "picksMinute");
        Check.positiveNumber(picksPerCm, "picksPerCm");
        Check.number(efficiency, "efficiency", Bounds.between(0, 1));
        double cmPerHour = ((picksMinute * 60) / picksPerCm) * efficiency;
        return Numeric.roundTo(cmPerHour / 100, 4);
    }

    /** The picks a run of cloth needs. */
    public static long picksForCloth(double metres, double picksPerCm) {
        Check.positiveNumber(metres, "metres");
        Check.positiveNumber(picksPerCm, "picksPerCm");
        return Math.round(metres * 100 * picksPerCm);
    }

    /** How long a run takes, in hours, at full running. */
    public static double runHours(double metres, double picksPerCm, double picksMinute) {
        return runHours(metres, picksPerCm, picksMinute, 1);
    }

    /** How long a run takes, in hours, at a given speed and efficiency. */
    public static double runHours(double metres, double picksPerCm, double picksMinute, double efficiency) {
        long picks = picksForCloth(metres, picksPerCm);
        Check.number(efficiency, "efficiency", Bounds.between(0.0001, 1));
        return Numeric.roundTo(picks / (picksMinute * 60 * efficiency), 3);
    }

    /** Production over an eight hour shift at full running, in metres. */
    public static double shiftProduction(double picksMinute, double picksPerCm) {
        return shiftProduction(8, picksMinute, picksPerCm, 1);
    }

    /** Production over an eight hour shift at an efficiency, in metres. */
    public static double shiftProduction(double picksMinute, double picksPerCm, double efficiency) {
        return shiftProduction(8, picksMinute, picksPerCm, efficiency);
    }

    /** Production over a shift, in metres. */
    public static double shiftProduction(double hours, double picksMinute, double picksPerCm, double efficiency) {
        Check.positiveNumber(hours, "hours");
        return Numeric.roundTo(clothPerHourMetres(picksMinute, picksPerCm, efficiency) * hours, 3);
    }

    /** How long a weft package lasts at no weft crimp, in minutes. */
    public static double weftPackageMinutes(double packageMetres, double reedWidthMetres, double picksMinute) {
        return weftPackageMinutes(packageMetres, reedWidthMetres, picksMinute, 0);
    }

    /**
     * How long a weft package lasts before the shuttle has to be changed, in minutes, from the length
     * on the package and the width being woven.
     */
    public static double weftPackageMinutes(
            double packageMetres, double reedWidthMetres, double picksMinute, double crimp) {
        Check.positiveNumber(packageMetres, "packageMetres");
        Check.positiveNumber(reedWidthMetres, "reedWidthMetres");
        Check.positiveNumber(picksMinute, "picksMinute");
        Check.number(crimp, "crimp", Bounds.atLeast(0));
        double perPick = reedWidthMetres * (1 + crimp / 100);
        return Numeric.roundTo((packageMetres / perPick) / picksMinute, 3);
    }

    /** How many looms one weaver can mind, leaving fifteen per cent of the hour spare. */
    public static int loomsPerWeaver(double attentionMinutesPerLoomHour) {
        return loomsPerWeaver(attentionMinutesPerLoomHour, 0.85);
    }

    /** How many looms one weaver can mind, given the time each one needs an hour. */
    public static int loomsPerWeaver(double attentionMinutesPerLoomHour, double allowance) {
        Check.positiveNumber(attentionMinutesPerLoomHour, "attentionMinutesPerLoomHour");
        Check.number(allowance, "allowance", Bounds.between(0.01, 1));
        return (int) Math.floor((60 * allowance) / attentionMinutesPerLoomHour);
    }

    /** Two looms compared on insertion rate rather than on picks a minute. */
    public static Comparison compareLooms(Machine a, Machine b) {
        double rateA = weftInsertionRate(a.picksMinute(), a.reedWidthMetres());
        double rateB = weftInsertionRate(b.picksMinute(), b.reedWidthMetres());
        return new Comparison(
                rateA,
                rateB,
                rateA >= rateB ? "left" : "right",
                Numeric.roundTo(Math.max(rateA, rateB) / Math.min(rateA, rateB), 3));
    }
}
