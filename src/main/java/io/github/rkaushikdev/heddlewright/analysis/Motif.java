package io.github.rkaushikdev.heddlewright.analysis;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;

/**
 * Finding the repeat in a piece of cloth.
 *
 * <p>A drawdown taken off a loom is usually several repeats wide and several deep, and the useful
 * thing to know is the smallest block that would rebuild it. It is not enough to find a block that
 * matches the first repeat: the block has to rebuild every cell of the cloth, which is what this
 * checks.
 */
public record Motif(int picks, int ends, Matrix block) {

    /** How many times the motif covers the cloth, each way, and whether it does so whole. */
    public record Repeats(double down, double across, boolean whole) {}

    /** How much smaller the motif is than the cloth it came from. */
    public record Compression(int cells, int motifCells, double ratio) {}

    /** True when a block of this size, tiled, rebuilds the whole cloth. */
    public static boolean tilesCloth(Matrix cloth, int picks, int ends) {
        Check.positiveInteger(picks, "picks");
        Check.positiveInteger(ends, "ends");
        for (int p = 0; p < cloth.rows(); p += 1) {
            for (int e = 0; e < cloth.cols(); e += 1) {
                if (cloth.get(p, e) != cloth.get(p % picks, e % ends)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The smallest number of picks the cloth repeats on. */
    public static int pickRepeat(Matrix cloth) {
        for (int picks = 1; picks <= cloth.rows(); picks += 1) {
            if (tilesCloth(cloth, picks, cloth.cols())) {
                return picks;
            }
        }
        return cloth.rows();
    }

    /** The smallest number of ends the cloth repeats on. */
    public static int endRepeat(Matrix cloth) {
        for (int ends = 1; ends <= cloth.cols(); ends += 1) {
            if (tilesCloth(cloth, cloth.rows(), ends)) {
                return ends;
            }
        }
        return cloth.cols();
    }

    /** The smallest block that rebuilds the cloth, with its size. */
    public static Motif motif(Matrix cloth) {
        int picks = pickRepeat(cloth);
        int ends = endRepeat(cloth);
        int[][] block = new int[picks][ends];
        for (int p = 0; p < picks; p += 1) {
            for (int e = 0; e < ends; e += 1) {
                block[p][e] = cloth.get(p, e);
            }
        }
        return new Motif(picks, ends, Matrix.of(block));
    }

    /** How many times the motif covers the cloth, each way. */
    public static Repeats repeatsAcross(Matrix cloth) {
        Motif found = motif(cloth);
        return new Repeats(
                (double) cloth.rows() / found.picks(),
                (double) cloth.cols() / found.ends(),
                cloth.rows() % found.picks() == 0 && cloth.cols() % found.ends() == 0);
    }

    /**
     * The cloth cut down to one motif, which is what should be stored. Anything larger is the same
     * information written out several times.
     */
    public static Matrix reduceToMotif(Matrix cloth) {
        return motif(cloth).block();
    }

    /** How much smaller the motif is than the cloth it came from. */
    public static Compression compression(Matrix cloth) {
        Motif found = motif(cloth);
        int cells = cloth.rows() * cloth.cols();
        int motifCells = found.picks() * found.ends();
        return new Compression(cells, motifCells, (double) motifCells / cells);
    }

    /** Whether two cloths are built from the same motif. */
    public static boolean sameMotif(Matrix left, Matrix right) {
        return reduceToMotif(left).equals(reduceToMotif(right));
    }

    /**
     * Refuse a cloth whose motif does not divide it evenly. A drawdown that is nine ends wide on a
     * four end repeat is a drawdown somebody cut in the wrong place, and everything downstream will be
     * a quarter of a repeat out.
     */
    public static Repeats assertWholeRepeats(Matrix cloth) {
        Repeats found = repeatsAcross(cloth);
        if (!found.whole()) {
            throw new InvalidArgumentException(
                    "the cloth is not a whole number of repeats",
                    Json.obj("down", found.down(), "across", found.across()));
        }
        return found;
    }
}
