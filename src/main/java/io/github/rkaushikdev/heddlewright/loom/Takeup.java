package io.github.rkaushikdev.heddlewright.loom;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.List;

/**
 * The take-up motion.
 *
 * <p>A ratchet on the take-up shaft is advanced one tooth every pick, and a train of wheels carries
 * that movement to the cloth roller. Change one wheel and the cloth moves a different distance per
 * pick, which is how a weaver sets the picks per inch. In the shed the whole train is collapsed into
 * a single number called the dividend, and the setting is the dividend over the change wheel.
 *
 * <pre>
 *   picks per inch = dividend / change wheel teeth
 * </pre>
 *
 * <p>so the arithmetic a weaver actually does is one division, and everything else is worked out
 * once when the loom is set up.
 */
public final class Takeup {

    /** The wheel that lands nearest a wanted sett, what it gives, and how far off that is. */
    public record BestWheel(int teeth, double achieved, double error, double exact, double errorPercent) {}

    /** One line of a setting card: a wheel and the sett it gives. */
    public record Setting(int teeth, double picksPerInch) {}

    /** The loosest and closest setts a drawer of wheels can reach. */
    public record SettRange(double lowest, double highest) {}

    private static final List<Integer> STOCK =
            List.of(24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50, 52, 56, 60);

    private Takeup() {}

    /** Change wheels a shed keeps in the drawer, in teeth. */
    public static List<Integer> stockChangeWheels() {
        return STOCK;
    }

    /**
     * The dividend for a seven-wheel take-up. The ratchet, the carrier and the wheel on the roller
     * shaft all raise it; the stud wheel and the roller circumference lower it.
     */
    public static double takeUpDividend(
            int ratchetTeeth, int carrierTeeth, int studTeeth, int rollerWheelTeeth, double rollerDiameterInches) {
        Check.positiveInteger(ratchetTeeth, "ratchetTeeth");
        Check.positiveInteger(carrierTeeth, "carrierTeeth");
        Check.positiveInteger(studTeeth, "studTeeth");
        Check.positiveInteger(rollerWheelTeeth, "rollerWheelTeeth");
        Check.positiveNumber(rollerDiameterInches, "rollerDiameterInches");
        double circumference = Math.PI * rollerDiameterInches;
        return Numeric.roundTo(
                ((double) ratchetTeeth * carrierTeeth * rollerWheelTeeth) / (studTeeth * circumference), 4);
    }

    /** The picks per inch a change wheel gives on a known dividend. */
    public static double picksPerInch(double dividend, double changeWheelTeeth) {
        return picksOn(dividend, changeWheelTeeth);
    }

    private static double picksOn(double dividend, Object changeWheelTeeth) {
        Check.positiveNumber(dividend, "dividend");
        int teeth = Check.positiveInteger(changeWheelTeeth, "changeWheelTeeth");
        return Numeric.roundTo(dividend / teeth, 3);
    }

    /** How far the cloth moves on each pick, in inches. */
    public static double clothPerPick(double dividend, double changeWheelTeeth) {
        return Numeric.roundTo(1 / picksPerInch(dividend, changeWheelTeeth), 6);
    }

    /** The change wheel a wanted sett needs, before rounding to a real wheel. */
    public static double exactChangeWheel(double dividend, double wantedPicksPerInch) {
        Check.positiveNumber(dividend, "dividend");
        Check.positiveNumber(wantedPicksPerInch, "wantedPicksPerInch");
        return Numeric.roundTo(dividend / wantedPicksPerInch, 3);
    }

    /** The best wheel in the stock drawer for a wanted sett. */
    public static BestWheel bestChangeWheel(double dividend, double wantedPicksPerInch) {
        return bestChangeWheel(dividend, wantedPicksPerInch, STOCK);
    }

    /**
     * The best wheel in a drawer for a wanted sett, with what it actually gives and how far off that
     * is. The error matters: a wheel two teeth out shows on the cloth as a pick density the customer
     * measures.
     */
    public static BestWheel bestChangeWheel(double dividend, double wantedPicksPerInch, List<?> wheels) {
        List<Object> drawer = Check.array(wheels, "wheels", 1);
        double exact = exactChangeWheel(dividend, wantedPicksPerInch);
        int bestTeeth = 0;
        double bestAchieved = 0;
        double bestError = Double.NaN;
        for (Object wheel : drawer) {
            int teeth = Check.positiveInteger(wheel, "wheel");
            double achieved = picksPerInch(dividend, teeth);
            double error = Math.abs(achieved - wantedPicksPerInch);
            // The running best is held rounded, as it is reported, so a later wheel has to beat the
            // rounded figure and not the raw one.
            if (Double.isNaN(bestError) || error < bestError) {
                bestTeeth = teeth;
                bestAchieved = achieved;
                bestError = Numeric.roundTo(error, 3);
            }
        }
        return new BestWheel(
                bestTeeth, bestAchieved, bestError, exact, Numeric.roundTo((bestError / wantedPicksPerInch) * 100, 3));
    }

    /** Every wheel in the stock drawer with the sett it gives, for a setting card. */
    public static List<Setting> settingCard(double dividend) {
        return settingCard(dividend, STOCK);
    }

    /** Every wheel in a drawer with the sett it gives, for a setting card. */
    public static List<Setting> settingCard(double dividend, List<?> wheels) {
        return Check.array(wheels, "wheels").stream()
                .map(wheel -> {
                    double picks = picksOn(dividend, wheel);
                    return new Setting(Check.integer(wheel, "changeWheelTeeth"), picks);
                })
                .toList();
    }

    /** The sett range the stock drawer can reach on a given dividend. */
    public static SettRange settRange(double dividend) {
        return settRange(dividend, STOCK);
    }

    /**
     * The sett range a drawer of wheels can reach on a given dividend. An empty drawer reaches
     * nothing, and reads as the lowest sett at infinity and the highest at minus infinity.
     */
    public static SettRange settRange(double dividend, List<?> wheels) {
        List<Setting> card = settingCard(dividend, wheels);
        double lowest = Double.POSITIVE_INFINITY;
        double highest = Double.NEGATIVE_INFINITY;
        for (Setting entry : card) {
            lowest = Math.min(lowest, entry.picksPerInch());
            highest = Math.max(highest, entry.picksPerInch());
        }
        return new SettRange(lowest, highest);
    }

    /** Refuse a sett the stock drawer cannot reach within one pick an inch. */
    public static BestWheel assertSettReachable(double dividend, double wantedPicksPerInch) {
        return assertSettReachable(dividend, wantedPicksPerInch, 1, STOCK);
    }

    /** Refuse a sett the stock drawer cannot reach within a tolerance. */
    public static BestWheel assertSettReachable(double dividend, double wantedPicksPerInch, double tolerance) {
        return assertSettReachable(dividend, wantedPicksPerInch, tolerance, STOCK);
    }

    /** Refuse a sett a drawer cannot reach within a tolerance. */
    public static BestWheel assertSettReachable(
            double dividend, double wantedPicksPerInch, double tolerance, List<?> wheels) {
        Check.positiveNumber(tolerance, "tolerance");
        BestWheel best = bestChangeWheel(dividend, wantedPicksPerInch, wheels);
        if (best.error() > tolerance) {
            throw new InvalidArgumentException(
                    "no change wheel reaches that sett closely enough",
                    Json.obj(
                            "wantedPicksPerInch",
                            wantedPicksPerInch,
                            "closest",
                            best.achieved(),
                            "teeth",
                            best.teeth(),
                            "tolerance",
                            tolerance));
        }
        return best;
    }
}
