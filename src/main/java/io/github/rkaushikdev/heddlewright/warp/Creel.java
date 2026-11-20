package io.github.rkaushikdev.heddlewright.warp;

import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Collation;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The colour order and the creel it is built from.
 *
 * <p>A striped warp is written as a colour order: so many ends of this, so many of that, repeated
 * across the width. Turning that into a creel plan means counting how many ends of each colour the
 * whole warp needs, and then how many packages of each colour have to hang in the creel to supply
 * them.
 */
public final class Creel {

    /** One stripe of a colour order: a colour and how many ends of it. */
    public record Stripe(String colour, int ends) {}

    /** How many packages of one colour the creel has to hold. */
    public record Position(String colour, int ends, int packages) {}

    /** The creel plan: the positions by colour, and what they add up to. */
    public record Plan(List<Position> positions, int packages, int colours, int sections) {
        /** Copies the positions. */
        public Plan {
            positions = List.copyOf(positions);
        }
    }

    /** A stripe as a width across the cloth. */
    public record StripeWidth(String colour, int ends, double inches) {}

    /** Where one stripe sits across the warp, in 1-based end numbers. */
    public record Span(String colour, int from, int to, int ends) {}

    /** The yarn one colour needs. */
    public record ColourYarn(String colour, int ends, double metres, double kilograms) {}

    private Creel() {}

    /** Check a colour order and hand back a normalised copy. */
    public static List<Stripe> normaliseColourOrder(List<Stripe> order) {
        Check.array(order, "order", 1);
        List<Stripe> out = new ArrayList<>(order.size());
        for (int index = 0; index < order.size(); index += 1) {
            Stripe stripe = Check.object(order.get(index), "order[" + index + "]");
            out.add(new Stripe(
                    Check.nonEmptyString(stripe.colour(), "order[" + index + "].colour"),
                    Check.positiveInteger(stripe.ends(), "order[" + index + "].ends")));
        }
        return List.copyOf(out);
    }

    /** How many ends one repeat of the order covers. */
    public static int orderRepeat(List<Stripe> order) {
        return normaliseColourOrder(order).stream().mapToInt(Stripe::ends).sum();
    }

    /** The colour of every end across the warp, repeating the order as needed. */
    public static List<String> expandColourOrder(List<Stripe> order, int totalEnds) {
        List<Stripe> stripes = normaliseColourOrder(order);
        Check.positiveInteger(totalEnds, "totalEnds");
        List<String> out = new ArrayList<>(totalEnds);
        int at = 0;
        while (out.size() < totalEnds) {
            Stripe stripe = stripes.get(at % stripes.size());
            for (int n = 0; n < stripe.ends() && out.size() < totalEnds; n += 1) {
                out.add(stripe.colour());
            }
            at += 1;
        }
        return List.copyOf(out);
    }

    /** How many ends of each colour a warp of this width needs, most first. */
    public static List<Stripe> endsByColour(List<Stripe> order, int totalEnds) {
        Map<String, Integer> colours = new LinkedHashMap<>();
        for (String colour : expandColourOrder(order, totalEnds)) {
            colours.merge(colour, 1, Integer::sum);
        }
        List<Stripe> out = new ArrayList<>();
        colours.forEach((colour, ends) -> out.add(new Stripe(colour, ends)));
        out.sort(Comparator.comparingInt(Stripe::ends).reversed().thenComparing(Stripe::colour, Collation.ORDER));
        return List.copyOf(out);
    }

    /** The creel plan for a warp warped in one pass. */
    public static Plan creelPlan(List<Stripe> order, int totalEnds) {
        return creelPlan(order, totalEnds, 1);
    }

    /**
     * The creel plan: one package position per end of each colour in a single warping pass, which is
     * what the creel actually has to hold at once.
     */
    public static Plan creelPlan(List<Stripe> order, int totalEnds, int sections) {
        Check.positiveInteger(sections, "sections");
        List<Position> positions = endsByColour(order, totalEnds).stream()
                .map(entry ->
                        new Position(entry.colour(), entry.ends(), (int) Math.ceil((double) entry.ends() / sections)))
                .toList();
        int packages = positions.stream().mapToInt(Position::packages).sum();
        return new Plan(positions, packages, positions.size(), sections);
    }

    /** Refuse a plan the creel cannot hold. */
    public static Plan assertCreelFits(Plan plan, int capacity) {
        Check.positiveInteger(capacity, "capacity");
        if (plan.packages() > capacity) {
            throw new CapacityException(
                    "the creel cannot hold that many packages at once",
                    Json.obj("packages", plan.packages(), "capacity", capacity, "over", plan.packages() - capacity));
        }
        return plan;
    }

    /** The stripes as widths across the cloth, at a given sett. */
    public static List<StripeWidth> stripeWidths(List<Stripe> order, double endsPerInch) {
        Check.positiveNumber(endsPerInch, "endsPerInch");
        return normaliseColourOrder(order).stream()
                .map(stripe -> new StripeWidth(
                        stripe.colour(), stripe.ends(), Numeric.roundTo(stripe.ends() / endsPerInch, 3)))
                .toList();
    }

    /**
     * Mirror a colour order about its last stripe, which is how a symmetric warp is written from half
     * of it. The turning stripe is not repeated.
     */
    public static List<Stripe> mirrorOrder(List<Stripe> order) {
        List<Stripe> stripes = normaliseColourOrder(order);
        if (stripes.size() < 2) {
            throw new InvalidArgumentException(
                    "a mirrored order needs at least two stripes", Json.obj("stripes", stripes.size()));
        }
        List<Stripe> back = new ArrayList<>(stripes.subList(0, stripes.size() - 1));
        Collections.reverse(back);
        List<Stripe> out = new ArrayList<>(stripes);
        out.addAll(back);
        return List.copyOf(out);
    }

    /** Where each stripe starts and ends across the warp, in 1-based end numbers. */
    public static List<Span> stripeSpans(List<Stripe> order, int totalEnds) {
        List<String> colours = expandColourOrder(order, totalEnds);
        List<Span> spans = new ArrayList<>();
        int start = 1;
        for (int end = 1; end <= colours.size(); end += 1) {
            if (end == colours.size() || !colours.get(end).equals(colours.get(end - 1))) {
                spans.add(new Span(colours.get(end - 1), start, end, end - start + 1));
                start = end + 1;
            }
        }
        return List.copyOf(spans);
    }

    /** How much yarn each colour needs, in metres and kilograms, at a count in tex for each colour. */
    public static List<ColourYarn> yarnByColour(
            List<Stripe> order, int totalEnds, double warpLengthMetres, Map<String, ? extends Number> texByColour) {
        Check.positiveNumber(warpLengthMetres, "warpLengthMetres");
        List<ColourYarn> out = new ArrayList<>();
        for (Stripe entry : endsByColour(order, totalEnds)) {
            double tex = Check.positiveNumber(texByColour.get(entry.colour()), "texByColour." + entry.colour());
            double metres = entry.ends() * warpLengthMetres;
            out.add(new ColourYarn(
                    entry.colour(),
                    entry.ends(),
                    Numeric.roundTo(metres, 3),
                    Numeric.roundTo((metres * tex) / 1000000, 4)));
        }
        return List.copyOf(out);
    }
}
