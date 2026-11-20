package io.github.rkaushikdev.heddlewright.draft;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Indexing;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;

/**
 * A liftplan is one row per pick and one column per shaft, which is what a dobby actually reads.
 *
 * <p>A treadle loom draft carries a tie-up and a treadling instead, and the two forms convert both
 * ways: forwards always, backwards only when the number of distinct lifts fits the treadles
 * available.
 */
public final class Liftplan {

    /** A treadle draft split out of a liftplan: one treadle per distinct lift. */
    public record TreadleDraft(List<List<Integer>> tieup, List<List<Integer>> treadling, int treadles) {
        /** Copies the lists. */
        public TreadleDraft {
            tieup = List.copyOf(tieup);
            treadling = List.copyOf(treadling);
        }
    }

    /** A pick on which no shed opens, and why. */
    public record ShedlessPick(int pick, String reason) {}

    private Liftplan() {}

    /** Build the liftplan a tie-up and treadling produce on a rising shed. */
    public static Matrix liftplanFromTreadling(Object tieup, Object treadling, int shafts, int treadles) {
        Check.positiveInteger(shafts, "shafts");
        Check.positiveInteger(treadles, "treadles");
        List<List<Integer>> ties = Tieup.normaliseTieup(tieup, shafts);
        if (ties.size() != treadles) {
            throw new CapacityException(
                    "the tie-up does not cover every treadle", Json.obj("treadles", treadles, "tied", ties.size()));
        }
        List<List<Integer>> picks = Treadling.normaliseTreadling(treadling, treadles);
        int[][] plan = new int[picks.size()][shafts];
        for (int p = 0; p < picks.size(); p += 1) {
            for (int treadle : picks.get(p)) {
                for (int shaft : ties.get(treadle - 1)) {
                    plan[p][shaft - 1] = 1;
                }
            }
        }
        return Matrix.of(plan);
    }

    /** Split a liftplan into a tie-up and a treadling, as many treadles as it needs. */
    public static TreadleDraft treadlingFromLiftplan(Matrix liftplan) {
        return treadlingFromLiftplan(liftplan, OptionalInt.empty());
    }

    /** Split a liftplan into a tie-up and a treadling for a loom with this many treadles. */
    public static TreadleDraft treadlingFromLiftplan(Matrix liftplan, int treadles) {
        return treadlingFromLiftplan(liftplan, OptionalInt.of(treadles));
    }

    /**
     * Split a liftplan back into a tie-up and a treadling. Each distinct row becomes one treadle, in
     * the order the rows are first seen, so a plan using more distinct lifts than the loom has
     * treadles is refused rather than silently merged.
     */
    private static TreadleDraft treadlingFromLiftplan(Matrix liftplan, OptionalInt treadles) {
        Indexing.Labelled<List<Integer>> labelled = Indexing.labelDistinct(liftplan.allRows(), Liftplan::rowKey);
        if (treadles.isPresent() && labelled.count() > treadles.getAsInt()) {
            throw new CapacityException(
                    "the liftplan needs more treadles than the loom has",
                    Json.obj("needed", labelled.count(), "treadles", treadles.getAsInt()));
        }
        return new TreadleDraft(
                labelled.representatives().stream().map(Indexing::setPositions).toList(),
                labelled.labels().stream().map(List::of).toList(),
                labelled.count());
    }

    private static String rowKey(List<Integer> row) {
        StringBuilder key = new StringBuilder(row.size());
        for (int cell : row) {
            key.append(cell);
        }
        return key.toString();
    }

    /** A liftplan straight from a shaft-per-pick list, for a direct-tie dobby. */
    public static Matrix liftplanFromShaftLists(Object shaftLists, int shafts) {
        List<Object> lists = Check.array(shaftLists, "shaftLists", 1);
        Check.positiveInteger(shafts, "shafts");
        int[][] plan = new int[lists.size()][shafts];
        for (int p = 0; p < lists.size(); p += 1) {
            Object list = lists.get(p);
            List<Object> entries = list instanceof List<?> || list instanceof JsonArray
                    ? Check.array(list, "shaftLists[" + p + "]")
                    : java.util.Collections.singletonList(list);
            for (int i = 0; i < entries.size(); i += 1) {
                int shaft =
                        Check.integer(entries.get(i), "shaftLists[" + p + "][" + i + "]", Bounds.between(1, shafts));
                plan[p][shaft - 1] = 1;
            }
        }
        return Matrix.of(plan);
    }

    /** The shafts lifted on each pick, as 1-based lists. */
    public static List<List<Integer>> shaftListsFromLiftplan(Matrix liftplan) {
        return liftplan.allRows().stream().map(Indexing::setPositions).toList();
    }

    /** How many shafts each pick lifts, which sizes the dobby cylinder load. */
    public static List<Integer> liftLoad(Matrix liftplan) {
        List<Integer> out = new ArrayList<>(liftplan.rows());
        for (List<Integer> row : liftplan.allRows()) {
            out.add(row.stream().mapToInt(Integer::intValue).sum());
        }
        return List.copyOf(out);
    }

    /** Picks that lift nothing, or lift every shaft, so no shed opens. */
    public static List<ShedlessPick> shedlessPicks(Matrix liftplan) {
        List<ShedlessPick> out = new ArrayList<>();
        List<Integer> loads = liftLoad(liftplan);
        for (int p = 0; p < loads.size(); p += 1) {
            int load = loads.get(p);
            if (load == 0) {
                out.add(new ShedlessPick(p + 1, "nothing lifted"));
            } else if (load == liftplan.cols()) {
                out.add(new ShedlessPick(p + 1, "everything lifted"));
            }
        }
        return List.copyOf(out);
    }

    /** The distinct lifts a plan uses, which is the treadle count it needs. */
    public static int distinctLifts(Matrix liftplan) {
        return new HashSet<>(liftplan.allRows()).size();
    }

    /** Invert every lift, which is the same cloth woven face down. */
    public static Matrix invertLiftplan(Matrix liftplan) {
        return liftplan.invert();
    }
}
