package io.github.rkaushikdev.heddlewright.loom;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.List;
import java.util.Optional;

/**
 * Temples and pulling in.
 *
 * <p>Cloth comes off the reed narrower than the warp went into it, because the weft draws the
 * outside ends inwards as it bends round them. A temple holds the cloth out at the fell so the
 * selvedge ends are not cut on the reed, and how much it has to hold depends on how much the cloth
 * would otherwise pull in, which is a function of the weft and the weave rather than of the loom.
 */
public final class Temple {

    /** The kinds of temple a shed fits. */
    public enum Kind {
        /** Spiked rings at each selvedge. */
        RING("ring"),
        /** A spiked roller at each selvedge. */
        ROLLER("roller"),
        /** A bar right across the cloth. */
        FULL_WIDTH("full width");

        private final String word;

        Kind(String word) {
            this.word = word;
        }

        @Override
        public String toString() {
            return word;
        }
    }

    /** The temple kinds, rings first. */
    public static final List<Kind> TEMPLE_KINDS = List.of(Kind.values());

    /** A temple setting read: the grip needed, the grip available, and whether it reaches. */
    public record Fit(double grip, double maxGripCm, boolean fits) {}

    /** A pull-in past the working limit. */
    public record Warning(String code, double share, double limit, String message) {}

    private Temple() {}

    /** How much narrower the cloth is than the reed, as a share. */
    public static double pullIn(double reedWidthCm, double clothWidthCm) {
        Check.positiveNumber(reedWidthCm, "reedWidthCm");
        Check.positiveNumber(clothWidthCm, "clothWidthCm");
        if (clothWidthCm > reedWidthCm) {
            throw new InvalidArgumentException(
                    "the cloth cannot be wider than the reed it came through",
                    Json.obj("reedWidthCm", reedWidthCm, "clothWidthCm", clothWidthCm));
        }
        return Numeric.roundTo((reedWidthCm - clothWidthCm) / reedWidthCm, 4);
    }

    /** The cloth width a reed width and a pull-in give. */
    public static double clothWidth(double reedWidthCm, double pullInShare) {
        Check.positiveNumber(reedWidthCm, "reedWidthCm");
        Check.number(pullInShare, "pullInShare", Bounds.between(0, 0.5));
        return Numeric.roundTo(reedWidthCm * (1 - pullInShare), 2);
    }

    /** The reed width a wanted cloth width needs, allowing for the pull-in. */
    public static double reedWidth(double clothWidthCm, double pullInShare) {
        Check.positiveNumber(clothWidthCm, "clothWidthCm");
        Check.number(pullInShare, "pullInShare", Bounds.between(0, 0.5));
        return Numeric.roundTo(clothWidthCm / (1 - pullInShare), 2);
    }

    /**
     * A working estimate of the pull-in, from the weft crimp and how firmly the cloth is set. A cloth
     * that bends its weft further round the warp pulls in further, and a closer sett resists it.
     */
    public static double estimatePullIn(double weftCrimpPercent, double coverFactor) {
        Check.number(weftCrimpPercent, "weftCrimpPercent", Bounds.atLeast(0));
        Check.positiveNumber(coverFactor, "coverFactor");
        double raw = weftCrimpPercent / 100 / (1 + coverFactor / 28);
        return Numeric.roundTo(Math.min(0.25, raw), 4);
    }

    /** How far a temple has to hold the cloth out, in centimetres. */
    public static double templeGrip(double reedWidthCm, double clothWidthCm) {
        // Called for the checks it makes: it refuses cloth wider than the reed, which would otherwise
        // come back here as a negative grip.
        pullIn(reedWidthCm, clothWidthCm);
        return Numeric.roundTo((reedWidthCm - clothWidthCm) / 2, 2);
    }

    /** Whether a shed's temple will reach across a given pull-in. */
    public static Fit templeFits(double reedWidthCm, double clothWidthCm, double maxGripCm) {
        Check.positiveNumber(maxGripCm, "maxGripCm");
        double grip = templeGrip(reedWidthCm, clothWidthCm);
        return new Fit(grip, maxGripCm, grip <= maxGripCm);
    }

    /** Refuse a setting the temple cannot hold. */
    public static Fit assertTempleFits(double reedWidthCm, double clothWidthCm, double maxGripCm) {
        Fit reading = templeFits(reedWidthCm, clothWidthCm, maxGripCm);
        if (!reading.fits()) {
            throw new CapacityException(
                    "the temple will not hold the cloth out that far", (JsonObject) Json.of(reading));
        }
        return reading;
    }

    /** A warning for a pull-in past a tenth, or nothing. */
    public static Optional<Warning> pullInWarning(double share) {
        return pullInWarning(share, 0.1);
    }

    /**
     * A pull-in past about a tenth is the point where selvedge ends start breaking on the reed,
     * whatever temple is fitted.
     */
    public static Optional<Warning> pullInWarning(double share, double limit) {
        Check.number(share, "share", Bounds.atLeast(0));
        if (share <= limit) {
            return Optional.empty();
        }
        return Optional.of(new Warning(
                "excessive_pull_in", share, limit, "the selvedge ends will be cut on the reed at that pull-in"));
    }

    /** How many selvedge ends a temple covers at a given sett. */
    public static int endsUnderTemple(double gripCm, double endsPerCm) {
        Check.positiveNumber(gripCm, "gripCm");
        Check.positiveNumber(endsPerCm, "endsPerCm");
        return (int) Math.ceil(gripCm * endsPerCm);
    }

    /** Check a temple kind. */
    public static Kind assertTempleKind(Object kind) {
        return Check.member(kind, "kind", Kind.values());
    }

    /** How many rings a width of cloth needs at five centimetres a ring. */
    public static int ringsNeeded(double clothWidthCm) {
        return ringsNeeded(clothWidthCm, 5);
    }

    /** How many temple rings a width of cloth needs, at a ring spacing. */
    public static int ringsNeeded(double clothWidthCm, double ringWidthCm) {
        Check.positiveNumber(clothWidthCm, "clothWidthCm");
        Check.positiveNumber(ringWidthCm, "ringWidthCm");
        return Check.positiveInteger((Object) Math.max(2, Math.ceil(clothWidthCm / ringWidthCm)), "rings");
    }
}
