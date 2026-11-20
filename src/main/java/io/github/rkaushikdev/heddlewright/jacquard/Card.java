package io.github.rkaushikdev.heddlewright.jacquard;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.ArrayList;
import java.util.List;

/**
 * The punched card.
 *
 * <p>One card is one pick. Its holes line up with the needle board, and a needle that finds a hole
 * passes through and leaves its hook standing in the griffe, so the hook lifts. A needle that meets
 * solid card is pushed back and its hook is left behind. That is the convention this package uses: a
 * hole lifts.
 *
 * <p>The card carries more than the pattern. It has peg holes that the cylinder's pegs drive, and
 * lacing holes along both ends where the cards are strung together into a chain.
 *
 * <p>The record does not check that its stated size matches its grid, because a card that has been
 * altered by hand is exactly what {@link #assertCardFitsMachine} is there to catch.
 *
 * @param number the card's place in the chain, from one
 * @param rows the rows of the board it was cut for
 * @param columns the columns of the board it was cut for
 * @param machine the name of the machine it was cut for
 * @param holes one cell per needle, 1 where a hole is punched
 */
public record Card(int number, int rows, int columns, String machine, Matrix holes) {

    /** Peg holes along a card. */
    public static final int PEG_HOLES = 2;

    /** Lacing holes at each end of a card. */
    public static final int LACING_HOLES = 2;

    /** A card's size on the bench, in millimetres. */
    public record Size(double width, double height, double pitchMm) {}

    /** The same card under a different number, which is bookkeeping rather than pattern. */
    public Card withNumber(int value) {
        return new Card(value, rows, columns, machine, holes);
    }

    /** An unpunched card, numbered one, sized for a machine's needle board. */
    public static Card blankCard(Machine machine) {
        return blankCard(machine, 1);
    }

    /** An unpunched card sized for a machine's needle board. */
    public static Card blankCard(Machine machine, int number) {
        Check.positiveInteger(number, "number");
        return new Card(
                number,
                machine.rows(),
                machine.columns(),
                machine.name(),
                Matrix.create(machine.rows(), machine.columns()));
    }

    /** Punch card one so the named hooks lift. */
    public static Card punchCard(Machine machine, List<?> hooks) {
        return punchCard(machine, hooks, 1);
    }

    /** Punch a card so the named hooks lift. */
    public static Card punchCard(Machine machine, List<?> hooks, int number) {
        List<Object> named = Check.array(hooks, "hooks");
        Card card = blankCard(machine, number);
        int[][] holes = card.holes().toArray();
        for (int i = 0; i < named.size(); i += 1) {
            int hook = Check.integer(named.get(i), "hooks[" + i + "]", Bounds.atLeast(1));
            if (hook > machine.hooks()) {
                throw new CapacityException(
                        "that hook is past the end of the machine", Json.obj("hook", hook, "hooks", machine.hooks()));
            }
            Machine.Position position = Machine.hookPosition(machine, hook);
            holes[position.row() - 1][position.column() - 1] = 1;
        }
        return new Card(card.number(), card.rows(), card.columns(), card.machine(), Matrix.of(holes));
    }

    /** The hooks a card lifts on a machine where a hole lifts, in machine order. */
    public static List<Integer> cardHooks(Card card, Machine machine) {
        return cardHooks(card, machine, true);
    }

    /** The hooks a card lifts, in machine order. */
    public static List<Integer> cardHooks(Card card, Machine machine, boolean holeMeansLift) {
        assertCardFitsMachine(card, machine);
        List<Integer> out = new ArrayList<>();
        for (int row = 1; row <= machine.rows(); row += 1) {
            for (int column = 1; column <= machine.columns(); column += 1) {
                boolean punched = card.holes().get(row - 1, column - 1) == 1;
                if (punched == holeMeansLift) {
                    out.add(Machine.hookAt(machine, row, column));
                }
            }
        }
        return List.copyOf(out);
    }

    /** How many holes are punched, which is how much of the card is cut away. */
    public static int holeCount(Card card) {
        Check.required(card.holes(), "card.holes");
        return card.holes().countOnes();
    }

    /** The share of the card that is cut away, which is what weakens it. */
    public static double cutAwayRatio(Card card) {
        int total = card.rows() * card.columns();
        return Numeric.roundTo((double) holeCount(card) / total, 4);
    }

    /** Refuse a card whose grid does not match the machine it is meant for. */
    public static Card assertCardFitsMachine(Card card, Machine machine) {
        Check.required(card.holes(), "card.holes");
        if (card.rows() != machine.rows() || card.columns() != machine.columns()) {
            throw new InvalidArgumentException(
                    "the card does not fit that machine",
                    Json.obj(
                            "card",
                            Json.obj("rows", card.rows(), "columns", card.columns()),
                            "machine",
                            Json.obj("rows", machine.rows(), "columns", machine.columns())));
        }
        if (card.holes().rows() != machine.rows() || card.holes().cols() != machine.columns()) {
            throw new InvalidArgumentException(
                    "the card grid does not match its own stated size",
                    Json.obj(
                            "stated",
                            Json.obj("rows", card.rows(), "columns", card.columns()),
                            "grid",
                            Json.obj(
                                    "rows",
                                    card.holes().rows(),
                                    "columns",
                                    card.holes().cols())));
        }
        return card;
    }

    /** The physical size of a card with the usual 12 mm margins. */
    public static Size cardSizeMm(Machine machine) {
        return cardSizeMm(machine, 12);
    }

    /**
     * The physical size of a card in millimetres, worked out from the needle pitch plus the margins
     * that carry the peg and lacing holes.
     */
    public static Size cardSizeMm(Machine machine, double marginMm) {
        return new Size(
                Numeric.roundTo(machine.columns() * machine.pitchMm() + marginMm * 2, 2),
                Numeric.roundTo(machine.rows() * machine.pitchMm() + marginMm * 2, 2),
                machine.pitchMm());
    }

    /** Two cards lift the same hooks when their holes match. */
    public static boolean sameCard(Card a, Card b) {
        Check.required(a.holes(), "a.holes");
        Check.required(b.holes(), "b.holes");
        return a.holes().format().equals(b.holes().format());
    }

    /** A card with nothing punched leaves every hook down, so no shed opens. */
    public static boolean isBlank(Card card) {
        return holeCount(card) == 0;
    }

    /** A card punched right through lifts everything, which also opens no shed. */
    public static boolean isFullyPunched(Card card) {
        return holeCount(card) == card.rows() * card.columns();
    }

    /** The card printed for a paper record, holes as O and solid card as a dot. */
    public static String renderCard(Card card) {
        Check.required(card.holes(), "card.holes");
        return card.holes().format('O', '.');
    }
}
