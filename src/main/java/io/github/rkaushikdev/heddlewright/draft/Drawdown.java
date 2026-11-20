package io.github.rkaushikdev.heddlewright.draft;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Indexing;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.ArrayList;
import java.util.List;

/**
 * The drawdown: one row per pick, one column per warp end, a set cell where the warp end lies over
 * the weft. It is the cloth, and every analysis in the package reads it rather than the draft that
 * produced it.
 */
public final class Drawdown {

    /** The number of picks and ends in a drawdown, named the way a weaver says them. */
    public record ClothSize(int picks, int ends) {}

    /** An end that never changes state through the repeat. */
    public record UnwovenEnd(int end, String state) {}

    /** A pick that never binds. */
    public record UnwovenPick(int pick, String state) {}

    private Drawdown() {}

    /** Compute a drawdown from a threading and a liftplan on a rising shed. */
    public static Matrix drawdownFromLiftplan(List<?> threading, Matrix liftplan) {
        return drawdownFromLiftplan(threading, liftplan, liftplan.cols(), Shed.RISING);
    }

    /** Compute a drawdown on a rising shed, against a stated shaft count. */
    public static Matrix drawdownFromLiftplan(List<?> threading, Matrix liftplan, int shafts) {
        return drawdownFromLiftplan(threading, liftplan, shafts, Shed.RISING);
    }

    /** Compute a drawdown on either shed, taking the shaft count from the liftplan. */
    public static Matrix drawdownFromLiftplan(List<?> threading, Matrix liftplan, Shed shed) {
        return drawdownFromLiftplan(threading, liftplan, liftplan.cols(), shed);
    }

    /** Compute a drawdown from a threading and a liftplan. */
    public static Matrix drawdownFromLiftplan(List<?> threading, Matrix liftplan, int shafts, Shed shed) {
        Check.positiveInteger(shafts, "shafts");
        List<Integer> ends = Threading.assertThreading(threading, shafts);
        Check.required(shed, "shed");
        int[][] cloth = new int[liftplan.rows()][ends.size()];
        for (int p = 0; p < liftplan.rows(); p += 1) {
            for (int e = 0; e < ends.size(); e += 1) {
                int shaft = ends.get(e) - 1;
                boolean lifted = shaft < liftplan.cols() && liftplan.isSet(p, shaft);
                cloth[p][e] = (shed == Shed.RISING ? lifted : !lifted) ? 1 : 0;
            }
        }
        return Matrix.of(cloth);
    }

    /** Compute a drawdown from a full treadle draft. */
    public static Matrix drawdownFromDraft(Draft draft) {
        Matrix plan =
                Liftplan.liftplanFromTreadling(draft.tieup(), draft.treadling(), draft.shafts(), draft.treadles());
        return drawdownFromLiftplan(draft.threading(), plan, draft.shafts(), draft.shed());
    }

    /**
     * Compute a drawdown from a plain draft record: a threading, a tie-up, a treadling, the shaft and
     * treadle counts and, optionally, the shed.
     */
    public static Matrix drawdownFromDraft(Object record) {
        if (record instanceof Draft draft) {
            return drawdownFromDraft(draft);
        }
        JsonObject draft = Check.plainObject(record, "draft");
        int shafts = Check.positiveInteger(draft.get("shafts"), "shafts");
        int treadles = Check.positiveInteger(draft.get("treadles"), "treadles");
        Shed shed = draft.get("shed").isMissing() ? Shed.RISING : Shed.parse(draft.get("shed"));
        Matrix plan = Liftplan.liftplanFromTreadling(draft.get("tieup"), draft.get("treadling"), shafts, treadles);
        return drawdownFromLiftplan(Check.array(draft.get("threading"), "threading"), plan, shafts, shed);
    }

    /** The picks and ends in a drawdown. */
    public static ClothSize clothSize(Matrix drawdown) {
        return new ClothSize(drawdown.rows(), drawdown.cols());
    }

    /** One warp end down the cloth, top to bottom, 1-based. */
    public static List<Integer> endColumn(Matrix drawdown, int end) {
        Check.positiveInteger(end, "end");
        if (end > drawdown.cols()) {
            throw new IndexOutOfBoundsException("end " + end + " is past the cloth width");
        }
        return drawdown.column(end - 1);
    }

    /** One pick across the cloth, left to right, 1-based. */
    public static List<Integer> pickRow(Matrix drawdown, int pick) {
        Check.positiveInteger(pick, "pick");
        if (pick > drawdown.rows()) {
            throw new IndexOutOfBoundsException("pick " + pick + " is past the cloth length");
        }
        return drawdown.row(pick - 1);
    }

    /**
     * The proportion of the face showing warp. A balanced weave sits at a half; a warp-faced satin
     * runs well above it.
     */
    public static double warpFaceRatio(Matrix drawdown) {
        return (double) drawdown.countOnes() / (drawdown.rows() * drawdown.cols());
    }

    /** Ends that never lift, or never sink, so their warp lies loose along the cloth. */
    public static List<UnwovenEnd> unwovenEnds(Matrix drawdown) {
        List<UnwovenEnd> out = new ArrayList<>();
        List<List<Integer>> columns = drawdown.columns();
        for (int index = 0; index < columns.size(); index += 1) {
            List<Integer> column = columns.get(index);
            if (column.stream().allMatch(cell -> cell.equals(column.get(0)))) {
                out.add(new UnwovenEnd(index + 1, column.get(0) == 1 ? "always up" : "always down"));
            }
        }
        return List.copyOf(out);
    }

    /** Picks that never bind, so the weft simply lies on the surface. */
    public static List<UnwovenPick> unwovenPicks(Matrix drawdown) {
        List<UnwovenPick> out = new ArrayList<>();
        for (int p = 0; p < drawdown.rows(); p += 1) {
            List<Integer> row = drawdown.row(p);
            if (row.stream().allMatch(cell -> cell == 1) || row.stream().allMatch(cell -> cell == 0)) {
                out.add(new UnwovenPick(p + 1, row.get(0) == 1 ? "all warp" : "all weft"));
            }
        }
        return List.copyOf(out);
    }

    /** Groups of ends that lift identically on every pick, so they weave as one thread. */
    public static List<List<Integer>> pairedEnds(Matrix drawdown) {
        return Indexing.duplicateGroups(drawdown.columns(), (List<Integer> column) -> column.toString());
    }

    /** The back of the cloth, which is the face with every cell swapped. */
    public static Matrix reverseFace(Matrix drawdown) {
        return drawdown.invert();
    }

    /** Read a drawdown out of rows that are strings of X and dot, or already lists of bits. */
    public static Matrix drawdownFromRows(List<?> rows) {
        List<Object> list = Check.array(rows, "rows", 1);
        List<Object> bits = new ArrayList<>(list.size());
        for (Object row : list) {
            Object text = row instanceof Json.Str json ? json.value() : row;
            if (text instanceof String line) {
                List<Integer> cells = new ArrayList<>(line.length());
                for (int i = 0; i < line.length(); i += 1) {
                    cells.add(line.charAt(i) == 'X' ? 1 : 0);
                }
                bits.add(cells);
            } else {
                bits.add(row);
            }
        }
        return Matrix.of(bits, "drawdown");
    }
}
