package io.github.rkaushikdev.heddlewright.jacquard;

import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A Jacquard machine.
 *
 * <p>A machine is counted in hooks. Each hook pulls one harness cord, and one card position selects
 * one hook, so the hook count is the widest pattern repeat the machine can weave before the harness
 * has to repeat it across the cloth.
 *
 * <p>The needle board is a grid, and the card is punched to match it, so a machine carries a row and
 * column layout as well as a total. Card positions are read along the rows of the board, which is
 * why a machine of the same hook count can still need a differently punched card.
 *
 * @param name what the shed calls it
 * @param hooks rows times columns
 * @param rows rows of the needle board
 * @param columns columns of the needle board
 * @param lift whether one card or two is read each cycle
 * @param pitchMm needle spacing
 */
public record Machine(String name, int hooks, int rows, int columns, Lift lift, double pitchMm) {

    /** How many cards a machine reads each cycle. */
    public enum Lift {
        /** One card a cycle. */
        SINGLE,
        /** Two cards a cycle, so twice as fast. */
        DOUBLE;

        /** Read a lift by its name, refusing anything else. */
        public static Lift parse(Object text) {
            return Check.member(text, "lift", values());
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The lifts, single first. */
    public static final List<Lift> LIFT_TYPES = List.of(Lift.values());

    /** Where a hook sits on the needle board, both counted from one. */
    public record Position(int row, int column) {}

    /** How a repeat covers the width: whole repeats, ends over, and hooks left standing. */
    public record Repeats(int repeats, int remainder, int hooksIdle) {}

    private static final Map<String, Machine> MACHINES = register(
            new Machine("100 hook", 100, 4, 25, Lift.SINGLE, 8),
            new Machine("200 hook", 200, 8, 25, Lift.SINGLE, 8),
            new Machine("400 hook", 400, 8, 50, Lift.SINGLE, 8),
            new Machine("600 hook", 600, 8, 75, Lift.DOUBLE, 8),
            new Machine("896 hook", 896, 8, 112, Lift.DOUBLE, 4),
            new Machine("1344 hook", 1344, 12, 112, Lift.DOUBLE, 4));

    private static Map<String, Machine> register(Machine... machines) {
        Map<String, Machine> out = new LinkedHashMap<>();
        for (Machine machine : machines) {
            out.put(machine.name(), machine);
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    /** Every machine on the books, smallest first. */
    public static List<Machine> listMachines() {
        return MACHINES.values().stream()
                .sorted(Comparator.comparingInt(Machine::hooks))
                .toList();
    }

    /** Look a machine up by name. */
    public static Machine machineByName(String name) {
        Check.nonEmptyString(name, "name");
        Machine machine = MACHINES.get(name);
        if (machine == null) {
            throw new NotFoundException(
                    "no machine called \"" + name + "\"",
                    Json.obj("name", name, "known", List.copyOf(MACHINES.keySet())));
        }
        return machine;
    }

    /** The smallest machine that carries a repeat of this many ends. */
    public static Machine smallestMachineFor(int hooksNeeded) {
        Check.positiveInteger(hooksNeeded, "hooksNeeded");
        List<Machine> machines = listMachines();
        return machines.stream()
                .filter(machine -> machine.hooks() >= hooksNeeded)
                .findFirst()
                .orElseThrow(() -> new CapacityException(
                        "no machine on the books carries that many hooks",
                        Json.obj(
                                "hooksNeeded",
                                hooksNeeded,
                                "largest",
                                machines.get(machines.size() - 1).hooks())));
    }

    /** Build a single-lift machine at the usual 8 mm pitch that is not on the books. */
    public static Machine defineMachine(String name, int rows, int columns) {
        return defineMachine(name, rows, columns, Lift.SINGLE, 8);
    }

    /** Build a machine that is not on the books, checking the layout adds up. */
    public static Machine defineMachine(String name, int rows, int columns, Lift lift, double pitchMm) {
        Check.nonEmptyString(name, "name");
        Check.positiveInteger(rows, "rows");
        Check.positiveInteger(columns, "columns");
        Check.required(lift, "lift");
        return new Machine(name, rows * columns, rows, columns, lift, pitchMm);
    }

    /** Where on the needle board a given hook sits, counting along the rows. */
    public static Position hookPosition(Machine machine, int hook) {
        Check.positiveInteger(hook, "hook");
        if (hook > machine.hooks()) {
            throw new CapacityException(
                    "that hook is past the end of the machine", Json.obj("hook", hook, "hooks", machine.hooks()));
        }
        int index = hook - 1;
        return new Position(index / machine.columns() + 1, index % machine.columns() + 1);
    }

    /** The hook at a row and column of the needle board. */
    public static int hookAt(Machine machine, int row, int column) {
        Check.positiveInteger(row, "row");
        Check.positiveInteger(column, "column");
        if (row > machine.rows() || column > machine.columns()) {
            throw new CapacityException(
                    "that position is off the needle board",
                    Json.obj("row", row, "column", column, "rows", machine.rows(), "columns", machine.columns()));
        }
        return (row - 1) * machine.columns() + column;
    }

    /** How many times a repeat covers the cloth width, and what is left over. */
    public static Repeats repeatsAcross(Machine machine, int totalEnds, int hooksPerRepeat) {
        Check.positiveInteger(totalEnds, "totalEnds");
        Check.positiveInteger(hooksPerRepeat, "hooksPerRepeat");
        if (hooksPerRepeat > machine.hooks()) {
            throw new CapacityException(
                    "the repeat needs more hooks than the machine has",
                    Json.obj("hooksPerRepeat", hooksPerRepeat, "hooks", machine.hooks()));
        }
        return new Repeats(totalEnds / hooksPerRepeat, totalEnds % hooksPerRepeat, machine.hooks() - hooksPerRepeat);
    }

    /** Refuse a repeat the machine cannot hold, with the shortfall named. */
    public static Machine assertFits(Machine machine, int hooksNeeded) {
        Check.positiveInteger(hooksNeeded, "hooksNeeded");
        if (hooksNeeded > machine.hooks()) {
            throw new CapacityException(
                    machine.name() + " has " + machine.hooks() + " hooks",
                    Json.obj(
                            "machine",
                            machine.name(),
                            "hooks",
                            machine.hooks(),
                            "hooksNeeded",
                            hooksNeeded,
                            "short",
                            hooksNeeded - machine.hooks()));
        }
        return machine;
    }

    /** A double-lift machine reads two cards at once, so it weaves twice as fast. */
    public static int cardsPerCycle(Machine machine) {
        return machine.lift() == Lift.DOUBLE ? 2 : 1;
    }
}
