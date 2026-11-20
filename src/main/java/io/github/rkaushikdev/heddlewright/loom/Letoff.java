package io.github.rkaushikdev.heddlewright.loom;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.List;
import java.util.Locale;

/**
 * The let-off motion.
 *
 * <p>The warp beam pays out as the cloth is taken up, and the tension in the warp is what a weight
 * or a spring holds against it. The awkward part is that the beam gets thinner as it empties, so a
 * weight that gives the right tension on a full beam gives far too much on an empty one. A negative
 * let-off lives with that; a positive one drives the beam and does not.
 */
public final class Letoff {

    /** The two kinds of let-off. */
    public enum Type {
        /** The beam is braked and the warp pulls it round. */
        NEGATIVE,
        /** The beam is driven at a rate. */
        POSITIVE;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The let-off types, negative first. */
    public static final List<Type> LET_OFF_TYPES = List.of(Type.values());

    /** How far the tension moves over a beam: full, empty, their ratio, and the rise. */
    public record Drift(double full, double empty, double ratio, double risePercent) {}

    /** Tension per end against the working limit. */
    public record SafeTension(double perEnd, double limit, boolean safe, double headroom) {}

    private static final double GRAVITY = 9.80665;

    private Letoff() {}

    /** Warp tension from a weight hung straight on the beam rope, in newtons. */
    public static double tensionFromWeight(double weightKg, double beamDiameterCm) {
        return tensionFromWeight(weightKg, 1, beamDiameterCm, 1);
    }

    /** Warp tension from a weight on a lever, in newtons. */
    public static double tensionFromWeight(double weightKg, double leverRatio, double beamDiameterCm) {
        return tensionFromWeight(weightKg, leverRatio, beamDiameterCm, 1);
    }

    /** Warp tension from a weight on a lever with the rope taken round the beam more than once. */
    public static double tensionFromWeight(double weightKg, double leverRatio, double beamDiameterCm, int ropeTurns) {
        Check.positiveNumber(weightKg, "weightKg");
        Check.positiveNumber(leverRatio, "leverRatio");
        Check.positiveNumber(beamDiameterCm, "beamDiameterCm");
        Check.positiveInteger(ropeTurns, "ropeTurns");
        double force = weightKg * GRAVITY * leverRatio * ropeTurns;
        // The braking force acts at the beam surface, so the tension it holds falls as the beam
        // empties and the radius shrinks.
        return Numeric.roundTo(force * (10 / beamDiameterCm), 3);
    }

    /** How much the tension moves over a beam with the weight hung straight on. */
    public static Drift tensionDrift(double weightKg, double fullDiameterCm, double emptyDiameterCm) {
        return tensionDrift(weightKg, 1, fullDiameterCm, emptyDiameterCm);
    }

    /**
     * How much the tension moves between a full beam and an empty one, on a negative let-off where
     * nothing compensates for the shrinking radius.
     */
    public static Drift tensionDrift(
            double weightKg, double leverRatio, double fullDiameterCm, double emptyDiameterCm) {
        double full = tensionFromWeight(weightKg, leverRatio, fullDiameterCm);
        double empty = tensionFromWeight(weightKg, leverRatio, emptyDiameterCm);
        if (emptyDiameterCm >= fullDiameterCm) {
            throw new InvalidArgumentException(
                    "an empty beam is thinner than a full one",
                    Json.obj("fullDiameterCm", fullDiameterCm, "emptyDiameterCm", emptyDiameterCm));
        }
        return new Drift(full, empty, Numeric.roundTo(empty / full, 4), Numeric.roundTo((empty / full - 1) * 100, 2));
    }

    /** The weight a wanted tension needs with no lever. */
    public static double weightForTension(double tensionNewtons, double beamDiameterCm) {
        return weightForTension(tensionNewtons, 1, beamDiameterCm);
    }

    /**
     * The weight a wanted tension needs at a given beam diameter, which is the calculation a weaver
     * actually does when they hang the lever.
     */
    public static double weightForTension(double tensionNewtons, double leverRatio, double beamDiameterCm) {
        Check.positiveNumber(tensionNewtons, "tensionNewtons");
        Check.positiveNumber(leverRatio, "leverRatio");
        Check.positiveNumber(beamDiameterCm, "beamDiameterCm");
        return Numeric.roundTo(tensionNewtons / (GRAVITY * leverRatio * (10 / beamDiameterCm)), 4);
    }

    /** Tension per end, which is what the yarn actually feels. */
    public static double tensionPerEnd(double tensionNewtons, int ends) {
        Check.positiveNumber(tensionNewtons, "tensionNewtons");
        Check.positiveInteger(ends, "ends");
        return Numeric.roundTo(tensionNewtons / ends, 5);
    }

    /** Whether the tension per end is inside fifteen per cent of the breaking load. */
    public static SafeTension withinSafeTension(double tensionNewtons, int ends, double breakingLoadN) {
        return withinSafeTension(tensionNewtons, ends, breakingLoadN, 0.15);
    }

    /**
     * Whether the tension per end is inside what the yarn will take. The usual working figure is a
     * fraction of the single end breaking load, because a warp held near its breaking load breaks
     * somewhere every few minutes.
     */
    public static SafeTension withinSafeTension(double tensionNewtons, int ends, double breakingLoadN, double share) {
        Check.positiveNumber(breakingLoadN, "breakingLoadN");
        Check.number(share, "share", Bounds.between(0.01, 1));
        double perEnd = tensionPerEnd(tensionNewtons, ends);
        double limit = Numeric.roundTo(breakingLoadN * share, 5);
        return new SafeTension(perEnd, limit, perEnd <= limit, Numeric.roundTo(limit - perEnd, 5));
    }

    /**
     * A positive let-off drives the beam at a rate, so it has to be reset as the beam empties. This
     * is how far the beam turns per pick at a given build-up.
     */
    public static double beamTurnsPerPick(double picksPerCm, double beamDiameterCm) {
        Check.positiveNumber(picksPerCm, "picksPerCm");
        Check.positiveNumber(beamDiameterCm, "beamDiameterCm");
        double circumference = Math.PI * beamDiameterCm;
        return Numeric.roundTo(1 / (picksPerCm * circumference), 8);
    }

    /** How many picks one turn of the beam pays out. */
    public static double picksPerBeamTurn(double picksPerCm, double beamDiameterCm) {
        return Numeric.roundTo(1 / beamTurnsPerPick(picksPerCm, beamDiameterCm), 2);
    }

    /** Which kind of let-off a shed is describing, checked. */
    public static Type assertLetOffType(Object type) {
        return Check.member(type, "type", Type.values());
    }
}
