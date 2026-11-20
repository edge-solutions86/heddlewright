package io.github.rkaushikdev.heddlewright.works;

import io.github.rkaushikdev.heddlewright.loom.Loom;
import io.github.rkaushikdev.heddlewright.loom.Registry;
import io.github.rkaushikdev.heddlewright.loom.Requirements;
import io.github.rkaushikdev.heddlewright.loom.Speed;
import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Collation;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Putting work on looms.
 *
 * <p>A style can only go on a loom that will hold it, and a loom can only run one style at a time.
 * Beyond that the choice is a trade: the fastest loom finishes soonest but a wide loom running a
 * narrow cloth wastes reed all week, so the plan here fills the narrowest capable loom first and
 * only reaches for a wide one when nothing else will do.
 */
public final class Schedule {

    /** The share of the running time a loom is assumed to weave. */
    public static final double EFFICIENCY = 0.85;

    /**
     * What one run asks of the floor. The requirements are kept as the record gave them; a figure
     * they leave out rules no loom out.
     *
     * @param order the order it is for
     * @param style the cloth
     * @param metres the length to weave
     * @param picksPerCm the pick density
     * @param requirements what the loom must do, as given
     * @param priority higher goes first
     */
    public record Run(
            String order, String style, double metres, double picksPerCm, JsonObject requirements, double priority) {}

    /** A run put on a loom, and the hours it occupies. */
    public record Placement(String order, String style, String loom, double fromHour, double toHour, double hours) {}

    /** A run no loom could take, and why. */
    public record Unplaced(String order, String reason) {}

    /** A plan: what went where, what could not go anywhere, when it ends, and when each loom is free. */
    public record Plan(List<Placement> placed, List<Unplaced> unplaced, double finishesAt, JsonObject loomFinish) {
        /** Copies the lists. */
        public Plan {
            placed = List.copyOf(placed);
            unplaced = List.copyOf(unplaced);
        }
    }

    /** How busy one loom is over a plan. */
    public record Load(String loom, double hours, double utilisation) {}

    /** An order that will not be finished by the hour it is wanted. */
    public record Late(String order, double due, double finishes, double byHours) {}

    private Schedule() {}

    /** What one run asks of the floor, read from its record. */
    public static Run createRun(Object input) {
        JsonObject record = Check.plainObject(input, "run");
        Json priority = record.get("priority");
        return new Run(
                Check.nonEmptyString(record.get("order"), "order"),
                Check.nonEmptyString(record.get("style"), "style"),
                Check.positiveNumber(record.get("metres"), "metres"),
                Check.positiveNumber(record.get("picksPerCm"), "picksPerCm"),
                Check.plainObject(record.get("requirements"), "requirements"),
                Check.number(priority.isNullish() ? Json.of(0) : priority, "priority"));
    }

    /** The looms that could take a run, narrowest capable first and then fastest. */
    public static List<Loom> candidates(List<Loom> looms, Run run) {
        Requirements wanted = Requirements.fromRecord(run.requirements());
        return looms.stream()
                .filter(loom -> Registry.canWeave(loom, wanted))
                .sorted(Comparator.comparingDouble(Loom::reedWidthCm)
                        .thenComparing(
                                Comparator.comparingDouble(Loom::picksMinute).reversed()))
                .toList();
    }

    /** How long a run takes on a given loom at the usual efficiency, in hours. */
    public static double runHoursOn(Loom loom, Run run) {
        return runHoursOn(loom, run, EFFICIENCY);
    }

    /** How long a run takes on a given loom, in hours. */
    public static double runHoursOn(Loom loom, Run run, double efficiency) {
        return Speed.runHours(run.metres(), run.picksPerCm(), loom.picksMinute(), efficiency);
    }

    /** A plan at the usual efficiency, starting at hour nought. */
    public static Plan schedule(List<Loom> looms, List<Run> runs) {
        return schedule(looms, runs, EFFICIENCY, 0);
    }

    /** A plan from the {@code efficiency} and {@code startHour} a request's options give, if any. */
    public static Plan schedule(List<Loom> looms, List<Run> runs, Object options) {
        JsonObject record = Check.plainObject(options, "options");
        Json efficiency = record.get("efficiency");
        Json startHour = record.get("startHour");
        return schedule(
                looms,
                runs,
                efficiency.isMissing() ? EFFICIENCY : Check.number(efficiency, "efficiency"),
                startHour.isMissing() ? 0 : Check.number(startHour, "startHour"));
    }

    /**
     * Build a plan. Runs are taken in priority order, highest first and then by order, and each goes
     * on the capable loom that is free soonest, breaking a tie on the narrower loom so wide capacity
     * is kept back for work that needs it.
     */
    public static Plan schedule(List<Loom> looms, List<Run> runs, double efficiency, double startHour) {
        Check.array(looms, "looms", 1);
        Check.array(runs, "runs");
        Check.number(startHour, "startHour", Bounds.atLeast(0));
        Map<String, Double> free = new LinkedHashMap<>();
        looms.forEach(loom -> free.put(loom.reference(), startHour));
        List<Placement> placed = new ArrayList<>();
        List<Unplaced> unplaced = new ArrayList<>();
        List<Run> ordered = new ArrayList<>(runs);
        ordered.sort(Comparator.comparingDouble(Run::priority).reversed().thenComparing(Run::order, Collation.ORDER));
        for (Run run : ordered) {
            List<Loom> able = candidates(looms, run);
            if (able.isEmpty()) {
                unplaced.add(new Unplaced(run.order(), "no loom on the floor can weave it"));
                continue;
            }
            Loom chosen = null;
            for (Loom loom : able) {
                if (chosen == null) {
                    chosen = loom;
                    continue;
                }
                double freeAt = free.get(loom.reference());
                double bestFree = free.get(chosen.reference());
                if (freeAt < bestFree || (freeAt == bestFree && loom.reedWidthCm() < chosen.reedWidthCm())) {
                    chosen = loom;
                }
            }
            double hours = runHoursOn(chosen, run, efficiency);
            double from = free.get(chosen.reference());
            placed.add(new Placement(
                    run.order(),
                    run.style(),
                    chosen.reference(),
                    Numeric.roundTo(from, 3),
                    Numeric.roundTo(from + hours, 3),
                    hours));
            free.put(chosen.reference(), from + hours);
        }
        placed.sort(Comparator.comparingDouble(Placement::fromHour).thenComparing(Placement::loom, Collation.ORDER));
        double finishesAt = startHour;
        for (Placement entry : placed) {
            finishesAt = Math.max(finishesAt, entry.toHour());
        }
        Map<String, Object> loomFinish = new LinkedHashMap<>();
        free.forEach((reference, hour) -> loomFinish.put(reference, Numeric.roundTo(hour, 3)));
        return new Plan(placed, unplaced, Numeric.roundTo(finishesAt, 3), (JsonObject) Json.of(loomFinish));
    }

    /** How busy each loom is over the plan, as a share of the whole span, busiest first. */
    public static List<Load> loomLoad(Plan plan) {
        Check.object(plan, "plan");
        double span = plan.finishesAt();
        Map<String, Double> busy = new LinkedHashMap<>();
        for (Placement entry : plan.placed()) {
            busy.put(entry.loom(), Numeric.roundTo(busy.getOrDefault(entry.loom(), 0.0) + entry.hours(), 3));
        }
        List<Load> out = new ArrayList<>();
        busy.forEach((loom, hours) -> out.add(new Load(loom, hours, span == 0 ? 0 : Numeric.roundTo(hours / span, 4))));
        out.sort(Comparator.comparingDouble(Load::hours).reversed().thenComparing(Load::loom, Collation.ORDER));
        return List.copyOf(out);
    }

    /** Refuse a plan that leaves work with nowhere to go. */
    public static Plan assertFullyPlaced(Plan plan) {
        if (!plan.unplaced().isEmpty()) {
            throw new CapacityException("some runs have no loom to go on", Json.obj("unplaced", plan.unplaced()));
        }
        return plan;
    }

    /** When one order finishes, or nothing when it was never placed. */
    public static OptionalDouble finishOf(Plan plan, String order) {
        Check.nonEmptyString(order, "order");
        return plan.placed().stream()
                .filter(placed -> placed.order().equals(order))
                .mapToDouble(Placement::toHour)
                .findFirst();
    }

    /** Orders that will not be finished by the hour they are wanted, latest first. */
    public static List<Late> lateOrders(Plan plan, Object wantedByHour) {
        JsonObject due = Check.plainObject(wantedByHour, "wantedByHour");
        List<Late> late = new ArrayList<>();
        for (Placement entry : plan.placed()) {
            Json hour = due.get(entry.order());
            if (hour.isMissing()) {
                continue;
            }
            double by = Check.number(hour, "wantedByHour." + entry.order(), Bounds.atLeast(0));
            if (entry.toHour() > by) {
                late.add(new Late(entry.order(), by, entry.toHour(), Numeric.roundTo(entry.toHour() - by, 3)));
            }
        }
        late.sort(Comparator.comparingDouble(Late::byHours).reversed());
        return List.copyOf(late);
    }

    /** A run that no loom can take is a design problem, not a planning one. */
    public static List<String> unweavableRuns(List<Loom> looms, List<Run> runs) {
        Check.array(runs, "runs");
        return runs.stream()
                .filter(run -> candidates(looms, run).isEmpty())
                .map(Run::order)
                .toList();
    }

    /** Refuse a run whose requirements were never filled in. */
    public static Run assertRunComplete(Run run) {
        if (run.requirements().size() == 0) {
            throw new InvalidArgumentException(
                    "the run says nothing about what the loom must do", Json.obj("order", run.order()));
        }
        return run;
    }
}
