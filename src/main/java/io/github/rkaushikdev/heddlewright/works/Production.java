package io.github.rkaushikdev.heddlewright.works;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.ConflictException;
import io.github.rkaushikdev.heddlewright.util.Group;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.works.Schedule.Placement;
import io.github.rkaushikdev.heddlewright.works.Schedule.Plan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * What the floor actually made.
 *
 * <p>A plan says what should happen; production says what did. The two are kept apart on purpose,
 * because the useful number is the difference between them, and a system that quietly overwrites
 * the plan with the outturn can no longer tell anybody why a delivery slipped.
 */
public final class Production {

    /**
     * A shift's output booked against a loom and an order. The time it was booked at is kept as the
     * record gave it, or null.
     */
    public record Outturn(
            String order, String loom, String shift, double metres, double hours, double stops, Json at) {}

    /** What one order has produced so far. */
    public record Made(String order, int entries, double metres, double hours, double stops) {}

    /** How an order stands against what was ordered. */
    public record Progress(
            String order,
            int entries,
            double metres,
            double hours,
            double stops,
            double orderedMetres,
            double remainingMetres,
            boolean complete,
            double share,
            double overrunMetres) {}

    /** One planned run beside what it actually made. */
    public record AgainstPlan(
            String order,
            String loom,
            double plannedHours,
            double actualHours,
            double metres,
            double orderedMetres,
            double share,
            double hoursOver) {}

    private Production() {}

    /** Book the first entry of a shift's output. */
    public static Outturn recordOutturn(Object entry) {
        return recordOutturn(entry, 0);
    }

    /** Book a shift's output against a loom and an order, the shift defaulting to day. */
    public static Outturn recordOutturn(Object entry, int index) {
        String at = "outturn[" + index + "]";
        JsonObject record = Check.plainObject(entry, at);
        Json shift = record.get("shift");
        Json stops = record.get("stops");
        Json when = record.get("at");
        return new Outturn(
                Check.nonEmptyString(record.get("order"), at + ".order"),
                Check.nonEmptyString(record.get("loom"), at + ".loom"),
                Check.nonEmptyString(shift.isNullish() ? Json.of("day") : shift, at + ".shift"),
                Check.positiveNumber(record.get("metres"), at + ".metres"),
                Check.positiveNumber(record.get("hours"), at + ".hours"),
                Check.number(stops.isNullish() ? Json.of(0) : stops, at + ".stops", Bounds.atLeast(0)),
                when.isNullish() ? Json.NULL : when);
    }

    /** Book a whole book of outturn at once. */
    public static List<Outturn> recordBook(List<?> entries) {
        List<Object> list = Check.array(entries, "entries");
        List<Outturn> out = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index += 1) {
            out.add(recordOutturn(list.get(index), index));
        }
        return List.copyOf(out);
    }

    /** What one order has produced so far. */
    public static Made producedFor(List<Outturn> book, String order) {
        Check.nonEmptyString(order, "order");
        List<Outturn> mine =
                book.stream().filter(entry -> entry.order().equals(order)).toList();
        return new Made(
                order,
                mine.size(),
                Numeric.roundTo(Numeric.sumBy(mine, Outturn::metres, "book"), 3),
                Numeric.roundTo(Numeric.sumBy(mine, Outturn::hours, "book"), 3),
                Numeric.sumBy(mine, Outturn::stops, "book"));
    }

    /** How an order stands against what was ordered. */
    public static Progress orderProgress(List<Outturn> book, String order, Object orderedMetres) {
        double ordered = Check.positiveNumber(orderedMetres, "orderedMetres");
        Made made = producedFor(book, order);
        double remaining = Math.max(0, Numeric.roundTo(ordered - made.metres(), 3));
        return new Progress(
                made.order(),
                made.entries(),
                made.metres(),
                made.hours(),
                made.stops(),
                ordered,
                remaining,
                made.metres() >= ordered,
                Numeric.roundTo(Math.min(1, made.metres() / ordered), 4),
                Numeric.roundTo(Math.max(0, made.metres() - ordered), 3));
    }

    /** Metres an hour by loom, with the stops an hour, best first. */
    public static List<JsonObject> loomRates(List<Outturn> book) {
        return rates(book, "loom", Outturn::loom, true);
    }

    /** The same reading by shift, which is where a night crew shows up. */
    public static List<JsonObject> shiftRates(List<Outturn> book) {
        return rates(book, "shift", Outturn::shift, false);
    }

    private static List<JsonObject> rates(
            List<Outturn> book, String label, Function<Outturn, String> key, boolean withStops) {
        Check.array(book, "book");
        Group.Tally<Outturn> spec = Group.<Outturn>by(key)
                .label(label)
                .field("metres", Outturn::metres)
                .field("hours", Outturn::hours);
        if (withStops) {
            spec = spec.field("stops", Outturn::stops);
        }
        List<JsonObject> rows = new ArrayList<>();
        for (JsonObject row : Group.tally(book, spec)) {
            double hours = row.get("hours").asDouble();
            JsonObject rated =
                    row.with("metresPerHour", Numeric.roundTo(row.get("metres").asDouble() / hours, 3));
            if (withStops) {
                rated = rated.with(
                        "stopsPerHour", Numeric.roundTo(row.get("stops").asDouble() / hours, 3));
            }
            rows.add(rated);
        }
        return Group.sortRows(rows, label, "metresPerHour");
    }

    /**
     * Production against plan. A plan entry that produced nothing is reported as such rather than
     * dropped, because an order nobody started is exactly the one a report has to name.
     */
    public static List<AgainstPlan> againstPlan(Plan plan, List<Outturn> book, Object orderedMetres) {
        Check.object(plan, "plan");
        JsonObject ordered = Check.plainObject(orderedMetres, "orderedMetres");
        List<AgainstPlan> out = new ArrayList<>();
        for (Placement entry : plan.placed()) {
            Json length = ordered.get(entry.order());
            if (length.isMissing()) {
                throw new InvalidArgumentException(
                        "no ordered length for " + entry.order(), Json.obj("order", entry.order()));
            }
            Progress progress = orderProgress(book, entry.order(), length);
            out.add(new AgainstPlan(
                    entry.order(),
                    entry.loom(),
                    entry.hours(),
                    progress.hours(),
                    progress.metres(),
                    progress.orderedMetres(),
                    progress.share(),
                    Numeric.roundTo(progress.hours() - entry.hours(), 3)));
        }
        return List.copyOf(out);
    }

    /** Refuse an outturn booked against a loom the plan never gave it. */
    public static List<Outturn> assertBookMatchesPlan(Plan plan, List<Outturn> book) {
        Map<String, String> planned = new HashMap<>();
        for (Placement entry : plan.placed()) {
            planned.put(entry.order(), entry.loom());
        }
        for (Outturn entry : book) {
            String loom = planned.get(entry.order());
            if (loom == null) {
                throw new ConflictException(entry.order() + " was never planned", Json.obj("order", entry.order()));
            }
            if (!loom.equals(entry.loom())) {
                throw new ConflictException(
                        entry.order() + " was planned for " + loom,
                        Json.obj("order", entry.order(), "planned", loom, "booked", entry.loom()));
            }
        }
        return book;
    }
}
