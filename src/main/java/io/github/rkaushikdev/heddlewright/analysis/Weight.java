package io.github.rkaushikdev.heddlewright.analysis;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.yarn.Count;

/**
 * Cloth weight from construction.
 *
 * <p>A square metre of cloth holds one hundred times the ends per centimetre in metres of warp,
 * each longer than the cloth by its crimp, and the same again in weft. Multiply that length by the
 * yarn's tex and the grams fall out. The whole calculation is two lines, and getting the crimp on
 * the right system is the only place it goes wrong:
 *
 * <pre>grams per square metre = 0.1 x threads per cm x (1 + crimp) x tex</pre>
 */
public final class Weight {

    /** A cloth's weight split by system, and the share the warp carries. */
    public record ClothWeight(double warp, double weft, double total, double warpShare) {}

    /** What the yarn in a square metre costs, by system. */
    public record YarnCost(double warp, double weft, double total) {}

    private static final double OUNCES_PER_GRAM = 1 / 28.349523125;
    private static final double SQUARE_YARDS_PER_SQUARE_METRE = 1 / 0.83612736;

    private Weight() {}

    /** The grams a square metre of warp weighs. */
    public static double warpWeightGsm(Construction construction) {
        Check.positiveNumber(construction.endsPerCm(), "endsPerCm");
        Check.number(construction.warpCrimp(), "warpCrimp", Bounds.atLeast(0));
        double tex = Count.toTex(construction.warpCount(), construction.system());
        return Numeric.roundTo(0.1 * construction.endsPerCm() * (1 + construction.warpCrimp() / 100) * tex, 3);
    }

    /** The grams a square metre of weft weighs. */
    public static double weftWeightGsm(Construction construction) {
        Check.positiveNumber(construction.picksPerCm(), "picksPerCm");
        Check.number(construction.weftCrimp(), "weftCrimp", Bounds.atLeast(0));
        double tex = Count.toTex(construction.weftCount(), construction.system());
        return Numeric.roundTo(0.1 * construction.picksPerCm() * (1 + construction.weftCrimp() / 100) * tex, 3);
    }

    /** The whole cloth, split so a designer can see which system is carrying it. */
    public static ClothWeight clothWeightGsm(Construction construction) {
        double warp = warpWeightGsm(construction);
        double weft = weftWeightGsm(construction);
        return new ClothWeight(warp, weft, Numeric.roundTo(warp + weft, 3), Numeric.roundTo(warp / (warp + weft), 4));
    }

    /** Grams a square metre in ounces a square yard, the other trade unit. */
    public static double gsmToOuncesPerSquareYard(double gsm) {
        Check.positiveNumber(gsm, "gsm");
        return Numeric.roundTo((gsm * OUNCES_PER_GRAM) / SQUARE_YARDS_PER_SQUARE_METRE, 3);
    }

    /** Ounces a square yard back to grams a square metre. */
    public static double ouncesPerSquareYardToGsm(double ounces) {
        Check.positiveNumber(ounces, "ounces");
        return Numeric.roundTo((ounces * SQUARE_YARDS_PER_SQUARE_METRE) / OUNCES_PER_GRAM, 3);
    }

    /** What a length of cloth weighs, in kilograms. */
    public static double pieceWeightKg(double gsm, double metres, double widthMetres) {
        Check.positiveNumber(gsm, "gsm");
        Check.positiveNumber(metres, "metres");
        Check.positiveNumber(widthMetres, "widthMetres");
        return Numeric.roundTo((gsm * metres * widthMetres) / 1000, 3);
    }

    /** The running weight of a cloth, grams per linear metre at its own width. */
    public static double gramsPerLinearMetre(double gsm, double widthMetres) {
        Check.positiveNumber(gsm, "gsm");
        Check.positiveNumber(widthMetres, "widthMetres");
        return Numeric.roundTo(gsm * widthMetres, 3);
    }

    /**
     * The yarn cost of a square metre, from the two counts and the two prices. It follows the same
     * split as the weight, which is what makes a warp-heavy cloth expensive when the warp yarn is the
     * dear one.
     */
    public static YarnCost yarnCostPerSquareMetre(
            Construction construction, double warpPricePerKg, double weftPricePerKg) {
        Check.positiveNumber(warpPricePerKg, "warpPricePerKg");
        Check.positiveNumber(weftPricePerKg, "weftPricePerKg");
        ClothWeight weight = clothWeightGsm(construction);
        double warp = (weight.warp() / 1000) * warpPricePerKg;
        double weft = (weight.weft() / 1000) * weftPricePerKg;
        return new YarnCost(Numeric.roundTo(warp, 4), Numeric.roundTo(weft, 4), Numeric.roundTo(warp + weft, 4));
    }

    /** The picks per centimetre a target weight needs, holding everything else in the construction. */
    public static double picksForWeight(double targetGsm, Construction construction) {
        Check.positiveNumber(targetGsm, "targetGsm");
        double remaining = targetGsm - warpWeightGsm(construction);
        if (remaining <= 0) {
            return 0;
        }
        double tex = Count.toTex(construction.weftCount(), construction.system());
        return Numeric.roundTo(remaining / (0.1 * (1 + construction.weftCrimp() / 100) * tex), 3);
    }
}
