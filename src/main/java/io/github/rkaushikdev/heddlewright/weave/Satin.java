package io.github.rkaushikdev.heddlewright.weave;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Satins and sateens.
 *
 * <p>A regular satin over n threads binds once per thread, stepping by a fixed move number. The
 * move has to be coprime with n, so that the binding points visit every thread, and it may not be 1
 * or n-1, which would lay the points next to each other and make a twill instead. That rules out a
 * regular satin on four or six threads, which is why the trade builds those as irregular satins
 * from a written move sequence.
 */
public final class Satin {

    /** Which face a satin shows. */
    public enum Face {
        /** Warp everywhere but the binding points: the satin. */
        WARP,
        /** Weft everywhere but the binding points: the sateen. */
        WEFT;

        /** The two faces, warp first. */
        public static final List<Face> SATIN_FACES = List.of(values());

        /** Read a face by its name, refusing anything else. */
        public static Face parse(Object text) {
            return Check.member(text, "face", values());
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private Satin() {}

    /** Every legal move number for an n-thread regular satin, ascending. */
    public static List<Integer> regularSatinMoves(int threads) {
        Check.positiveInteger(threads, "threads");
        List<Integer> moves = new ArrayList<>();
        for (int move = 2; move < threads - 1; move += 1) {
            if (Numeric.gcd(move, threads) == 1) {
                moves.add(move);
            }
        }
        return List.copyOf(moves);
    }

    /** True when a regular satin exists at all on this many threads. */
    public static boolean hasRegularSatin(int threads) {
        return !regularSatinMoves(threads).isEmpty();
    }

    /** Check a move number, throwing with the reason it cannot be used. */
    public static int assertSatinMove(int threads, int move) {
        Check.positiveInteger(threads, "threads");
        Check.integer(move, "move", Bounds.atLeast(1));
        if (threads < 5) {
            throw new DraftException("a regular satin needs at least five threads", Json.obj("threads", threads));
        }
        if (move == 1 || move == threads - 1) {
            throw new DraftException(
                    "a move of one either way lays the binding points in a twill line",
                    Json.obj("threads", threads, "move", move));
        }
        if (move >= threads) {
            throw new DraftException(
                    "the move must be smaller than the repeat", Json.obj("threads", threads, "move", move));
        }
        if (Numeric.gcd(move, threads) != 1) {
            throw new DraftException(
                    "the move shares a factor with the repeat, so it closes early",
                    Json.obj("threads", threads, "move", move, "commonFactor", Numeric.gcd(move, threads)));
        }
        return move;
    }

    /** One repeat of a warp-faced regular satin. */
    public static Matrix satinWeave(int threads, int move) {
        return satinWeave(threads, move, Face.WARP);
    }

    /**
     * One repeat of a regular satin. A warp-faced satin shows warp everywhere except one binding
     * point per thread; a weft-faced one, the sateen, is its reverse.
     */
    public static Matrix satinWeave(int threads, int move, Face face) {
        assertSatinMove(threads, move);
        Check.required(face, "face");
        int ground = face == Face.WARP ? 1 : 0;
        int[][] repeat = new int[threads][threads];
        for (int p = 0; p < threads; p += 1) {
            java.util.Arrays.fill(repeat[p], ground);
            repeat[p][Numeric.mod(p * move, threads)] = 1 - ground;
        }
        return Matrix.of(repeat);
    }

    /** A warp-faced irregular satin from a move sequence. */
    public static Matrix irregularSatin(int threads, List<?> moves) {
        return irregularSatin(threads, moves, Face.WARP);
    }

    /**
     * An irregular satin built from an explicit move sequence, which is how a four or six thread
     * satin is written when no regular move exists. The sequence gives the step from each binding
     * point to the next.
     */
    public static Matrix irregularSatin(int threads, List<?> moves, Face face) {
        Check.positiveInteger(threads, "threads");
        List<Object> steps = Check.array(moves, "moves", 1);
        Check.required(face, "face");
        if (steps.size() != threads) {
            throw new DraftException(
                    "an irregular satin needs one move per thread",
                    Json.obj("threads", threads, "moves", steps.size()));
        }
        List<Integer> positions = new ArrayList<>(threads);
        int at = 0;
        for (int i = 0; i < steps.size(); i += 1) {
            int step = Check.integer(steps.get(i), "moves[" + i + "]");
            positions.add(at);
            at = Numeric.mod(at + step, threads);
        }
        Set<Integer> reached = new HashSet<>(positions);
        if (reached.size() != threads) {
            throw new DraftException(
                    "the move sequence does not reach every thread",
                    Json.obj("threads", threads, "reached", reached.size()));
        }
        int ground = face == Face.WARP ? 1 : 0;
        int[][] repeat = new int[threads][threads];
        for (int p = 0; p < threads; p += 1) {
            java.util.Arrays.fill(repeat[p], ground);
            repeat[p][positions.get(p)] = 1 - ground;
        }
        return Matrix.of(repeat);
    }

    /**
     * The four thread irregular satin, bound at ends 1, 3, 2, 4. On four threads no arrangement can
     * keep every binding point clear of its neighbours, which is why this cloth reads as a crow weave
     * rather than a true satin.
     */
    public static List<Integer> fourThreadSatinMoves() {
        return List.of(2, 3, 2, 1);
    }

    /** The six thread irregular satin, bound at ends 1, 3, 5, 2, 6, 4. */
    public static List<Integer> sixThreadSatinMoves() {
        return List.of(2, 2, 3, 4, 4, 3);
    }

    /** Where the binding point of a warp-faced satin sits on each pick, as 1-based end numbers. */
    public static List<Integer> bindingPoints(Matrix repeat) {
        return bindingPoints(repeat, Face.WARP);
    }

    /** Where the binding point sits on each pick, as 1-based end numbers. */
    public static List<Integer> bindingPoints(Matrix repeat, Face face) {
        Check.required(face, "face");
        int looking = face == Face.WARP ? 0 : 1;
        return repeat.allRows().stream().map(row -> row.indexOf(looking) + 1).toList();
    }

    /**
     * A satin is sound when no two binding points touch, side by side or diagonally, because that is
     * what a satin is for: an unbroken face.
     */
    public static boolean bindingPointsIsolated(Matrix repeat) {
        int threads = repeat.rows();
        List<Integer> points =
                bindingPoints(repeat).stream().map(end -> end - 1).toList();
        for (int p = 0; p < threads; p += 1) {
            int next = points.get((p + 1) % threads);
            int here = points.get(p);
            int gap = Math.min(Numeric.mod(next - here, threads), Numeric.mod(here - next, threads));
            if (gap <= 1) {
                return false;
            }
        }
        return true;
    }

    /** The satin that weaves the back of this one. */
    public static Matrix reverseSatin(Matrix repeat) {
        return repeat.invert();
    }
}
