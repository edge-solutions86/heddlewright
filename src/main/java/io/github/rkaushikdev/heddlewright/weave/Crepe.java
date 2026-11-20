package io.github.rkaushikdev.heddlewright.weave;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.util.Runs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Crepe.
 *
 * <p>A crepe has no line in it: no twill running across, no check, nothing the eye can follow. That
 * is harder to build than a pattern, because almost any rule regular enough to write down leaves a
 * regularity behind. The two devices that work are laying a satin over a ground, which scatters the
 * binding points, and rearranging the threads of a known weave by a satin order, which breaks the
 * line without changing the interlacement counts.
 */
public final class Crepe {

    /** How a satin crepe is built: the satin's size and move, and the ground it is laid over. */
    public record SatinOptions(int threads, OptionalInt move, Optional<Matrix> ground) {

        /** An eight thread satin on its first legal move, over a 2/2 twill. */
        public static final SatinOptions DEFAULT = new SatinOptions(8, OptionalInt.empty(), Optional.empty());

        /** The same crepe on a satin of another size. */
        public SatinOptions threads(int count) {
            return new SatinOptions(count, move, ground);
        }

        /** The same crepe on a given move. */
        public SatinOptions move(int step) {
            return new SatinOptions(threads, OptionalInt.of(step), ground);
        }

        /** The same crepe over another ground. */
        public SatinOptions ground(Matrix weave) {
            return new SatinOptions(threads, move, Optional.of(weave));
        }
    }

    /** The longest run of anything in each direction, and the longer of the two. */
    public record Line(int warp, int weft, int longest) {}

    /** Something that keeps a cloth from reading as a crepe, with the figure and its limit. */
    public record Finding(String code, double got, double limit) {}

    /** Whether a repeat reads as a crepe, and what was measured to decide it. */
    public record Reading(boolean reads, Line line, double diagonal, List<Finding> findings) {
        /** Copies the findings. */
        public Reading {
            findings = List.copyOf(findings);
        }
    }

    private Crepe() {}

    /** The eight thread satin crepe over a 2/2 twill. */
    public static Matrix satinCrepe() {
        return satinCrepe(SatinOptions.DEFAULT);
    }

    /**
     * Lay a satin over a twill ground, taking the cells where exactly one of them raises. Union
     * would give a cloth that is nearly all warp and intersection one that is nearly all weft; it is
     * the exclusive reading that scatters the binding points and leaves the cloth balanced.
     */
    public static Matrix satinCrepe(SatinOptions options) {
        List<Integer> moves = Satin.regularSatinMoves(options.threads());
        if (moves.isEmpty()) {
            throw new DraftException(
                    "no regular satin exists on that many threads", Json.obj("threads", options.threads()));
        }
        Matrix satin = Satin.satinWeave(options.threads(), options.move().orElse(moves.get(0)));
        Matrix base = options.ground()
                .orElseGet(() ->
                        Twill.twillWeave("2/2", Twill.Options.DEFAULT.size(options.threads(), options.threads())));
        return Combine.overlayWeaves(satin, base, Combine.Overlay.EITHER);
    }

    /** Rearrange the threads of a weave by its first satin order. */
    public static Matrix rearrangedCrepe(Matrix weave) {
        return rearrangedCrepe(weave, OptionalInt.empty());
    }

    /** Rearrange the threads of a weave by the satin order of a given move. */
    public static Matrix rearrangedCrepe(Matrix weave, int move) {
        return rearrangedCrepe(weave, OptionalInt.of(move));
    }

    /**
     * Rearrange the threads of a weave by a satin order. The interlacement of every thread is
     * untouched, so the cloth is as firm as it was; only the order they lie in changes, and the line
     * goes.
     */
    private static Matrix rearrangedCrepe(Matrix weave, OptionalInt move) {
        int rows = weave.rows();
        int cols = weave.cols();
        List<Integer> moves = Satin.regularSatinMoves(cols);
        if (moves.isEmpty()) {
            throw new DraftException("no satin order exists for a repeat that size", Json.obj("ends", cols));
        }
        int step = move.orElse(moves.get(0));
        int[][] out = new int[rows][cols];
        for (int p = 0; p < rows; p += 1) {
            for (int e = 0; e < cols; e += 1) {
                out[p][e] = weave.get(p, Numeric.mod(e * step, cols));
            }
        }
        return Matrix.of(out);
    }

    /**
     * The longest run of anything in one direction, which is the reading that says whether a cloth
     * has a line in it. A crepe should be low both ways.
     */
    public static Line longestLine(Matrix repeat) {
        int warp = 0;
        int weft = 0;
        for (List<Integer> column : repeat.columns()) {
            warp = Math.max(warp, Math.max(Runs.longestRun(column, 1), Runs.longestRun(column, 0)));
        }
        for (List<Integer> row : repeat.allRows()) {
            weft = Math.max(weft, Math.max(Runs.longestRun(row, 1), Runs.longestRun(row, 0)));
        }
        return new Line(warp, weft, Math.max(warp, weft));
    }

    /**
     * How much of a diagonal a repeat carries: the share of cells that continue the cell up and to
     * the right of them. A twill scores one, because every cell does; a scatter with no line in it
     * sits nearer a half, and anything above about four fifths still has a diagonal somebody will
     * see.
     */
    public static double diagonalScore(Matrix repeat) {
        int rows = repeat.rows();
        int cols = repeat.cols();
        int continued = 0;
        for (int p = 0; p < rows; p += 1) {
            for (int e = 0; e < cols; e += 1) {
                if (repeat.get(p, e) == repeat.get((p + 1) % rows, (e + 1) % cols)) {
                    continued += 1;
                }
            }
        }
        return (double) continued / (rows * cols);
    }

    /** Whether a repeat reads as a crepe, with no line past three threads and no diagonal past 0.8. */
    public static Reading readsAsCrepe(Matrix repeat) {
        return readsAsCrepe(repeat, 3, 0.8);
    }

    /**
     * A crepe reads as a crepe when no line runs far and no diagonal carries. The limits are what a
     * designer looks for rather than laws, so they come in.
     */
    public static Reading readsAsCrepe(Matrix repeat, int maxLine, double maxDiagonal) {
        Check.positiveInteger(maxLine, "maxLine");
        Line line = longestLine(repeat);
        double diagonal = diagonalScore(repeat);
        List<Finding> findings = new ArrayList<>();
        if (line.longest() > maxLine) {
            findings.add(new Finding("long_line", line.longest(), maxLine));
        }
        if (diagonal > maxDiagonal) {
            findings.add(new Finding("diagonal", diagonal, maxDiagonal));
        }
        return new Reading(findings.isEmpty(), line, diagonal, findings);
    }
}
