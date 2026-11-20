package io.github.rkaushikdev.heddlewright.weave;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Leno and its imitation.
 *
 * <p>True leno crosses one warp end over another between picks, and no arrangement of shafts can do
 * that on its own: it needs a doup, a half heddle that carries the crossing end over its neighbour.
 * What a shaft loom can do is mock leno, which opens holes by putting tight interlacement next to
 * floats so that the threads gather away from each other. It is not gauze and it does not hold like
 * gauze, but it looks like it and it weaves on four shafts.
 */
public final class Leno {

    /** A corner where the threads of mock leno pull apart. */
    public record Hole(int end, int pick) {}

    /** One crossing: the doup end, the standard end it crosses over, and the side it crosses to. */
    public record DoupPair(int doup, int standard, String side) {}

    /** A reason a doup plan will not open, with where it happens. */
    public record Conflict(String code, OptionalInt at, int end, String message) {}

    private Leno() {}

    /** Mock leno in groups of two. */
    public static Matrix mockLeno() {
        return mockLeno(2);
    }

    /**
     * One repeat of mock leno. Ends and picks are grouped, and a group that meets itself interlaces
     * plainly while a group meeting another floats, so the threads pull together into bundles and
     * leave a hole at each corner.
     */
    public static Matrix mockLeno(int unit) {
        Check.positiveInteger(unit, "unit");
        if (unit < 2) {
            throw new DraftException("a mock leno group has to be at least two threads", Json.obj("unit", unit));
        }
        int size = unit * 2;
        int[][] repeat = new int[size][size];
        for (int p = 0; p < size; p += 1) {
            for (int e = 0; e < size; e += 1) {
                repeat[p][e] = p / unit == e / unit ? (p + e) % 2 : 1;
            }
        }
        return Matrix.of(repeat);
    }

    /** Where the holes fall in mock leno of groups of two. */
    public static List<Hole> holePositions() {
        return holePositions(2);
    }

    /** Where the holes fall, as the 1-based end and pick of each corner. */
    public static List<Hole> holePositions(int unit) {
        Check.positiveInteger(unit, "unit");
        int size = unit * 2;
        return List.of(new Hole(unit, unit), new Hole(size, size));
    }

    /**
     * A doup plan for true leno: which end crosses and which it crosses over. The crossing end is
     * called the doup end and the other the standard end. Each pair is a plain record of the two.
     */
    public static List<DoupPair> doupPlan(List<?> pairs) {
        List<Object> list = Check.array(pairs, "pairs", 1);
        List<DoupPair> plan = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index += 1) {
            JsonObject pair = Check.plainObject(list.get(index), "pairs[" + index + "]");
            int doup = Check.integer(pair.get("doup"), "pairs[" + index + "].doup", Bounds.atLeast(1));
            int standard = Check.integer(pair.get("standard"), "pairs[" + index + "].standard", Bounds.atLeast(1));
            if (doup == standard) {
                throw new DraftException("an end cannot cross over itself", Json.obj("at", index + 1, "end", doup));
            }
            plan.add(new DoupPair(doup, standard, doup < standard ? "left" : "right"));
        }
        return List.copyOf(plan);
    }

    /** The ends a doup plan needs threaded through the half heddles. */
    public static List<Integer> doupEnds(List<DoupPair> plan) {
        return plan.stream().map(DoupPair::doup).sorted().toList();
    }

    /**
     * A doup end may only cross over one standard end, and only if nothing else claims it. Two doups
     * sharing a standard end lock the shed shut.
     */
    public static List<Conflict> doupConflicts(List<DoupPair> plan) {
        Map<Integer, Integer> claimed = new HashMap<>();
        List<Conflict> findings = new ArrayList<>();
        for (int index = 0; index < plan.size(); index += 1) {
            DoupPair pair = plan.get(index);
            if (claimed.containsKey(pair.standard())) {
                findings.add(new Conflict(
                        "shared_standard",
                        OptionalInt.of(index + 1),
                        pair.standard(),
                        "end " + pair.standard() + " is crossed by two doups"));
            }
            claimed.put(pair.standard(), index + 1);
        }
        Set<Integer> doups = new HashSet<>(doupEnds(plan));
        for (DoupPair pair : plan) {
            if (doups.contains(pair.standard())) {
                findings.add(new Conflict(
                        "doup_as_standard",
                        OptionalInt.empty(),
                        pair.standard(),
                        "end " + pair.standard() + " is both a doup and a standard end"));
            }
        }
        return List.copyOf(findings);
    }

    /** Refuse a doup plan that will not open. */
    public static List<DoupPair> assertDoupPlanSound(List<DoupPair> plan) {
        List<Conflict> findings = doupConflicts(plan);
        if (!findings.isEmpty()) {
            throw new DraftException("that doup plan will not open a shed", Json.obj("findings", findings));
        }
        return plan;
    }

    /** How many ends one leno stripe takes, doup ends included. */
    public static int endsForLenoStripe(int pairs) {
        return endsForLenoStripe(pairs, 1);
    }

    /** How many ends a leno stripe takes, doup ends included. */
    public static int endsForLenoStripe(int pairs, int repeats) {
        Check.positiveInteger(pairs, "pairs");
        Check.positiveInteger(repeats, "repeats");
        return pairs * 2 * repeats;
    }

    /** True when a repeat could be woven on shafts alone, without a doup. */
    public static boolean weavableOnShafts(Matrix repeat) {
        return repeat.allRows().stream()
                .allMatch(row -> row.contains(1) || row.stream().allMatch(cell -> cell == 0));
    }
}
