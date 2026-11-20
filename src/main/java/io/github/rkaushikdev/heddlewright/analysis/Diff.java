package io.github.rkaushikdev.heddlewright.analysis;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Comparing two cloths.
 *
 * <p>A designer changes one thing in a draft and wants to know what it did. The useful answer is
 * not "they differ": it is which ends and which picks moved, and by how much, because a change that
 * shows on two ends is a correction and a change that shows on every end is a different cloth.
 */
public final class Diff {

    /** How many cells differ, of how many, and what share of the cloth that is. */
    public record Difference(int cells, int total, double share) {}

    /** An end that changed, and how many of its cells did. */
    public record EndChange(int end, int cells) {}

    /** A pick that changed, and how many of its cells did. */
    public record PickChange(int pick, int cells) {}

    /** A reading a designer can act on: how much changed, whether it is local, and where it starts. */
    public record Change(
            int cells,
            int total,
            double share,
            String kind,
            int endsChanged,
            int picksChanged,
            OptionalInt firstEnd,
            OptionalInt firstPick)
            implements Json.Writable {

        /** Written with a null first thread where nothing changed, as the report prints it. */
        @Override
        public Json toJson() {
            return Json.obj(
                    "cells", cells,
                    "total", total,
                    "share", share,
                    "kind", kind,
                    "endsChanged", endsChanged,
                    "picksChanged", picksChanged,
                    "firstEnd", firstEnd.isPresent() ? firstEnd.getAsInt() : Json.NULL,
                    "firstPick", firstPick.isPresent() ? firstPick.getAsInt() : Json.NULL);
        }
    }

    private Diff() {}

    /** Refuse two cloths that cannot be compared cell for cell. */
    public static Matrix.Dimensions assertSameShape(Matrix left, Matrix right) {
        Matrix.Dimensions a = left.dimensions();
        Matrix.Dimensions b = right.dimensions();
        if (!a.equals(b)) {
            throw new InvalidArgumentException("those two cloths are different sizes", Json.obj("left", a, "right", b));
        }
        return a;
    }

    /** A matrix with a set cell wherever the two cloths disagree. */
    public static Matrix differenceMask(Matrix left, Matrix right) {
        Matrix.Dimensions size = assertSameShape(left, right);
        int[][] mask = new int[size.rows()][size.cols()];
        for (int p = 0; p < size.rows(); p += 1) {
            for (int e = 0; e < size.cols(); e += 1) {
                mask[p][e] = left.get(p, e) == right.get(p, e) ? 0 : 1;
            }
        }
        return Matrix.of(mask);
    }

    /** How many cells differ, and what share of the cloth that is. */
    public static Difference differenceCount(Matrix left, Matrix right) {
        Matrix.Dimensions size = assertSameShape(left, right);
        int cells = differenceMask(left, right).countOnes();
        int total = size.rows() * size.cols();
        return new Difference(cells, total, Numeric.roundTo((double) cells / total, 4));
    }

    /** The 1-based ends that changed, and how many cells changed on each. */
    public static List<EndChange> endsChanged(Matrix left, Matrix right) {
        Matrix mask = differenceMask(left, right);
        List<EndChange> out = new ArrayList<>();
        for (int e = 0; e < mask.cols(); e += 1) {
            int cells = mask.column(e).stream().mapToInt(Integer::intValue).sum();
            if (cells > 0) {
                out.add(new EndChange(e + 1, cells));
            }
        }
        return List.copyOf(out);
    }

    /** The 1-based picks that changed, and how many cells changed on each. */
    public static List<PickChange> picksChanged(Matrix left, Matrix right) {
        Matrix mask = differenceMask(left, right);
        List<PickChange> out = new ArrayList<>();
        for (int p = 0; p < mask.rows(); p += 1) {
            int cells = mask.row(p).stream().mapToInt(Integer::intValue).sum();
            if (cells > 0) {
                out.add(new PickChange(p + 1, cells));
            }
        }
        return List.copyOf(out);
    }

    /** True when the two cloths are the same everywhere. */
    public static boolean sameCloth(Matrix left, Matrix right) {
        return differenceCount(left, right).cells() == 0;
    }

    /** Describe a change, calling it local when it touches a tenth of the cloth or less. */
    public static Change describeChange(Matrix left, Matrix right) {
        return describeChange(left, right, 0.1);
    }

    /**
     * A reading a designer can act on: whether the change is local, meaning it touched a handful of
     * threads, or wholesale.
     */
    public static Change describeChange(Matrix left, Matrix right, double localShare) {
        Check.number(localShare, "localShare", Bounds.between(0, 1));
        Difference count = differenceCount(left, right);
        List<EndChange> ends = endsChanged(left, right);
        List<PickChange> picks = picksChanged(left, right);
        String kind = "none";
        if (count.cells() > 0) {
            kind = count.share() <= localShare ? "local" : "wholesale";
        }
        return new Change(
                count.cells(),
                count.total(),
                count.share(),
                kind,
                ends.size(),
                picks.size(),
                ends.isEmpty()
                        ? OptionalInt.empty()
                        : OptionalInt.of(ends.get(0).end()),
                picks.isEmpty()
                        ? OptionalInt.empty()
                        : OptionalInt.of(picks.get(0).pick()));
    }

    /** The two cloths side by side, three spaces apart. */
    public static String renderSideBySide(Matrix left, Matrix right) {
        return renderSideBySide(left, right, "   ");
    }

    /** The two cloths side by side, for a report that has to show its working. */
    public static String renderSideBySide(Matrix left, Matrix right, String gap) {
        Matrix.Dimensions size = assertSameShape(left, right);
        List<String> lines = new ArrayList<>(size.rows());
        for (int p = 0; p < size.rows(); p += 1) {
            StringBuilder a = new StringBuilder();
            StringBuilder b = new StringBuilder();
            StringBuilder marks = new StringBuilder();
            for (int e = 0; e < size.cols(); e += 1) {
                a.append(left.isSet(p, e) ? 'X' : '.');
                b.append(right.isSet(p, e) ? 'X' : '.');
                marks.append(left.get(p, e) == right.get(p, e) ? ' ' : '!');
            }
            lines.add(a + gap + b + gap + marks);
        }
        return String.join("\n", lines);
    }
}
