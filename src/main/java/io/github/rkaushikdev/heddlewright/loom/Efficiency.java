package io.github.rkaushikdev.heddlewright.loom;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Group;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * Loom efficiency and where it goes.
 *
 * <p>A loom that never stopped would weave its quoted speed all day. Every stop costs the time it
 * takes plus the time before somebody reaches the loom, and the two are not the same: a shed can
 * lose more time waiting than mending. Splitting the loss by cause is what tells a manager whether to
 * buy better yarn or hire another weaver.
 */
public final class Efficiency {

    /** The causes a shed books its stops under. */
    public static final List<String> STOP_CAUSES = List.of(
            "warp break", "weft break", "shuttle change", "beam change", "mechanical", "no weaver", "quality stop");

    /** One line of the stop book: a cause, how many stops, and the minutes each took and waited. */
    public record Stop(String cause, double count, double minutesEach, double waitEach, double minutesLost) {}

    /** The minutes a run of stops cost, split into mending and waiting. */
    public record Lost(double mending, double waiting, double total) {}

    /** A shift handover: the time lost, the efficiency, the picks woven if the speed is known, by cause. */
    public record ShiftReport(
            double shiftMinutes,
            Lost lostMinutes,
            double efficiency,
            double efficiencyPercent,
            OptionalLong picksWoven,
            List<JsonObject> byCause) {
        /** Copies the causes. */
        public ShiftReport {
            byCause = List.copyOf(byCause);
        }
    }

    private Efficiency() {}

    /** Check the first stop record of a book and hand back a normalised copy. */
    public static Stop normaliseStop(Object stop) {
        return normaliseStop(stop, 0);
    }

    /** Check a stop record and hand back a normalised copy. */
    public static Stop normaliseStop(Object stop, int index) {
        String at = "stops[" + index + "]";
        JsonObject record = Check.plainObject(stop, at);
        String cause = Check.nonEmptyString(record.get("cause"), at + ".cause");
        double count = Check.number(orDefault(record.get("count"), 1), at + ".count", Bounds.atLeast(0));
        double minutesEach =
                Check.number(orDefault(record.get("minutesEach"), 0), at + ".minutesEach", Bounds.atLeast(0));
        double waitEach = Check.number(orDefault(record.get("waitEach"), 0), at + ".waitEach", Bounds.atLeast(0));
        return new Stop(cause, count, minutesEach, waitEach, count * (minutesEach + waitEach));
    }

    private static Json orDefault(Json value, double fallback) {
        return value.isNullish() ? Json.of(fallback) : value;
    }

    private static List<Stop> records(List<?> stops) {
        List<Object> list = Check.array(stops, "stops");
        List<Stop> out = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index += 1) {
            out.add(normaliseStop(list.get(index), index));
        }
        return out;
    }

    /** The minutes a run of stops costs, split into mending and waiting. */
    public static Lost lostMinutes(List<?> stops) {
        List<Stop> records = records(stops);
        double mending = 0;
        double waiting = 0;
        double total = 0;
        for (Stop record : records) {
            mending += record.count() * record.minutesEach();
            waiting += record.count() * record.waitEach();
            total += record.minutesLost();
        }
        return new Lost(Numeric.roundTo(mending, 3), Numeric.roundTo(waiting, 3), Numeric.roundTo(total, 3));
    }

    /** Efficiency over a shift, as a fraction between nothing and one. */
    public static double efficiency(double shiftMinutes, List<?> stops) {
        Check.positiveNumber(shiftMinutes, "shiftMinutes");
        double lost = lostMinutes(stops).total();
        if (lost > shiftMinutes) {
            throw new InvalidArgumentException(
                    "the stops add up to more than the shift",
                    Json.obj("shiftMinutes", shiftMinutes, "lostMinutes", lost));
        }
        return Numeric.roundTo((shiftMinutes - lost) / shiftMinutes, 4);
    }

    /**
     * The losses broken down by cause, worst first. The count is the number of stops rather than the
     * number of records, because a shed books twenty weft breaks on one line and that line is not
     * twenty stops until it is read as one.
     */
    public static List<JsonObject> lossByCause(List<?> stops) {
        return Group.tally(
                records(stops),
                Group.<Stop>by(Stop::cause)
                        .label("cause")
                        .field("count", Stop::count)
                        .field("minutesLost", Stop::minutesLost)
                        .sortBy("minutesLost"));
    }

    /** Breaks per hundred thousand picks. */
    public static double breakRate(double breaks, double picks) {
        return breakRate(breaks, picks, 100000);
    }

    /**
     * Breaks per so many picks, the figure a spinner is judged on. A warp break rate is quoted per
     * hundred thousand picks so that looms of different speeds and widths can be compared.
     */
    public static double breakRate(double breaks, double picks, double per) {
        Check.number(breaks, "breaks", Bounds.atLeast(0));
        Check.positiveNumber(picks, "picks");
        Check.positiveNumber(per, "per");
        return Numeric.roundTo((breaks / picks) * per, 3);
    }

    /**
     * The efficiency a stop rate implies, before anything is measured. Each stop takes its own time
     * plus the wait, and the rest of the hour is weaving.
     */
    public static double efficiencyFromStopRate(double stopsPerHour, double minutesPerStop) {
        Check.number(stopsPerHour, "stopsPerHour", Bounds.atLeast(0));
        Check.positiveNumber(minutesPerStop, "minutesPerStop");
        double lost = stopsPerHour * minutesPerStop;
        if (lost >= 60) {
            return 0;
        }
        return Numeric.roundTo((60 - lost) / 60, 4);
    }

    /** What one more or one fewer stop an hour is worth, in efficiency points. */
    public static double stopSensitivity(double minutesPerStop) {
        Check.positiveNumber(minutesPerStop, "minutesPerStop");
        return Numeric.roundTo(minutesPerStop / 60, 4);
    }

    /** A full report, ready to print for a shift handover, the picks woven given only with a speed. */
    public static ShiftReport shiftReport(double shiftMinutes, List<?> stops, OptionalDouble picksMinute) {
        Lost lost = lostMinutes(stops);
        double running = efficiency(shiftMinutes, stops);
        return new ShiftReport(
                shiftMinutes,
                lost,
                running,
                Numeric.roundTo(running * 100, 2),
                picksMinute.isPresent()
                        ? OptionalLong.of(Math.round(shiftMinutes * running * picksMinute.getAsDouble()))
                        : OptionalLong.empty(),
                lossByCause(stops));
    }
}
