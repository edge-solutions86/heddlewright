package io.github.rkaushikdev.heddlewright.weave;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * Twills.
 *
 * <p>A twill is written as an alternating count of warp-up and warp-down threads, {@code 2/2} or
 * {@code 3/1} or {@code 2/1/1/2}, and the numbers add up to the repeat size. The drawdown is drawn
 * with the first pick at the bottom, so a Z twill, the right-hand one, runs up to the right.
 */
public final class Twill {

    /** Which way the twill line runs. */
    public enum Hand {
        /** Up to the right. */
        Z,
        /** Up to the left. */
        S;

        /** The two hands, Z first. */
        public static final List<Hand> HANDEDNESS = List.of(values());

        /** Read a hand by its letter, refusing anything else. */
        public static Hand parse(Object text) {
            return Check.member(text, "handedness", values());
        }
    }

    /** How a continuous twill is drawn: its hand, its step, and the size to draw it at. */
    public record Options(Hand handedness, int step, OptionalInt picks, OptionalInt ends) {

        /** A Z twill stepping one end a pick, one repeat square. */
        public static final Options DEFAULT = new Options(Hand.Z, 1, OptionalInt.empty(), OptionalInt.empty());

        /** The same options with another hand. */
        public Options handedness(Hand hand) {
            return new Options(hand, step, picks, ends);
        }

        /** The same options with another step. */
        public Options step(int count) {
            return new Options(handedness, count, picks, ends);
        }

        /** The same options drawn at a given size. */
        public Options size(int pickCount, int endCount) {
            return new Options(handedness, step, OptionalInt.of(pickCount), OptionalInt.of(endCount));
        }

        /** Read options from a plain record, checking each field as it is read. */
        public static Options fromRecord(Object record) {
            JsonObject options = Check.plainObject(record, "options");
            Json hand = options.get("handedness");
            Json step = options.get("step");
            return new Options(
                    hand.isNullish() ? Hand.Z : Hand.parse(hand),
                    step.isNullish() ? 1 : Check.integer(step, "step"),
                    whole(options.get("picks"), "picks"),
                    whole(options.get("ends"), "ends"));
        }

        private static OptionalInt whole(Json value, String field) {
            return value.isNullish() ? OptionalInt.empty() : OptionalInt.of(Check.integer(value, field));
        }
    }

    /** How many of the repeat's threads are warp on the face, and how many weft. */
    public record Balance(int warp, int weft, int repeat) {}

    private Twill() {}

    /** Read {@code 2/2} or {@code 2 1 1 2} into a list of counts. */
    public static List<Integer> parseTwillNotation(String notation) {
        Check.nonEmptyString(notation, "notation");
        List<String> parts = new ArrayList<>();
        for (String part : Text.trim(notation).split("(?:/|" + Text.SPACE_CLASS + ")+", -1)) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        if (parts.size() < 2) {
            throw new DraftException(
                    "a twill needs at least one up count and one down count", Json.obj("notation", notation));
        }
        List<Integer> counts = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i += 1) {
            String part = parts.get(i);
            if (!part.matches("\\d+")) {
                throw new DraftException(
                        "twill notation part \"" + part + "\" is not a count",
                        Json.obj("notation", notation, "part", part));
            }
            counts.add(Check.integer(Integer.parseInt(part), "notation[" + i + "]", Bounds.atLeast(1)));
        }
        if (counts.size() % 2 != 0) {
            throw new DraftException(
                    "twill notation must alternate up and down, so it needs an even count",
                    Json.obj("notation", notation, "parts", counts.size()));
        }
        return List.copyOf(counts);
    }

    /** Write a count list back as slash notation. */
    public static String formatTwillNotation(List<Integer> counts) {
        Check.array(counts, "counts", 2);
        return counts.stream().map(String::valueOf).collect(Collectors.joining("/"));
    }

    /** The repeat size of a twill is the sum of its counts. */
    public static int twillRepeatSize(List<Integer> counts) {
        return (int) Numeric.sum(counts);
    }

    /** The face pattern for one end of the repeat, starting with the warp up. */
    public static List<Integer> twillPattern(List<Integer> counts) {
        Check.array(counts, "counts", 2);
        List<Integer> pattern = new ArrayList<>();
        for (int i = 0; i < counts.size(); i += 1) {
            int count = Check.integer(counts.get(i), "counts[" + i + "]", Bounds.atLeast(1));
            for (int n = 0; n < count; n += 1) {
                pattern.add(i % 2 == 0 ? 1 : 0);
            }
        }
        return List.copyOf(pattern);
    }

    /** One repeat of a Z twill written in notation. */
    public static Matrix twillWeave(String notation) {
        return twillWeave(parseTwillNotation(notation), Options.DEFAULT);
    }

    /** A twill written in notation, drawn with the given options. */
    public static Matrix twillWeave(String notation, Options options) {
        return twillWeave(parseTwillNotation(notation), options);
    }

    /** One repeat of a Z twill from its counts. */
    public static Matrix twillWeave(List<Integer> counts) {
        return twillWeave(counts, Options.DEFAULT);
    }

    /** One repeat of a continuous twill as a picks-by-ends matrix. */
    public static Matrix twillWeave(List<Integer> counts, Options options) {
        Check.required(options.handedness(), "handedness");
        Check.integer(options.step(), "step", Bounds.atLeast(1));
        List<Integer> pattern = twillPattern(counts);
        int size = pattern.size();
        int rows = Check.integer(options.picks().orElse(size), "picks", Bounds.atLeast(1));
        int cols = Check.integer(options.ends().orElse(size), "ends", Bounds.atLeast(1));
        int[][] repeat = new int[rows][cols];
        for (int p = 0; p < rows; p += 1) {
            for (int e = 0; e < cols; e += 1) {
                int shift = options.handedness() == Hand.Z ? e - p * options.step() : e + p * options.step();
                repeat[p][e] = pattern.get(Numeric.mod(shift, size));
            }
        }
        return Matrix.of(repeat);
    }

    /** A Z broken twill breaking at half the repeat. */
    public static Matrix brokenTwill(String notation) {
        List<Integer> counts = parseTwillNotation(notation);
        return brokenTwill(counts, twillRepeatSize(counts) / 2, Hand.Z);
    }

    /** A Z broken twill breaking every {@code breakAt} ends. */
    public static Matrix brokenTwill(String notation, int breakAt) {
        return brokenTwill(parseTwillNotation(notation), breakAt, Hand.Z);
    }

    /**
     * A broken twill: the drafting order runs straight for {@code breakAt} ends, then back down for
     * the next {@code breakAt}, so the diagonal never crosses the whole repeat. It is a
     * rearrangement of the ends, not a change of interlacement, so every pick keeps the up and down
     * counts the notation asked for.
     */
    public static Matrix brokenTwill(List<Integer> counts, int breakAt, Hand handedness) {
        List<Integer> pattern = twillPattern(counts);
        int size = pattern.size();
        Check.integer(breakAt, "breakAt", Bounds.atLeast(1));
        Check.required(handedness, "handedness");
        List<Integer> order = brokenDraftingOrder(size, breakAt);
        int[][] repeat = new int[size][size];
        for (int p = 0; p < size; p += 1) {
            for (int e = 0; e < order.size(); e += 1) {
                int source = order.get(e);
                int shift = handedness == Hand.Z ? source - p : source + p;
                repeat[p][e] = pattern.get(Numeric.mod(shift, size));
            }
        }
        return Matrix.of(repeat);
    }

    /**
     * The end order a broken twill is drafted in: every second block of {@code width} ends runs
     * backwards. It is always a permutation of 0..size-1, which is what keeps the interlacement
     * counts intact.
     */
    public static List<Integer> brokenDraftingOrder(int size, int width) {
        Check.integer(size, "size", Bounds.atLeast(1));
        Check.integer(width, "width", Bounds.atLeast(1));
        List<Integer> order = new ArrayList<>(size);
        for (int e = 0; e < size; e += 1) {
            int block = e / width;
            int within = e % width;
            int last = Math.min(width, size - block * width) - 1;
            order.add(block * width + (block % 2 == 0 ? within : last - within));
        }
        return List.copyOf(order);
    }

    /** A pointed twill turning at the repeat size, one repeat long. */
    public static Matrix pointedTwill(String notation) {
        List<Integer> counts = parseTwillNotation(notation);
        int size = twillRepeatSize(counts);
        return pointedTwill(counts, size, size);
    }

    /** A pointed twill turning after {@code width} ends, one repeat long. */
    public static Matrix pointedTwill(String notation, int width) {
        List<Integer> counts = parseTwillNotation(notation);
        return pointedTwill(counts, width, twillRepeatSize(counts));
    }

    /**
     * A pointed twill turns the line back on itself, giving herringbone across the cloth.
     * {@code width} is how many ends run before the turn.
     */
    public static Matrix pointedTwill(List<Integer> counts, int width, int picks) {
        List<Integer> pattern = twillPattern(counts);
        int size = pattern.size();
        Check.integer(width, "width", Bounds.atLeast(2));
        int cols = width * 2 - 2;
        int[][] repeat = new int[picks][cols];
        for (int p = 0; p < picks; p += 1) {
            for (int e = 0; e < cols; e += 1) {
                int folded = e < width ? e : cols - e;
                repeat[p][e] = pattern.get(Numeric.mod(folded - p, size));
            }
        }
        return Matrix.of(repeat);
    }

    /** The angle of a twill stepping one end a pick, in degrees. */
    public static double twillAngle(double endsPerInch, double picksPerInch) {
        return twillAngle(1, endsPerInch, picksPerInch);
    }

    /**
     * The angle the twill line makes with the weft, in degrees. A step of one on a square sett gives
     * the familiar forty-five; a bigger step reclines it.
     */
    public static double twillAngle(int step, double endsPerInch, double picksPerInch) {
        Check.integer(step, "step", Bounds.atLeast(1));
        Check.positiveNumber(endsPerInch, "endsPerInch");
        Check.positiveNumber(picksPerInch, "picksPerInch");
        double radians = StrictMath.atan(endsPerInch / (step * picksPerInch));
        return Numeric.roundTo((radians * 180) / Math.PI, 2);
    }

    /** The warp and weft balance of a twill written in notation. */
    public static Balance twillBalance(String notation) {
        return twillBalance(parseTwillNotation(notation));
    }

    /** How many of the repeat's threads are warp on the face, and how many weft. */
    public static Balance twillBalance(List<Integer> counts) {
        int warp = 0;
        int weft = 0;
        for (int i = 0; i < counts.size(); i += 1) {
            if (i % 2 == 0) {
                warp += counts.get(i);
            } else {
                weft += counts.get(i);
            }
        }
        return new Balance(warp, weft, warp + weft);
    }

    /** True when a twill written in notation shows more warp than weft on the face. */
    public static boolean isWarpFaced(String notation) {
        return isWarpFaced(parseTwillNotation(notation));
    }

    /** True when a twill shows more warp than weft on the face. */
    public static boolean isWarpFaced(List<Integer> counts) {
        Balance balance = twillBalance(counts);
        return balance.warp() > balance.weft();
    }

    /** The twill that weaves the back of one written in notation. */
    public static List<Integer> reverseTwill(String notation) {
        return reverseTwill(parseTwillNotation(notation));
    }

    /** The twill that weaves the back of this one, by swapping every count pair. */
    public static List<Integer> reverseTwill(List<Integer> counts) {
        List<Integer> out = new ArrayList<>(counts.size());
        for (int i = 0; i < counts.size(); i += 2) {
            out.add(counts.get(i + 1));
            out.add(counts.get(i));
        }
        return List.copyOf(out);
    }
}
