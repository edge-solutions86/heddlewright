package io.github.rkaushikdev.heddlewright.weave;

import io.github.rkaushikdev.heddlewright.draft.Shed;
import io.github.rkaushikdev.heddlewright.draft.Threading;
import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.ArrayList;
import java.util.List;

/**
 * Honeycomb, the waffle weave.
 *
 * <p>The cloth is a grid of square cells. Long floats ride the ridges between cells and the
 * interlacement tightens towards the middle of each well, which is what makes the cloth stand up
 * off the loom. The rule is geometric: an end is on the face while its distance from the ridge,
 * added to the pick's own distance from the ridge, still fits inside the cell.
 */
public final class Honeycomb {

    private Honeycomb() {}

    /** The repeat size of an order-n honeycomb, which is 2n - 2 threads each way. */
    public static int honeycombRepeat(int order) {
        Check.positiveInteger(order, "order");
        if (order < 3) {
            throw new DraftException("a honeycomb cell needs at least three shafts", Json.obj("order", order));
        }
        return 2 * order - 2;
    }

    /** Distance from the ridge, measured the short way round a repeat of {@code size}. */
    public static int ridgeDistance(int index, int size) {
        Check.integer(index, "index", Bounds.atLeast(0));
        Check.positiveInteger(size, "size");
        int at = index % size;
        return Math.min(at, size - at);
    }

    /** One repeat of an order-n honeycomb as a picks-by-ends matrix. */
    public static Matrix honeycombWeave(int order) {
        int size = honeycombRepeat(order);
        int limit = order - 2;
        int[][] repeat = new int[size][size];
        for (int p = 0; p < size; p += 1) {
            for (int e = 0; e < size; e += 1) {
                repeat[p][e] = ridgeDistance(e, size) + ridgeDistance(p, size) <= limit ? 1 : 0;
            }
        }
        return Matrix.of(repeat);
    }

    /**
     * The plain draft record a loom is set up from: a point threading, a point treadling and a
     * staircase tie-up where treadle t lifts the first n - 1 - t shafts.
     */
    public static JsonObject honeycombDraft(int order) {
        int size = honeycombRepeat(order);
        List<List<Integer>> tieup = new ArrayList<>(order);
        for (int t = 0; t < order; t += 1) {
            List<Integer> lifted = new ArrayList<>();
            for (int s = 0; s < order - 1 - t; s += 1) {
                lifted.add(s + 1);
            }
            tieup.add(lifted);
        }
        List<Integer> threading = Threading.pointDraw(order, size);
        List<List<Integer>> treadling = threading.stream().map(List::of).toList();
        return Json.obj(
                "threading", threading,
                "tieup", tieup,
                "treadling", treadling,
                "shafts", order,
                "treadles", order,
                "shed", Shed.RISING);
    }

    /** The longest warp float a honeycomb of this order carries on its ridge. */
    public static int ridgeFloat(int order) {
        return honeycombRepeat(order) - 1;
    }

    /**
     * How deep the well is, counted as the number of picks over which the interlacement tightens
     * from the ridge to the centre of the cell.
     */
    public static int cellDepth(int order) {
        Check.positiveInteger(order, "order");
        return order - 1;
    }

    /** A honeycomb is square: every repeat has as many picks as ends. */
    public static boolean isSquareCell(Matrix repeat) {
        return repeat.rows() == repeat.cols();
    }

    /**
     * Two honeycombs of the same order sitting side by side, offset by half a repeat, which staggers
     * the cells the way a brick course is staggered.
     */
    public static Matrix staggeredHoneycomb(int order) {
        Matrix base = honeycombWeave(order);
        int size = base.rows();
        int half = size / 2;
        int[][] out = new int[size][size * 2];
        for (int p = 0; p < size; p += 1) {
            for (int e = 0; e < size; e += 1) {
                out[p][e] = base.get(p, e);
                out[p][size + e] = base.get((p + half) % size, e);
            }
        }
        return Matrix.of(out);
    }
}
