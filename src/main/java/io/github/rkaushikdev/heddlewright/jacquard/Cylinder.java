package io.github.rkaushikdev.heddlewright.jacquard;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Limits;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.List;
import java.util.Locale;

/**
 * The cylinder, and how the chain runs over it.
 *
 * <p>The cylinder is a square prism that turns a quarter at a time, bringing the next card against
 * the needle board. Pegs on its faces drive the cards along, and the chain hangs in a loop
 * underneath. Because it turns rather than slides, the chain can also be run backwards to pick a lost
 * pick up again, and getting that arithmetic right is what stops a mispick becoming a fault.
 */
public final class Cylinder {

    /** The faces of a square cylinder. */
    public static final int CYLINDER_FACES = 4;

    /** Which way the chain runs. */
    public enum Direction {
        /** Weaving. */
        FORWARD,
        /** Backing off. */
        REVERSE;

        /** Read a direction by its name, refusing anything else. */
        public static Direction parse(Object text) {
            return Check.member(text, "direction", values());
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The directions, forward first. */
    public static final List<Direction> DIRECTIONS = List.of(Direction.values());

    /** Whole turns of the chain and the picks left over. */
    public record Revolutions(int whole, int remainder) {}

    private Cylinder() {}

    /** Which face of a four-faced cylinder card {@code number} lands on. */
    public static int faceForCard(int number) {
        return faceForCard(number, CYLINDER_FACES);
    }

    /** Which face of the cylinder card {@code number} lands on, counting from face one. */
    public static int faceForCard(int number, int faces) {
        Check.positiveInteger(number, "number");
        Check.positiveInteger(faces, "faces");
        return Numeric.mod(number - 1, faces) + 1;
    }

    /** How far a four-faced cylinder has turned after a run of picks, in degrees. */
    public static double turnAngle(int picks) {
        return turnAngle(picks, CYLINDER_FACES);
    }

    /** How far the cylinder has turned after a run of picks, in degrees. */
    public static double turnAngle(int picks, int faces) {
        Check.integer(picks, "picks", Bounds.atLeast(0));
        Check.positiveInteger(faces, "faces");
        double angle = ((double) picks * 360) / faces;
        return ((angle % 360) + 360) % 360;
    }

    /** Where the chain stands after weaving forwards. */
    public static int advance(int at, int picks, int cards) {
        return advance(at, picks, cards, Direction.FORWARD);
    }

    /**
     * Where the chain stands after weaving. {@code at} is the 1-based card next against the board,
     * and it wraps because the chain is a loop.
     */
    public static int advance(int at, int picks, int cards, Direction direction) {
        Check.positiveInteger(at, "at");
        Check.integer(picks, "picks", Bounds.atLeast(0));
        Check.positiveInteger(cards, "cards");
        Check.required(direction, "direction");
        if (at > cards) {
            throw new InvalidArgumentException("the chain has no such card", Json.obj("at", at, "cards", cards));
        }
        int step = direction == Direction.FORWARD ? picks : -picks;
        return Numeric.mod(at - 1 + step, cards) + 1;
    }

    /** How many picks it takes to get from one card to another going forwards. */
    public static int picksBetween(int from, int to, int cards) {
        Check.positiveInteger(from, "from");
        Check.positiveInteger(to, "to");
        Check.positiveInteger(cards, "cards");
        return Numeric.mod(to - from, cards);
    }

    /**
     * Backing the loom off after a broken pick: how far to reverse the chain so the next card is the
     * one that should have been woven.
     */
    public static int backOff(int at, int picksToUndo, int cards) {
        return advance(at, picksToUndo, cards, Direction.REVERSE);
    }

    /** How many full turns of the chain a length of cloth takes. */
    public static Revolutions chainRevolutions(int picks, int cards) {
        Check.integer(picks, "picks", Bounds.atLeast(0));
        Check.positiveInteger(cards, "cards");
        return new Revolutions(picks / cards, picks % cards);
    }

    /**
     * A cylinder can only carry so much chain before the loop drags on the floor. The limit is set by
     * how much card the machine's chain rail will hold.
     */
    public static int assertChainFits(int cards, int railCapacity) {
        Check.positiveInteger(cards, "cards");
        Check.positiveInteger(railCapacity, "railCapacity");
        Limits.assertFits(
                cards,
                railCapacity,
                "chain",
                "the chain is longer than the rail will carry",
                Json.obj("cards", cards, "railCapacity", railCapacity));
        return cards;
    }

    /** How long a pattern runs on the cloth before it repeats, in centimetres. */
    public static double patternLengthCm(int cards, double picksPerCm) {
        Check.positiveInteger(cards, "cards");
        Check.positiveNumber(picksPerCm, "picksPerCm");
        return Numeric.roundTo(cards / picksPerCm, 3);
    }

    /** How many cards a length of cloth needs at a given pick density. */
    public static int cardsForLength(double lengthCm, double picksPerCm) {
        Check.positiveNumber(lengthCm, "lengthCm");
        Check.positiveNumber(picksPerCm, "picksPerCm");
        return (int) Math.ceil(lengthCm * picksPerCm);
    }
}
