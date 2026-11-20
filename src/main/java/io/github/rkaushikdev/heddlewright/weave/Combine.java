package io.github.rkaushikdev.heddlewright.weave;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/**
 * Weave arithmetic.
 *
 * <p>New cloths are built by putting known ones together: two weaves laid over one another make a
 * crepe, alternate ends taken from two weaves make a double cloth face, and enlarging a weave thread
 * for thread makes it coarser without changing its structure. Every operation here takes and
 * returns a repeat.
 */
public final class Combine {

    /** How two weaves laid over one another decide a cell. */
    public enum Overlay {
        /** Raised when either weave raises it. */
        OVER,
        /** Raised only when both do. */
        UNDER,
        /** Raised when exactly one does, which is how a crepe is drawn from two plain grounds. */
        EITHER;

        /** The three modes, in the order the docs list them. */
        public static final List<Overlay> OVERLAY_MODES = List.of(values());

        /** Read a mode by its name, refusing anything else. */
        public static Overlay parse(Object text) {
            return Check.member(text, "mode", values());
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Two weaves grown to the smallest repeat that holds both whole. */
    public record Aligned(int picks, int ends, Matrix a, Matrix b) {}

    private Combine() {}

    /** Enlarge a weave two threads for one each way. */
    public static Matrix expandWeave(Matrix repeat) {
        return expandWeave(repeat, 2, 2);
    }

    /** Enlarge a weave by the same factor down and across. */
    public static Matrix expandWeave(Matrix repeat, int factor) {
        return expandWeave(repeat, factor, factor);
    }

    /** Enlarge a weave by repeating each pick and each end. */
    public static Matrix expandWeave(Matrix repeat, int downFactor, int acrossFactor) {
        Check.positiveInteger(downFactor, "downFactor");
        Check.positiveInteger(acrossFactor, "acrossFactor");
        int[][] out = new int[repeat.rows() * downFactor][repeat.cols() * acrossFactor];
        for (int p = 0; p < out.length; p += 1) {
            for (int e = 0; e < out[p].length; e += 1) {
                out[p][e] = repeat.get(p / downFactor, e / acrossFactor);
            }
        }
        return Matrix.of(out);
    }

    /** Grow two weaves to a common repeat, the smallest that holds both whole. */
    public static Aligned alignWeaves(Matrix a, Matrix b) {
        int picks = Numeric.lcm(a.rows(), b.rows());
        int ends = Numeric.lcm(a.cols(), b.cols());
        return new Aligned(picks, ends, stretchTo(a, picks, ends), stretchTo(b, picks, ends));
    }

    private static Matrix stretchTo(Matrix repeat, int picks, int ends) {
        int[][] out = new int[picks][ends];
        for (int p = 0; p < picks; p += 1) {
            for (int e = 0; e < ends; e += 1) {
                out[p][e] = repeat.get(p % repeat.rows(), e % repeat.cols());
            }
        }
        return Matrix.of(out);
    }

    /** Lay one weave over another, keeping a cell raised when either raises it. */
    public static Matrix overlayWeaves(Matrix a, Matrix b) {
        return overlayWeaves(a, b, Overlay.OVER);
    }

    /** Lay one weave over another in the given mode. */
    public static Matrix overlayWeaves(Matrix a, Matrix b, Overlay mode) {
        Check.required(mode, "mode");
        Aligned aligned = alignWeaves(a, b);
        int[][] out = new int[aligned.picks()][aligned.ends()];
        for (int p = 0; p < aligned.picks(); p += 1) {
            for (int e = 0; e < aligned.ends(); e += 1) {
                boolean x = aligned.a().isSet(p, e);
                boolean y = aligned.b().isSet(p, e);
                boolean raised =
                        switch (mode) {
                            case OVER -> x || y;
                            case UNDER -> x && y;
                            case EITHER -> x != y;
                        };
                out[p][e] = raised ? 1 : 0;
            }
        }
        return Matrix.of(out);
    }

    /** Take ends alternately from two weaves, one from each in turn. */
    public static Matrix interleaveEnds(Matrix a, Matrix b) {
        return interleaveEnds(a, b, List.of(0, 1));
    }

    /**
     * Take ends alternately from two weaves in the order given, which is how the face and back cloths
     * of a double weave share a warp. The order is a list of 0s and 1s naming which weave the next end
     * comes from.
     */
    public static Matrix interleaveEnds(Matrix a, Matrix b, List<?> order) {
        Aligned aligned = alignWeaves(a, b);
        List<Integer> choices = choices(order);
        int width = aligned.ends() * choices.size();
        int[][] out = new int[aligned.picks()][width];
        for (int p = 0; p < aligned.picks(); p += 1) {
            for (int e = 0; e < width; e += 1) {
                Matrix source = choices.get(e % choices.size()) == 0 ? aligned.a() : aligned.b();
                out[p][e] = source.get(p, (e / choices.size()) % aligned.ends());
            }
        }
        return Matrix.of(out);
    }

    private static List<Integer> choices(List<?> order) {
        List<Object> list = Check.array(order, "order", 1);
        List<Integer> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i += 1) {
            Check.oneOf(list.get(i), "order[" + i + "]", List.of(0, 1));
            out.add(Check.integer(list.get(i), "order[" + i + "]"));
        }
        return out;
    }

    /** Take picks alternately from two weaves, one from each in turn. */
    public static Matrix interleavePicks(Matrix a, Matrix b) {
        return interleavePicks(a, b, List.of(0, 1));
    }

    /** The same, taken pick by pick, which is how two cloths share a shuttle box. */
    public static Matrix interleavePicks(Matrix a, Matrix b, List<?> order) {
        Aligned aligned = alignWeaves(a, b);
        List<Integer> choices = choices(order);
        int height = aligned.picks() * choices.size();
        int[][] out = new int[height][aligned.ends()];
        for (int p = 0; p < height; p += 1) {
            Matrix source = choices.get(p % choices.size()) == 0 ? aligned.a() : aligned.b();
            int row = (p / choices.size()) % aligned.picks();
            for (int e = 0; e < aligned.ends(); e += 1) {
                out[p][e] = source.get(row, e);
            }
        }
        return Matrix.of(out);
    }

    /** Force down the cells a second weave names, which is how a backing cloth is tied to its face. */
    public static Matrix stitchWeave(Matrix repeat, Matrix stitches) {
        return stitchWeave(repeat, stitches, false);
    }

    /** Add stitching points to a weave, forcing the named cells down, or up when {@code raise} is set. */
    public static Matrix stitchWeave(Matrix repeat, Matrix stitches, boolean raise) {
        Aligned aligned = alignWeaves(repeat, stitches);
        int[][] out = new int[aligned.picks()][aligned.ends()];
        for (int p = 0; p < aligned.picks(); p += 1) {
            for (int e = 0; e < aligned.ends(); e += 1) {
                out[p][e] =
                        aligned.b().isSet(p, e) ? (raise ? 1 : 0) : aligned.a().get(p, e);
            }
        }
        return Matrix.of(out);
    }

    /** Cut a piece the size of the repeat, starting at its first pick and end. */
    public static Matrix cutWeave(Matrix repeat) {
        return cutWeave(repeat, 0, 0, OptionalInt.empty(), OptionalInt.empty());
    }

    /** Cut a piece of the given size from the first pick and end. */
    public static Matrix cutWeave(Matrix repeat, int picks, int ends) {
        return cutWeave(repeat, 0, 0, OptionalInt.of(picks), OptionalInt.of(ends));
    }

    /** Cut a piece out of a repeat, wrapping past its edges. */
    public static Matrix cutWeave(Matrix repeat, int fromPick, int fromEnd, OptionalInt picks, OptionalInt ends) {
        int rows = Check.positiveInteger(picks.orElse(repeat.rows()), "picks");
        int cols = Check.positiveInteger(ends.orElse(repeat.cols()), "ends");
        int[][] out = new int[rows][cols];
        for (int p = 0; p < rows; p += 1) {
            for (int e = 0; e < cols; e += 1) {
                out[p][e] = repeat.get((fromPick + p) % repeat.rows(), (fromEnd + e) % repeat.cols());
            }
        }
        return Matrix.of(out);
    }

    /** Refuse a weave that has no interlacement at all, in either direction. */
    public static Matrix assertInterlaces(Matrix repeat) {
        return assertInterlaces(repeat, "repeat");
    }

    /** Refuse a weave that has no interlacement at all, naming it in the complaint. */
    public static Matrix assertInterlaces(Matrix repeat, String label) {
        int ones = repeat.countOnes();
        if (ones == 0 || ones == repeat.rows() * repeat.cols()) {
            throw new DraftException(label + " does not interlace anywhere", Json.obj("label", label));
        }
        return repeat;
    }
}
