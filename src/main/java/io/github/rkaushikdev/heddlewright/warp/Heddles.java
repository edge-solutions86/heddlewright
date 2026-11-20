package io.github.rkaushikdev.heddlewright.warp;

import io.github.rkaushikdev.heddlewright.draft.Threading;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Limits;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Heddles and drawing in.
 *
 * <p>A heddle is a wire with an eye, and one end goes through one of them. The loom carries a fixed
 * number on each shaft, so a draft that wants more on one shaft than the loom has cannot be drawn in
 * however many spare heddles sit on the shaft next to it. That asymmetry is the whole difficulty,
 * and it is why the check is per shaft rather than against a total.
 */
public final class Heddles {

    /** A shaft that needs more heddles than it carries, and how many short it is. */
    public record Shortfall(int shaft, int needed, int available, int shortBy) implements Json.Writable {
        /** Written with the shortfall under {@code short}, as the reports name it. */
        @Override
        public Json toJson() {
            return Json.obj("shaft", shaft, "needed", needed, "available", available, "short", shortBy);
        }
    }

    /** The spare heddles on one shaft, which go negative on a shaft that is short. */
    public record Spare(int shaft, int spare) {}

    /** Heddles to move from one shaft to another. */
    public record Move(int from, int to, int heddles) {}

    /** How a warp change is made and how long it takes. */
    public record Changeover(String method, double hours) {}

    private Heddles() {}

    /** How many heddles each shaft needs for a threading. */
    public static List<Integer> heddlesNeeded(List<Integer> threading, int shafts) {
        return Threading.shaftLoad(threading, shafts);
    }

    /** Where a draft asks for more heddles than the shaft carries, given the count on each shaft. */
    public static List<Shortfall> heddleShortfalls(List<Integer> threading, int shafts, List<?> available) {
        List<Integer> needed = heddlesNeeded(threading, shafts);
        List<Object> counts = Check.array(available, "available", shafts, shafts);
        for (int index = 0; index < counts.size(); index += 1) {
            Check.positiveInteger(counts.get(index), "available[" + index + "]");
        }
        return Limits.shortfalls(needed, counts, "shaft").stream()
                .map(entry -> new Shortfall(
                        entry.get("shaft").asInt(),
                        entry.get("wanted").asInt(),
                        entry.get("available").asInt(),
                        entry.get("over").asInt()))
                .toList();
    }

    /** Refuse a draft the loom has not the heddles for. */
    public static List<Integer> assertHeddlesFit(List<Integer> threading, int shafts, List<?> available) {
        List<Shortfall> found = heddleShortfalls(threading, shafts, available);
        if (!found.isEmpty()) {
            List<JsonObject> rows = found.stream()
                    .map(entry -> ((JsonObject) entry.toJson()).with("over", entry.shortBy()))
                    .toList();
            throw new CapacityException(
                    "there are not enough heddles on every shaft",
                    Json.obj("shafts", found.size(), "worst", Limits.worstShortfall(rows)));
        }
        return threading;
    }

    /**
     * The spare heddles on each shaft. They are not interchangeable between shafts without moving them
     * by hand, which is an hour's work a shaft, so the spares are reported per shaft rather than added
     * up.
     */
    public static List<Spare> spareHeddles(List<Integer> threading, int shafts, List<? extends Number> available) {
        List<Integer> needed = heddlesNeeded(threading, shafts);
        List<Spare> out = new ArrayList<>(needed.size());
        for (int index = 0; index < needed.size(); index += 1) {
            out.add(new Spare(index + 1, available.get(index).intValue() - needed.get(index)));
        }
        return List.copyOf(out);
    }

    /**
     * How many heddles to move, and from where, to make a draft fit. Heddles come off the shafts with
     * the most spare, which is what a weaver would do.
     */
    public static List<Move> heddleMoves(List<Integer> threading, int shafts, List<? extends Number> available) {
        List<Shortfall> found = heddleShortfalls(threading, shafts, available);
        List<int[]> spare = new ArrayList<>();
        for (Spare entry : spareHeddles(threading, shafts, available)) {
            if (entry.spare() > 0) {
                spare.add(new int[] {entry.shaft(), entry.spare()});
            }
        }
        spare.sort(Comparator.comparingInt((int[] entry) -> entry[1]).reversed());
        List<Move> moves = new ArrayList<>();
        for (Shortfall need : found) {
            int outstanding = need.shortBy();
            for (int[] source : spare) {
                if (outstanding == 0) {
                    break;
                }
                int taken = Math.min(outstanding, source[1]);
                if (taken == 0) {
                    continue;
                }
                moves.add(new Move(source[0], need.shaft(), taken));
                source[1] -= taken;
                outstanding -= taken;
            }
            if (outstanding > 0) {
                throw new CapacityException(
                        "the loom has not enough heddles anywhere to make it fit",
                        Json.obj("shaft", need.shaft(), "short", outstanding));
            }
        }
        return List.copyOf(moves);
    }

    /** How long drawing a warp in takes at four hundred ends an hour. */
    public static double drawingInHours(int ends) {
        return drawingInHours(ends, 400);
    }

    /** How long drawing a warp in takes, at a rate an operator can hold. */
    public static double drawingInHours(int ends, double endsPerHour) {
        Check.positiveInteger(ends, "ends");
        Check.positiveNumber(endsPerHour, "endsPerHour");
        return Numeric.roundTo(ends / endsPerHour, 3);
    }

    /** How long tying a warp in takes at two thousand ends an hour. */
    public static double tyingInHours(int ends) {
        return tyingInHours(ends, 2000);
    }

    /**
     * Tying in instead: knotting the new warp to the old one end for end, which is much faster but
     * only works when the new threading is the same as the old.
     */
    public static double tyingInHours(int ends, double endsPerHour) {
        Check.positiveInteger(ends, "ends");
        Check.positiveNumber(endsPerHour, "endsPerHour");
        return Numeric.roundTo(ends / endsPerHour, 3);
    }

    /** Whether a new warp can be tied in, which needs the same threading. */
    public static boolean canTieIn(List<Integer> oldThreading, List<Integer> newThreading) {
        Check.array(oldThreading, "oldThreading", 1);
        Check.array(newThreading, "newThreading", 1);
        return oldThreading.equals(newThreading);
    }

    /** The hours a warp change takes at the usual rates, choosing the method that applies. */
    public static Changeover changeoverHours(List<Integer> oldThreading, List<Integer> newThreading) {
        return changeoverHours(oldThreading, newThreading, OptionalDouble.empty());
    }

    /** The hours a warp change takes at a stated rate, choosing the method that applies. */
    public static Changeover changeoverHours(
            List<Integer> oldThreading, List<Integer> newThreading, double endsPerHour) {
        return changeoverHours(oldThreading, newThreading, OptionalDouble.of(endsPerHour));
    }

    private static Changeover changeoverHours(
            List<Integer> oldThreading, List<Integer> newThreading, OptionalDouble endsPerHour) {
        boolean tie = canTieIn(oldThreading, newThreading);
        int ends = newThreading.size();
        if (ends == 0) {
            throw new InvalidArgumentException("a warp change needs a warp", JsonObject.EMPTY);
        }
        if (tie) {
            return new Changeover("tie in", tyingInHours(ends, endsPerHour.orElse(2000)));
        }
        return new Changeover("draw in", drawingInHours(ends, endsPerHour.orElse(400)));
    }
}
