package io.github.rkaushikdev.heddlewright.analysis;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;

/**
 * A cloth construction: the sett in each direction, the two yarn counts, the system they are
 * counted in, and the crimp each system takes. It is what the weight, the cost and the report are
 * worked out from.
 *
 * <p>A figure a construction record leaves out reads as not a number, so the calculation that needs
 * it refuses it by name rather than the record being refused for a figure nobody asked about.
 */
public record Construction(
        double endsPerCm,
        double picksPerCm,
        double warpCount,
        double weftCount,
        String system,
        double warpCrimp,
        double weftCrimp) {

    /** A construction in tex with no crimp. */
    public static Construction of(double endsPerCm, double picksPerCm, double warpCount, double weftCount) {
        return new Construction(endsPerCm, picksPerCm, warpCount, weftCount, "tex", 0, 0);
    }

    /** Read a construction from its plain record. */
    public static Construction fromRecord(Object input) {
        if (input instanceof Construction construction) {
            return construction;
        }
        JsonObject record = Check.plainObject(input, "construction");
        Json system = record.get("system");
        return new Construction(
                figure(record, "endsPerCm", Double.NaN),
                figure(record, "picksPerCm", Double.NaN),
                figure(record, "warpCount", Double.NaN),
                figure(record, "weftCount", Double.NaN),
                system.isNullish() ? "tex" : Check.nonEmptyString(system, "system"),
                figure(record, "warpCrimp", 0),
                figure(record, "weftCrimp", 0));
    }

    private static double figure(JsonObject record, String field, double fallback) {
        Json value = record.get(field);
        return value.isNullish() ? fallback : Check.number(value, field);
    }

    /** The same construction at another end sett. */
    public Construction endsPerCm(double value) {
        return new Construction(value, picksPerCm, warpCount, weftCount, system, warpCrimp, weftCrimp);
    }

    /** The same construction at another pick sett. */
    public Construction picksPerCm(double value) {
        return new Construction(endsPerCm, value, warpCount, weftCount, system, warpCrimp, weftCrimp);
    }

    /** The same construction in another warp count. */
    public Construction warpCount(double value) {
        return new Construction(endsPerCm, picksPerCm, value, weftCount, system, warpCrimp, weftCrimp);
    }

    /** The same construction with the counts read in another system. */
    public Construction system(String value) {
        return new Construction(endsPerCm, picksPerCm, warpCount, weftCount, value, warpCrimp, weftCrimp);
    }

    /** The same construction with another warp crimp. */
    public Construction warpCrimp(double value) {
        return new Construction(endsPerCm, picksPerCm, warpCount, weftCount, system, value, weftCrimp);
    }

    /** The same construction with another weft crimp. */
    public Construction weftCrimp(double value) {
        return new Construction(endsPerCm, picksPerCm, warpCount, weftCount, system, warpCrimp, value);
    }
}
