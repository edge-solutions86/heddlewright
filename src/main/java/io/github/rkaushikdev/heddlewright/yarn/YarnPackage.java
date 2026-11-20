package io.github.rkaushikdev.heddlewright.yarn;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * A yarn package.
 *
 * <p>Yarn arrives on cones, cheeses, pirns or beams, and what matters about a package is how much
 * length is on it, because that is what decides when a loom stops. Weight is what the yarn is bought
 * by and length is what it is used by, and the count is the only thing that connects them.
 */
public record YarnPackage(
        Kind kind, double grossKg, double tareKg, double netKg, double count, String system, double metres) {

    /** What the yarn is wound on. */
    public enum Kind {
        /** A cone. */
        CONE,
        /** A cheese. */
        CHEESE,
        /** A pirn, for a shuttle. */
        PIRN,
        /** A spool. */
        SPOOL,
        /** A beam. */
        BEAM;

        /** The kinds, in the order the docket lists them. */
        public static final List<Kind> PACKAGE_KINDS = List.of(values());

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** How many packages a length needs, what is left on the last, and what they weigh. */
    public record Needed(int packages, double leftOverMetres, double totalKg) {}

    /** What a delivery comes to, in packages, weight and length. */
    public record Totals(int packages, double grossKg, double netKg, double metres) {}

    /** Describe a package from its plain record: gross and tare weight, count and system. */
    public static YarnPackage create(Object input) {
        JsonObject record = Check.plainObject(input, "package");
        Json kindGiven = record.get("kind");
        Kind kind = kindGiven.isNullish() ? Kind.CONE : Check.member(kindGiven, "kind", Kind.values());
        double grossKg = Check.positiveNumber(record.get("grossKg"), "grossKg");
        Json tareGiven = record.get("tareKg");
        double tareKg = tareGiven.isNullish() ? 0 : Check.number(tareGiven, "tareKg", Bounds.atLeast(0));
        if (tareKg >= grossKg) {
            throw new InvalidArgumentException(
                    "the packing weighs as much as the package", Json.obj("grossKg", grossKg, "tareKg", tareKg));
        }
        double count = Check.positiveNumber(record.get("count"), "count");
        Json systemGiven = record.get("system");
        String system = Check.nonEmptyString(systemGiven.isNullish() ? Json.of("tex") : systemGiven, "system");
        double netKg = Numeric.roundTo(grossKg - tareKg, 4);
        double metres = Numeric.roundTo((netKg * 1000000) / Count.toTex(count, system), 1);
        return new YarnPackage(kind, grossKg, tareKg, netKg, count, system, metres);
    }

    /** How long the package lasts on a loom, in minutes. */
    public double runsForMinutes(double metresPerMinute) {
        Check.positiveNumber(metresPerMinute, "metresPerMinute");
        return Numeric.roundTo(metres / metresPerMinute, 2);
    }

    /** How many packages like this one a length of yarn needs, and what is left on the last one. */
    public Needed packagesFor(double metresNeeded) {
        Check.positiveNumber(metresNeeded, "metresNeeded");
        int whole = (int) Math.ceil(metresNeeded / metres);
        double used = metresNeeded - (whole - 1) * metres;
        return new Needed(whole, Numeric.roundTo(metres - used, 1), Numeric.roundTo(whole * netKg, 4));
    }

    /** The usable length when two per cent is left on the tube. */
    public double usableMetres() {
        return usableMetres(2);
    }

    /**
     * The tail a shed will not use. Yarn left below the doffing height is thrown away with the tube,
     * so the usable length is short of the whole one.
     */
    public double usableMetres(double wastePercent) {
        Check.number(wastePercent, "wastePercent", Bounds.between(0, 100));
        return Numeric.roundTo(metres * (1 - wastePercent / 100), 1);
    }

    /** Refuse a package heavier than a creel peg takes. */
    public YarnPackage assertFitsCreel(double maxKg) {
        return assertFitsCreel(maxKg, OptionalDouble.empty(), OptionalDouble.empty());
    }

    /** Refuse a package a creel position cannot physically take, by weight or, when measured, by width. */
    public YarnPackage assertFitsCreel(double maxKg, OptionalDouble maxDiameterMm, OptionalDouble diameterMm) {
        Check.positiveNumber(maxKg, "maxKg");
        if (grossKg > maxKg) {
            throw new CapacityException(
                    "the package is heavier than the creel peg takes", Json.obj("grossKg", grossKg, "maxKg", maxKg));
        }
        if (maxDiameterMm.isPresent()
                && diameterMm.isPresent()
                && diameterMm.getAsDouble() > maxDiameterMm.getAsDouble()) {
            throw new CapacityException(
                    "the package is wider than the creel spacing",
                    Json.obj("diameterMm", diameterMm, "maxDiameterMm", maxDiameterMm));
        }
        return this;
    }

    /** What a delivery of packages comes to, in weight and in length. */
    public static Totals deliveryTotals(List<YarnPackage> packages) {
        if (packages == null || packages.isEmpty()) {
            throw new InvalidArgumentException("a delivery has at least one package", JsonObject.EMPTY);
        }
        return new Totals(
                packages.size(),
                Numeric.roundTo(Numeric.sumBy(packages, YarnPackage::grossKg, "packages"), 4),
                Numeric.roundTo(Numeric.sumBy(packages, YarnPackage::netKg, "packages"), 4),
                Numeric.roundTo(Numeric.sumBy(packages, YarnPackage::metres, "packages"), 1));
    }

    /** How much a delivery costs, priced on net weight. */
    public static double deliveryCost(List<YarnPackage> packages, double pricePerKg) {
        Check.positiveNumber(pricePerKg, "pricePerKg");
        return Numeric.roundTo(deliveryTotals(packages).netKg() * pricePerKg, 2);
    }
}
