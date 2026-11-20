package io.github.rkaushikdev.heddlewright.jacquard;

import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Indexing;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The harness tie.
 *
 * <p>A Jacquard machine has far fewer hooks than the cloth has ends, so the harness cords are tied so
 * that one hook pulls several ends across the width. Which ends a hook pulls is the tie, and the tie
 * decides how the pattern repeats: straight through gives the same figure over and over, a point tie
 * mirrors it, and a centre tie puts one figure in the middle of the cloth. A tie is a list of hook
 * numbers, one per end.
 */
public final class Harness {

    /** The ties a harness builder knows. */
    public enum TieType {
        /** Straight through. */
        STRAIGHT,
        /** Mirrored at each turn. */
        POINT,
        /** Out from the middle. */
        CENTRE,
        /** A body with a border block at each side. */
        BORDERED;

        /** Read a tie type by its name, refusing anything else. */
        public static TieType parse(Object text) {
            return Check.member(text, "type", values());
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The tie types, straight first. */
    public static final List<TieType> TIE_TYPES = List.of(TieType.values());

    /** The row and hole of the comber board a cord runs through. */
    public record BoardHole(int row, int hole) {}

    /** A comber board's size: rows deep, holes per row, and holes in use. */
    public record BoardSize(int rowsDeep, int holesPerRow, int holes) {}

    private Harness() {}

    /** Straight through: hook 1 to end 1, and round again every {@code hooks} ends. */
    public static List<Integer> straightTie(int hooks, int totalEnds) {
        Check.positiveInteger(hooks, "hooks");
        Check.positiveInteger(totalEnds, "totalEnds");
        List<Integer> out = new ArrayList<>(totalEnds);
        for (int e = 0; e < totalEnds; e += 1) {
            out.add(Numeric.mod(e, hooks) + 1);
        }
        return List.copyOf(out);
    }

    /**
     * A point tie mirrors the figure, so one repeat covers 2h - 2 ends and the cloth reads as a
     * reflected pattern rather than a marching one.
     */
    public static List<Integer> pointTie(int hooks, int totalEnds) {
        Check.positiveInteger(hooks, "hooks");
        Check.positiveInteger(totalEnds, "totalEnds");
        if (hooks < 2) {
            throw new DraftException("a point tie needs at least two hooks", Json.obj("hooks", hooks));
        }
        int cycle = 2 * hooks - 2;
        List<Integer> out = new ArrayList<>(totalEnds);
        for (int e = 0; e < totalEnds; e += 1) {
            int at = Numeric.mod(e, cycle);
            out.add(at < hooks ? at + 1 : cycle - at + 1);
        }
        return List.copyOf(out);
    }

    /**
     * A centre tie runs one figure out from the middle of the cloth and mirrors it back, which is how
     * a shawl or a panel is centred on the loom.
     */
    public static List<Integer> centreTie(int hooks, int totalEnds) {
        Check.positiveInteger(hooks, "hooks");
        Check.positiveInteger(totalEnds, "totalEnds");
        double middle = totalEnds / 2.0;
        List<Integer> out = new ArrayList<>(totalEnds);
        for (int e = 0; e < totalEnds; e += 1) {
            int fromCentre = e < middle ? (int) Math.floor(middle) - 1 - e : e - (int) Math.ceil(middle);
            out.add(Numeric.mod(fromCentre, hooks) + 1);
        }
        return List.copyOf(out);
    }

    /**
     * A bordered tie reserves a block of hooks for the selvedge figure and gives the rest to the
     * body, which is how a bordered shawl is woven in one piece.
     */
    public static List<Integer> borderedTie(int bodyHooks, int borderHooks, int bodyEnds, int borderEnds) {
        Check.positiveInteger(bodyHooks, "bodyHooks");
        Check.positiveInteger(borderHooks, "borderHooks");
        Check.positiveInteger(bodyEnds, "bodyEnds");
        Check.positiveInteger(borderEnds, "borderEnds");
        List<Integer> left = new ArrayList<>(borderEnds);
        for (int e = 0; e < borderEnds; e += 1) {
            left.add(Numeric.mod(e, borderHooks) + 1);
        }
        List<Integer> out = new ArrayList<>(left);
        for (int e = 0; e < bodyEnds; e += 1) {
            out.add(borderHooks + Numeric.mod(e, bodyHooks) + 1);
        }
        List<Integer> right = new ArrayList<>(left);
        Collections.reverse(right);
        out.addAll(right);
        return List.copyOf(out);
    }

    /**
     * Build a tie by name from a style sheet's record, so a style sheet can name one rather than list
     * it. Straight, point and centre ties read {@code hooks} and {@code totalEnds}; a bordered tie
     * reads {@code bodyHooks}, {@code borderHooks}, {@code bodyEnds} and {@code borderEnds}.
     */
    public static List<Integer> tieByType(Object type, Object options) {
        TieType tie = TieType.parse(type);
        JsonObject record = Check.plainObject(options, "options");
        return switch (tie) {
            case STRAIGHT -> straightTie(hooks(record), totalEnds(record));
            case POINT -> pointTie(hooks(record), totalEnds(record));
            case CENTRE -> centreTie(hooks(record), totalEnds(record));
            case BORDERED ->
                borderedTie(
                        Check.positiveInteger(record.get("bodyHooks"), "bodyHooks"),
                        Check.positiveInteger(record.get("borderHooks"), "borderHooks"),
                        Check.positiveInteger(record.get("bodyEnds"), "bodyEnds"),
                        Check.positiveInteger(record.get("borderEnds"), "borderEnds"));
        };
    }

    private static int hooks(JsonObject record) {
        return Check.positiveInteger(record.get("hooks"), "hooks");
    }

    private static int totalEnds(JsonObject record) {
        return Check.positiveInteger(record.get("totalEnds"), "totalEnds");
    }

    /** How many ends each hook pulls, hook one first, which is the load on its cord. */
    public static List<Integer> hookLoad(List<Integer> tie, int hooks) {
        Check.array(tie, "tie", 1);
        Check.positiveInteger(hooks, "hooks");
        int[] load = new int[hooks];
        for (int e = 0; e < tie.size(); e += 1) {
            int hook = tie.get(e);
            if (hook < 1 || hook > hooks) {
                throw new CapacityException(
                        "end " + (e + 1) + " is tied to a hook the machine does not have",
                        Json.obj("end", e + 1, "hook", hook, "hooks", hooks));
            }
            load[hook - 1] += 1;
        }
        return java.util.Arrays.stream(load).boxed().toList();
    }

    /** Hooks pulling nothing, whose cords could be taken out of the comber board. */
    public static List<Integer> idleHooks(List<Integer> tie, int hooks) {
        return Indexing.positionsOf(hookLoad(tie, hooks), 0);
    }

    /** The 1-based ends a given hook pulls. */
    public static List<Integer> endsOnHook(List<Integer> tie, int hook) {
        Check.positiveInteger(hook, "hook");
        return Indexing.positionsOf(tie, hook);
    }

    /** How wide one pattern repeat is, in ends, for a tie of this kind. */
    public static int repeatWidth(TieType type, int hooks) {
        Check.required(type, "type");
        Check.positiveInteger(hooks, "hooks");
        return type == TieType.POINT ? 2 * hooks - 2 : hooks;
    }

    /**
     * The comber board spaces the cords above the loom. Each cord passes through one hole, and the
     * board is a grid, so this says which row and hole a cord runs through.
     */
    public static BoardHole comberBoardPosition(int end, int rowsDeep, int holesPerRow) {
        Check.positiveInteger(end, "end");
        Check.positiveInteger(rowsDeep, "rowsDeep");
        Check.positiveInteger(holesPerRow, "holesPerRow");
        int capacity = rowsDeep * holesPerRow;
        if (end > capacity) {
            throw new CapacityException(
                    "the comber board has no hole for that end", Json.obj("end", end, "capacity", capacity));
        }
        int index = end - 1;
        return new BoardHole(index % rowsDeep + 1, index / rowsDeep + 1);
    }

    /** How many holes a comber board eight rows deep needs for a warp of this width. */
    public static BoardSize comberBoardSize(int totalEnds) {
        return comberBoardSize(totalEnds, 8);
    }

    /** How many holes a comber board needs for a warp of this width. */
    public static BoardSize comberBoardSize(int totalEnds, int rowsDeep) {
        Check.positiveInteger(totalEnds, "totalEnds");
        Check.positiveInteger(rowsDeep, "rowsDeep");
        return new BoardSize(rowsDeep, (int) Math.ceil((double) totalEnds / rowsDeep), totalEnds);
    }
}
