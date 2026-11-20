package io.github.rkaushikdev.heddlewright.loom;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Collation;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shed timing.
 *
 * <p>Everything on a loom is timed against the crank, which turns once per pick. Beat-up is at
 * nought degrees, the shed crosses somewhere on the way round, and it has to be open wide enough,
 * for long enough, for the shuttle to fly through without touching a warp end. Getting that window
 * wrong does not stop the loom: it just cuts ends, one at a time, all day.
 */
public final class ShedTiming {

    /** The crank angle of beat-up. */
    public static final int BEAT_UP = 0;

    /** The shed cycle: where it crosses, opens fully, starts to close, and how long it dwells. */
    public record Cycle(int crossAngle, int fullyOpen, int startClosing, double dwellDegrees, double openDegrees) {}

    /** The window a shuttle occupies, from launch to arrival in the far box. */
    public record ShuttleWindow(int launch, int arrival, double flightDegrees) {}

    /** Whether the shuttle flies inside the open shed, and how much it clears by at each end. */
    public record Clearance(boolean clears, int entryMargin, double exitMargin, int dwellDegrees) {}

    /** How far ahead of beat-up the shed crosses, and what that makes the timing. */
    public record Lead(int leadDegrees, String timing) {}

    /** One event in the pick, at a crank angle. */
    public record Event(int at, String event) {}

    private ShedTiming() {}

    /** Put an angle in the 0 to 360 range, to the nearest degree. */
    public static int normaliseAngle(double angle) {
        Check.number(angle, "angle", Bounds.NONE);
        return Numeric.mod((int) Math.round(angle), 360);
    }

    /** True when {@code angle} falls in the arc from {@code from} to {@code to}, going forwards. */
    public static boolean angleWithin(double angle, double from, double to) {
        int a = normaliseAngle(angle);
        int start = normaliseAngle(from);
        int end = normaliseAngle(to);
        if (start <= end) {
            return a >= start && a <= end;
        }
        return a >= start || a <= end;
    }

    /** The usual shed: crossing at 300 degrees, opening over 60 and dwelling for 140. */
    public static Cycle shedCycle() {
        return shedCycle(300, 140, 60);
    }

    /**
     * The shed cycle. The shed crosses at {@code crossAngle}, opens to full over {@code openDegrees},
     * holds open for the dwell, and closes again to cross on the next pick.
     */
    public static Cycle shedCycle(double crossAngle, double dwellDegrees, double openDegrees) {
        int cross = normaliseAngle(crossAngle);
        Check.positiveNumber(dwellDegrees, "dwellDegrees");
        Check.positiveNumber(openDegrees, "openDegrees");
        if (dwellDegrees + openDegrees * 2 > 360) {
            throw new DraftException(
                    "the shed cannot open, dwell and close inside one turn",
                    Json.obj("dwellDegrees", dwellDegrees, "openDegrees", openDegrees));
        }
        int fullyOpen = normaliseAngle(cross + openDegrees);
        int startClosing = normaliseAngle(fullyOpen + dwellDegrees);
        return new Cycle(cross, fullyOpen, startClosing, dwellDegrees, openDegrees);
    }

    /** True when the shed is at its full opening at that crank angle. */
    public static boolean isShedOpenAt(Cycle cycle, double angle) {
        return angleWithin(angle, cycle.fullyOpen(), cycle.startClosing());
    }

    /** The usual shuttle: launched at ten degrees and boxed a hundred and twenty later. */
    public static ShuttleWindow shuttleWindow() {
        return shuttleWindow(10, 120);
    }

    /** The window a shuttle occupies, from launch to arrival in the far box. */
    public static ShuttleWindow shuttleWindow(double pickingAngle, double flightDegrees) {
        int launch = normaliseAngle(pickingAngle);
        Check.positiveNumber(flightDegrees, "flightDegrees");
        if (flightDegrees >= 360) {
            throw new DraftException(
                    "the shuttle cannot take a whole turn to cross", Json.obj("flightDegrees", flightDegrees));
        }
        return new ShuttleWindow(launch, normaliseAngle(launch + flightDegrees), flightDegrees);
    }

    /**
     * Whether the shuttle flies entirely inside the open shed, and by how much it clears at each end.
     * A negative margin is warp being cut.
     */
    public static Clearance shuttleClearance(Cycle cycle, ShuttleWindow window) {
        int dwell = Numeric.mod(cycle.startClosing() - cycle.fullyOpen(), 360);
        // Both margins are measured forwards from the moment the shed is fully open, so the exit
        // margin goes negative exactly when the shuttle is still in the shed as it starts to close.
        // That sign is the whole reading.
        int entryMargin = Numeric.mod(window.launch() - cycle.fullyOpen(), 360);
        double exitMargin = Numeric.roundTo(dwell - entryMargin - window.flightDegrees(), 2);
        return new Clearance(entryMargin <= dwell && exitMargin >= 0, entryMargin, exitMargin, dwell);
    }

    /** Refuse a timing that would fly the shuttle into a closing shed. */
    public static Clearance assertShuttleClears(Cycle cycle, ShuttleWindow window) {
        Clearance clearance = shuttleClearance(cycle, window);
        if (!clearance.clears()) {
            throw new DraftException(
                    "the shuttle does not clear the shed",
                    Json.obj(
                            "flightDegrees", window.flightDegrees(),
                            "dwellDegrees", clearance.dwellDegrees(),
                            "launch", window.launch(),
                            "arrival", window.arrival()));
        }
        return clearance;
    }

    /**
     * How early the shed crosses relative to beat-up. Early shedding closes the warp before the reed
     * arrives and packs the pick harder; late shedding leaves it open and gives a softer cloth.
     */
    public static Lead sheddingLead(Cycle cycle) {
        int lead = Numeric.mod(BEAT_UP - cycle.crossAngle(), 360);
        String timing = "level";
        if (lead > 0 && lead <= 180) {
            timing = "early";
        } else if (lead > 180) {
            timing = "late";
        }
        return new Lead(lead, timing);
    }

    /** The whole pick as a list of events in crank order, for a timing chart. */
    public static List<Event> timingChart(Cycle cycle, ShuttleWindow window) {
        List<Event> events = new ArrayList<>(List.of(
                new Event(BEAT_UP, "beat-up"),
                new Event(cycle.crossAngle(), "shed crosses"),
                new Event(cycle.fullyOpen(), "shed fully open"),
                new Event(window.launch(), "shuttle launched"),
                new Event(window.arrival(), "shuttle boxed"),
                new Event(cycle.startClosing(), "shed starts closing")));
        events.sort(Comparator.comparingInt(Event::at).thenComparing(Event::event, Collation.ORDER));
        return List.copyOf(events);
    }
}
