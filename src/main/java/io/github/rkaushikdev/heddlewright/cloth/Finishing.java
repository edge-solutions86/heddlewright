package io.github.rkaushikdev.heddlewright.cloth;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Collation;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Finishing.
 *
 * <p>Cloth leaves the loom longer, wider and lighter than it will be sold. Every finishing route
 * shrinks it by its own amount in each direction and changes its weight, and the changes compound: a
 * route of four processes is not the sum of four shrinkages, it is the product of four ratios. Adding
 * percentages here is the mistake that puts a piece under its contract width.
 */
public final class Finishing {

    /** A finishing process and what it does, in per cent. */
    public record Process(String name, double lengthShrink, double widthShrink, double weightChange) {}

    /** A route's compound effect. */
    public record Effect(
            double lengthRatio,
            double widthRatio,
            double weightRatio,
            double lengthShrinkPercent,
            double widthShrinkPercent) {}

    /** A piece of cloth as finishing sees it: length, width, and the weight when it is known. */
    public record Cloth(double metres, double widthCm, OptionalDouble gsm) {
        /** A piece with no weight given. */
        public static Cloth of(double metres, double widthCm) {
            return new Cloth(metres, widthCm, OptionalDouble.empty());
        }

        /** A piece with its weight per square metre. */
        public static Cloth of(double metres, double widthCm, double gsm) {
            return new Cloth(metres, widthCm, OptionalDouble.of(gsm));
        }
    }

    /** A finished width against a contract. */
    public record WidthReading(double finishedWidthCm, double contractWidthCm, double shortfallCm, boolean meets) {}

    /** The shrinkages added up, the way a hand-written docket does it. */
    public record NaiveShrink(double lengthShrinkPercent, double widthShrinkPercent) {}

    /** The contract width tolerance when none is given, in per cent. */
    public static final double TOLERANCE_PERCENT = 2;

    private static final Map<String, Process> PROCESSES = register(
            new Process("scour", 1.5, 2, -4),
            new Process("bleach", 1, 1.5, -2),
            new Process("mercerise", 2, 3, 1),
            new Process("dye", 1, 1, 1.5),
            new Process("sanforise", 3, 0.5, 0),
            new Process("calender", 0, -1, 0),
            new Process("raise", 2, 2, -3));

    private static Map<String, Process> register(Process... processes) {
        Map<String, Process> out = new LinkedHashMap<>();
        for (Process process : processes) {
            out.put(process.name(), process);
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    private Finishing() {}

    /** Every finishing process the works knows, in name order. */
    public static List<Process> listProcesses() {
        return PROCESSES.values().stream()
                .sorted(Comparator.comparing(Process::name, Collation.ORDER))
                .toList();
    }

    /** Look one up. */
    public static Process processByName(Object name) {
        String wanted = Check.nonEmptyString(name, "name");
        Process found = PROCESSES.get(wanted);
        if (found == null) {
            throw new NotFoundException(
                    "no finishing process called \"" + wanted + "\"",
                    Json.obj("name", wanted, "known", List.copyOf(PROCESSES.keySet())));
        }
        return found;
    }

    /**
     * The compound effect of a route. Each stage takes its share of what is left, so the ratios
     * multiply rather than the percentages adding.
     */
    public static Effect routeEffect(List<?> route) {
        List<Object> names = Check.array(route, "route", 1);
        double length = 1;
        double width = 1;
        double weight = 1;
        for (Object name : names) {
            Process process = processByName(name);
            length *= 1 - process.lengthShrink() / 100;
            width *= 1 - process.widthShrink() / 100;
            weight *= 1 + process.weightChange() / 100;
        }
        return new Effect(
                Numeric.roundTo(length, 6),
                Numeric.roundTo(width, 6),
                Numeric.roundTo(weight, 6),
                Numeric.roundTo((1 - length) * 100, 3),
                Numeric.roundTo((1 - width) * 100, 3));
    }

    /** What a piece measures after a route, the weight given only when the greige weight was. */
    public static Cloth finishedPiece(Cloth greige, List<?> route) {
        Check.positiveNumber(greige.metres(), "metres");
        Check.positiveNumber(greige.widthCm(), "widthCm");
        Effect effect = routeEffect(route);
        double metres = Numeric.roundTo(greige.metres() * effect.lengthRatio(), 3);
        double widthCm = Numeric.roundTo(greige.widthCm() * effect.widthRatio(), 2);
        if (greige.gsm().isEmpty()) {
            return Cloth.of(metres, widthCm);
        }
        double gsm = Check.positiveNumber(greige.gsm().getAsDouble(), "gsm");
        // Weight per square metre rises as the cloth shrinks into itself, on top of whatever the
        // process added or took away.
        double area = effect.lengthRatio() * effect.widthRatio();
        return Cloth.of(metres, widthCm, Numeric.roundTo((gsm * effect.weightRatio()) / area, 2));
    }

    /**
     * The greige a finished piece has to start from, which is the calculation a planner actually
     * needs: work backwards from the contract width and length.
     */
    public static Cloth greigeNeeded(Cloth finished, List<?> route) {
        Effect effect = routeEffect(route);
        return Cloth.of(
                Numeric.roundTo(finished.metres() / effect.lengthRatio(), 3),
                Numeric.roundTo(finished.widthCm() / effect.widthRatio(), 2));
    }

    /** Whether a finished piece will meet a contract width within two per cent. */
    public static WidthReading meetsWidth(Cloth greige, List<?> route, double contractWidthCm) {
        return meetsWidth(greige, route, contractWidthCm, TOLERANCE_PERCENT);
    }

    /** Whether a finished piece will meet a contract width. */
    public static WidthReading meetsWidth(
            Cloth greige, List<?> route, double contractWidthCm, double tolerancePercent) {
        Check.positiveNumber(contractWidthCm, "contractWidthCm");
        Check.number(tolerancePercent, "tolerancePercent", Bounds.atLeast(0));
        Cloth finished = finishedPiece(greige, route);
        double shortfall = contractWidthCm - finished.widthCm();
        double allowed = (contractWidthCm * tolerancePercent) / 100;
        return new WidthReading(
                finished.widthCm(), contractWidthCm, Numeric.roundTo(shortfall, 2), shortfall <= allowed);
    }

    /** Refuse a route naming a process nobody has. */
    public static <T extends List<?>> T assertRouteKnown(T route) {
        List<Object> names = Check.array(route, "route", 1);
        for (Object name : names) {
            processByName(name);
        }
        return route;
    }

    /**
     * Adding the shrinkages instead of compounding them, which is what a docket written by hand
     * usually does. It is here so the difference can be shown rather than argued about.
     */
    public static NaiveShrink naiveRouteShrink(List<?> route) {
        assertRouteKnown(route);
        return new NaiveShrink(
                Numeric.roundTo(Numeric.sumBy(route, name -> processByName(name).lengthShrink(), "route"), 3),
                Numeric.roundTo(Numeric.sumBy(route, name -> processByName(name).widthShrink(), "route"), 3));
    }

    /** Refuse a route whose finished width will miss the contract by more than two per cent. */
    public static WidthReading assertMeetsWidth(Cloth greige, List<?> route, double contractWidthCm) {
        return assertMeetsWidth(greige, route, contractWidthCm, TOLERANCE_PERCENT);
    }

    /** Refuse a route whose finished width will miss the contract. */
    public static WidthReading assertMeetsWidth(
            Cloth greige, List<?> route, double contractWidthCm, double tolerancePercent) {
        WidthReading reading = meetsWidth(greige, route, contractWidthCm, tolerancePercent);
        if (!reading.meets()) {
            throw new InvalidArgumentException(
                    "that route finishes narrower than the contract allows", (JsonObject) Json.of(reading));
        }
        return reading;
    }
}
