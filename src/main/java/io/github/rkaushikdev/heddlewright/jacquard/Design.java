package io.github.rkaushikdev.heddlewright.jacquard;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/**
 * Sizing a figured design for a machine.
 *
 * <p>A designer draws a figure at whatever size looks right and then somebody has to fit it to the
 * hooks and the picks. The figure is scaled, placed and repeated, and the awkward part is that
 * scaling a binary design is not resampling a photograph: every cell has to end up as a whole cell,
 * so the scale factors are whole numbers or the figure comes out ragged.
 */
public final class Design {

    /** Where a figure sits across the hooks. */
    public enum Placement {
        /** Against hook one. */
        LEFT,
        /** In the middle, any odd hook on the right. */
        CENTRE,
        /** Against the last hook. */
        RIGHT;

        /** Read a placement by its name, refusing anything else. */
        public static Placement parse(Object text) {
            return Check.member(text, "placement", values());
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The placements, left first. */
    public static final List<Placement> PLACEMENTS = List.of(Placement.values());

    /** A placed figure: the plan across every hook, and the hooks spare on each side. */
    public record Placed(Matrix plan, int left, int right) {}

    /** A figure fitted to a machine. */
    public record Fitted(
            int scale, int designEnds, int designPicks, int spareHooks, int left, int right, Matrix plan) {}

    /** How many times the figure repeats down a length, what is left over, and whether that is nothing. */
    public record InLength(int repeats, int remainder, boolean whole) {}

    private Design() {}

    /** The design at a scale of one. */
    public static Matrix scaleDesign(Matrix design) {
        return scaleDesign(design, 1, 1);
    }

    /** Enlarge a design by the same whole factor both ways. */
    public static Matrix scaleDesign(Matrix design, double across) {
        return scaleDesign(design, across, across);
    }

    /**
     * Enlarge a design by whole factors, which is the only honest way to scale one. The factors are
     * taken as numbers and checked, so a fractional one is refused rather than truncated.
     */
    public static Matrix scaleDesign(Matrix design, double across, double down) {
        Check.required(design, "design");
        int wide = Check.positiveInteger((Object) across, "across");
        int deep = Check.positiveInteger((Object) down, "down");
        int[][] out = new int[design.rows() * deep][design.cols() * wide];
        for (int r = 0; r < design.rows(); r += 1) {
            for (int c = 0; c < design.cols(); c += 1) {
                for (int d = 0; d < deep; d += 1) {
                    for (int a = 0; a < wide; a += 1) {
                        out[r * deep + d][c * wide + a] = design.get(r, c);
                    }
                }
            }
        }
        return Matrix.of(out);
    }

    /** The largest whole scale a design can take inside a hook count. */
    public static int largestScale(Matrix design, int hooks) {
        Check.required(design, "design");
        Check.positiveInteger(hooks, "hooks");
        int width = design.cols();
        int scale = hooks / width;
        if (scale < 1) {
            throw new CapacityException(
                    "the design is wider than the machine even unscaled",
                    Json.obj("designEnds", width, "hooks", hooks));
        }
        return scale;
    }

    /** Centre a design across a hook count. */
    public static Placed placeDesign(Matrix design, int hooks) {
        return placeDesign(design, hooks, Placement.CENTRE);
    }

    /** Place a design across a hook count, padding either side. */
    public static Placed placeDesign(Matrix design, int hooks, Placement placement) {
        Check.required(design, "design");
        Check.positiveInteger(hooks, "hooks");
        Check.required(placement, "placement");
        int rows = design.rows();
        int cols = design.cols();
        if (cols > hooks) {
            throw new CapacityException(
                    "the design is wider than the machine", Json.obj("designEnds", cols, "hooks", hooks));
        }
        int spare = hooks - cols;
        int left =
                switch (placement) {
                    case LEFT -> 0;
                    case RIGHT -> spare;
                    case CENTRE -> spare / 2;
                };
        int[][] out = new int[rows][hooks];
        for (int p = 0; p < rows; p += 1) {
            for (int e = 0; e < cols; e += 1) {
                out[p][left + e] = design.get(p, e);
            }
        }
        return new Placed(Matrix.of(out), left, spare - left);
    }

    /** Repeat a design down the cloth to reach a pick count. */
    public static Matrix repeatDown(Matrix design, int picks) {
        Check.required(design, "design");
        Check.positiveInteger(picks, "picks");
        int[][] out = new int[picks][];
        for (int p = 0; p < picks; p += 1) {
            out[p] = design.row(p % design.rows()).stream()
                    .mapToInt(Integer::intValue)
                    .toArray();
        }
        return Matrix.of(out);
    }

    /** Fit a figure as large as the hooks allow, centred, at its own height. */
    public static Fitted fitDesign(Matrix design, int hooks) {
        return fitDesign(design, hooks, OptionalInt.empty(), Placement.CENTRE, OptionalInt.empty());
    }

    /**
     * The whole fitting: scale the figure as large as the hooks allow, or to the scale asked for,
     * place it, repeat it down to a pick count if one is given, and report what was left over. A
     * design that fits exactly is rare, and the spare hooks are what a border is woven on.
     */
    public static Fitted fitDesign(
            Matrix design, int hooks, OptionalInt picks, Placement placement, OptionalInt scale) {
        int wanted = scale.isPresent() ? scale.getAsInt() : largestScale(design, hooks);
        Check.positiveInteger(wanted, "scale");
        Matrix scaled = scaleDesign(design, wanted, wanted);
        Placed placed = placeDesign(scaled, hooks, placement);
        Matrix plan = picks.isPresent() ? repeatDown(placed.plan(), picks.getAsInt()) : placed.plan();
        return new Fitted(
                wanted,
                scaled.cols(),
                scaled.rows(),
                placed.left() + placed.right(),
                placed.left(),
                placed.right(),
                plan);
    }

    /** How much of the machine a fitted design actually uses. */
    public static double hookUtilisation(Fitted fitted, int hooks) {
        Check.positiveInteger(hooks, "hooks");
        return Numeric.roundTo((double) fitted.designEnds() / hooks, 4);
    }

    /**
     * How many times the figure repeats down a length of cloth, and whether the length holds a whole
     * number of them.
     */
    public static InLength repeatsInLength(Fitted fitted, int picks) {
        Check.integer(picks, "picks", Bounds.atLeast(1));
        return new InLength(
                picks / fitted.designPicks(), picks % fitted.designPicks(), picks % fitted.designPicks() == 0);
    }

    /** Refuse a fitting that leaves the figure off centre when it was meant to be centred. */
    public static Fitted assertCentred(Fitted fitted) {
        if (Math.abs(fitted.left() - fitted.right()) > 1) {
            throw new DraftException(
                    "the figure is not centred on the machine",
                    Json.obj("left", fitted.left(), "right", fitted.right()));
        }
        return fitted;
    }
}
