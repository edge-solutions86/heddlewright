package io.github.rkaushikdev.heddlewright.warp;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Numeric;

/**
 * The warp beam.
 *
 * <p>A beam is a barrel with two flanges, and the warp winds on it as an annulus. How much fits is
 * geometry: the area of the ring between the barrel and the flange, times the width, times how
 * densely the yarn packs. Getting it wrong means either a beam that runs out mid-piece or one that
 * jams on the flanges halfway through beaming, and the second is much the more expensive.
 */
public record Beam(double barrelDiameterCm, double flangeDiameterCm, double widthCm) {

    /** The density cotton packs at on a loom beam, in grams a cubic centimetre. */
    public static final double PACKING_DENSITY = 0.5;

    /** A warp wound onto the beam: its end count, its yarn in tex, its length and how it packs. */
    public record Warp(int ends, double tex, double metres, double packingDensity) {

        /** A warp packing at the usual density. */
        public static Warp of(int ends, double tex, double metres) {
            return new Warp(ends, tex, metres, PACKING_DENSITY);
        }
    }

    /** The volume, in cubic centimetres, of the ring a full beam winds into. */
    public double windingVolumeCm3() {
        Check.positiveNumber(barrelDiameterCm, "barrelDiameterCm");
        Check.positiveNumber(flangeDiameterCm, "flangeDiameterCm");
        Check.positiveNumber(widthCm, "widthCm");
        if (flangeDiameterCm <= barrelDiameterCm) {
            throw new InvalidArgumentException(
                    "the flange has to be wider than the barrel",
                    Json.obj("barrelDiameterCm", barrelDiameterCm, "flangeDiameterCm", flangeDiameterCm));
        }
        double outer = (flangeDiameterCm / 2) * (flangeDiameterCm / 2);
        double inner = (barrelDiameterCm / 2) * (barrelDiameterCm / 2);
        return Numeric.roundTo(Math.PI * (outer - inner) * widthCm, 3);
    }

    /** How much yarn the beam holds, in kilograms, at the usual packing density. */
    public double capacityKg() {
        return capacityKg(PACKING_DENSITY);
    }

    /**
     * How much yarn the beam holds, in kilograms, at a packing density. Cotton on a loom beam packs
     * at around half a gram a cubic centimetre; a shed measures its own and passes it in.
     */
    public double capacityKg(double packingDensity) {
        Check.positiveNumber(packingDensity, "packingDensity");
        return Numeric.roundTo((windingVolumeCm3() * packingDensity) / 1000, 3);
    }

    /** How long a warp of a given end count and yarn fits on the beam, in metres. */
    public double capacityMetres(int ends, double tex) {
        return capacityMetres(ends, tex, PACKING_DENSITY);
    }

    /** How long a warp fits on the beam at a packing density, in metres. */
    public double capacityMetres(int ends, double tex, double packingDensity) {
        Check.positiveInteger(ends, "ends");
        Check.positiveNumber(tex, "tex");
        double kilograms = capacityKg(packingDensity);
        double gramsPerMetre = (ends * tex) / 1000;
        return Numeric.roundTo((kilograms * 1000) / gramsPerMetre, 1);
    }

    /** How thick the wound warp is, in centimetres of build-up. */
    public double buildUpCm(Warp warp) {
        Check.positiveNumber(warp.metres(), "metres");
        Check.positiveInteger(warp.ends(), "ends");
        Check.positiveNumber(warp.tex(), "tex");
        Check.positiveNumber(warp.packingDensity(), "packingDensity");
        double grams = (warp.ends() * warp.tex() * warp.metres()) / 1000;
        double volume = grams / warp.packingDensity();
        double inner = (barrelDiameterCm / 2) * (barrelDiameterCm / 2);
        double outer = volume / (Math.PI * widthCm) + inner;
        return Numeric.roundTo(Math.sqrt(outer) * 2 - barrelDiameterCm, 4);
    }

    /** The diameter the wound warp reaches. */
    public double woundDiameterCm(Warp warp) {
        return Numeric.roundTo(barrelDiameterCm + buildUpCm(warp), 4);
    }

    /** Refuse a warp that would wind past the flanges, or hand back the diameter it winds to. */
    public double assertFitsBeam(Warp warp) {
        double diameter = woundDiameterCm(warp);
        if (diameter > flangeDiameterCm) {
            throw new CapacityException(
                    "that warp would wind past the flanges",
                    Json.obj(
                            "diameter",
                            diameter,
                            "flangeDiameterCm",
                            flangeDiameterCm,
                            "over",
                            Numeric.roundTo(diameter - flangeDiameterCm, 4)));
        }
        return diameter;
    }

    /** How full the beam is, as a share of the ring the flanges allow. */
    public double fillShare(Warp warp) {
        double used = woundDiameterCm(warp) - barrelDiameterCm;
        double available = flangeDiameterCm - barrelDiameterCm;
        return Numeric.roundTo(used / available, 4);
    }

    /** Whether a warp fills at least two fifths of the beam. */
    public boolean isWorthBeaming(Warp warp) {
        return isWorthBeaming(warp, 0.4);
    }

    /**
     * A beam wound below a working minimum is not worth the change-over, so the plan is either to
     * lengthen the warp or to put it on a smaller beam.
     */
    public boolean isWorthBeaming(Warp warp, double minimumShare) {
        Check.number(minimumShare, "minimumShare", Bounds.between(0, 1));
        return fillShare(warp) >= minimumShare;
    }
}
