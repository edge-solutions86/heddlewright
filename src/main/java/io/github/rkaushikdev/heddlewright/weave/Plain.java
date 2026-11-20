package io.github.rkaushikdev.heddlewright.weave;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain weave and the weaves that come straight out of it by grouping threads: basket, warp rib
 * and weft rib. Everything here returns one repeat as a picks-by-ends matrix with a set cell
 * meaning the warp end is on the face.
 */
public final class Plain {

    private Plain() {}

    /** The one over one interlacement, the tightest cloth two shafts can make. */
    public static Matrix plainWeave() {
        return Matrix.of(new int[][] {{1, 0}, {0, 1}});
    }

    /** The common two by two basket. */
    public static Matrix basketWeave() {
        return basketWeave(2, 2);
    }

    /** A square basket of so many threads a group. */
    public static Matrix basketWeave(int endsPerGroup) {
        return basketWeave(endsPerGroup, endsPerGroup);
    }

    /**
     * Basket, or hopsack: plain weave with {@code endsPerGroup} warp ends and {@code picksPerGroup}
     * weft picks working as one thread. A 2 by 2 basket is the common one; 2 by 1 is a half basket.
     */
    public static Matrix basketWeave(int endsPerGroup, int picksPerGroup) {
        Check.positiveInteger(endsPerGroup, "endsPerGroup");
        Check.positiveInteger(picksPerGroup, "picksPerGroup");
        int ends = endsPerGroup * 2;
        int picks = picksPerGroup * 2;
        int[][] repeat = new int[picks][ends];
        for (int p = 0; p < picks; p += 1) {
            for (int e = 0; e < ends; e += 1) {
                repeat[p][e] = (e / endsPerGroup) % 2 == (p / picksPerGroup) % 2 ? 1 : 0;
            }
        }
        return Matrix.of(repeat);
    }

    /** A warp rib holding each shed for two picks. */
    public static Matrix warpRib() {
        return warpRib(2);
    }

    /**
     * Warp rib: plain weave stretched down the cloth, so each shed is held for several picks and the
     * warp covers the weft. The cord runs weft-way.
     */
    public static Matrix warpRib(int picksPerShed) {
        Check.positiveInteger(picksPerShed, "picksPerShed");
        int picks = picksPerShed * 2;
        int[][] repeat = new int[picks][2];
        for (int p = 0; p < picks; p += 1) {
            boolean first = (p / picksPerShed) % 2 == 0;
            repeat[p][0] = first ? 1 : 0;
            repeat[p][1] = first ? 0 : 1;
        }
        return Matrix.of(repeat);
    }

    /** A weft rib working ends in pairs. */
    public static Matrix weftRib() {
        return weftRib(2);
    }

    /**
     * Weft rib, the ottoman: plain weave stretched across the cloth, so groups of ends work together
     * and the weft covers the warp. The cord runs warp-way.
     */
    public static Matrix weftRib(int endsPerShed) {
        Check.positiveInteger(endsPerShed, "endsPerShed");
        int ends = endsPerShed * 2;
        int[][] repeat = new int[2][ends];
        for (int e = 0; e < ends; e += 1) {
            boolean first = (e / endsPerShed) % 2 == 0;
            repeat[0][e] = first ? 1 : 0;
            repeat[1][e] = first ? 0 : 1;
        }
        return Matrix.of(repeat);
    }

    /**
     * An irregular rib, where the groups are not all the same size. The list gives the run of picks
     * for each shed in turn, alternating which shed is up.
     */
    public static Matrix irregularWarpRib(List<?> runs) {
        List<Object> list = Check.array(runs, "runs", 2);
        List<Integer> lengths = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i += 1) {
            lengths.add(Check.positiveInteger(list.get(i), "runs[" + i + "]"));
        }
        if (lengths.size() % 2 != 0) {
            throw new DraftException(
                    "an irregular rib needs an even number of runs to close its repeat",
                    Json.obj("runs", lengths.size()));
        }
        List<int[]> rows = new ArrayList<>();
        for (int i = 0; i < lengths.size(); i += 1) {
            for (int n = 0; n < lengths.get(i); n += 1) {
                rows.add(i % 2 == 0 ? new int[] {1, 0} : new int[] {0, 1});
            }
        }
        return Matrix.of(rows.toArray(int[][]::new));
    }

    /**
     * Plain weave threaded over more shafts than two, for a wider heddle spread: the tie-up lifts
     * the odd shafts on one treadle and the even on the other.
     */
    public static JsonObject plainOnShafts(int shafts) {
        Check.positiveInteger(shafts, "shafts");
        if (shafts % 2 != 0) {
            throw new DraftException(
                    "plain weave needs an even shaft count to divide evenly", Json.obj("shafts", shafts));
        }
        List<Integer> odd = new ArrayList<>();
        List<Integer> even = new ArrayList<>();
        for (int shaft = 1; shaft <= shafts; shaft += 1) {
            (shaft % 2 == 1 ? odd : even).add(shaft);
        }
        return Json.obj("tieup", List.of(odd, even), "treadling", List.of(List.of(1), List.of(2)), "shafts", shafts);
    }

    /** The float length of every one of these weaves is one thread. */
    public static boolean isPlainDerived(Matrix repeat) {
        if (repeat.rows() % 2 != 0 || repeat.cols() % 2 != 0) {
            return false;
        }
        for (List<Integer> row : repeat.allRows()) {
            if (!row.contains(1) || !row.contains(0)) {
                return false;
            }
        }
        return true;
    }
}
