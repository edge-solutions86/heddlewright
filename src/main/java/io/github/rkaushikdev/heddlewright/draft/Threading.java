package io.github.rkaushikdev.heddlewright.draft;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Indexing;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Threading: which shaft each warp end is threaded through, read left to right across the loom.
 *
 * <p>Ends are 1-based in every message a weaver sees, and the list itself is 0-based, so every
 * function here converts at its own edge.
 */
public final class Threading {

    /** The draws {@link #drawByStyle} can build. */
    public static final List<String> DRAW_STYLES = List.of("straight", "point", "broken", "undulating", "divided");

    private Threading() {}

    /** Check a threading against a shaft count and hand it back as a list of shaft numbers. */
    public static List<Integer> assertThreading(Object threading, int shafts) {
        List<Object> list = Check.array(threading, "threading", 1);
        Check.positiveInteger(shafts, "shafts");
        List<Integer> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i += 1) {
            out.add(Check.integer(list.get(i), "threading[" + i + "]", Bounds.between(1, shafts)));
        }
        return List.copyOf(out);
    }

    /** A straight draw over one repeat: 1, 2, 3 ... shafts. */
    public static List<Integer> straightDraw(int shafts) {
        return straightDraw(shafts, shafts);
    }

    /** A straight draw: 1, 2, 3 ... shafts, repeated until {@code ends} are threaded. */
    public static List<Integer> straightDraw(int shafts, int ends) {
        return straightDraw(shafts, ends, 1, 1);
    }

    /** A straight draw from a chosen shaft, running up ({@code 1}) or down ({@code -1}). */
    public static List<Integer> straightDraw(int shafts, int ends, int start, int direction) {
        Check.positiveInteger(shafts, "shafts");
        Check.positiveInteger(ends, "ends");
        Check.integer(start, "start", Bounds.between(1, shafts));
        Check.oneOf(direction, "direction", List.of(1, -1));
        List<Integer> out = new ArrayList<>(ends);
        for (int i = 0; i < ends; i += 1) {
            out.add(Numeric.mod(start - 1 + direction * i, shafts) + 1);
        }
        return List.copyOf(out);
    }

    /** One full point: up to the top shaft and back without repeating the turn, 2n - 2 ends. */
    public static List<Integer> pointDraw(int shafts) {
        Check.positiveInteger(shafts, "shafts");
        requirePoint(shafts);
        return pointDraw(shafts, 2 * shafts - 2);
    }

    /**
     * A point draw runs up to the top shaft and back down without repeating the turning shaft, so
     * one full point over n shafts is 2n - 2 ends long.
     */
    public static List<Integer> pointDraw(int shafts, int ends) {
        Check.positiveInteger(shafts, "shafts");
        // The shaft check comes first: on a single shaft the default end count works out at zero,
        // and reporting that as a bad end count hides the real problem.
        requirePoint(shafts);
        int cycle = 2 * shafts - 2;
        Check.positiveInteger(ends, "ends");
        List<Integer> out = new ArrayList<>(ends);
        for (int i = 0; i < ends; i += 1) {
            int at = Numeric.mod(i, cycle);
            out.add(at < shafts ? at + 1 : cycle - at + 1);
        }
        return List.copyOf(out);
    }

    private static void requirePoint(int shafts) {
        if (shafts < 2) {
            throw new DraftException("a point draw needs at least two shafts", Json.obj("shafts", shafts));
        }
    }

    /** A broken draw in blocks of four, each starting two shafts on. */
    public static List<Integer> brokenDraw(int shafts, int ends) {
        return brokenDraw(shafts, ends, 4, 2);
    }

    /**
     * A broken draw is a straight draw whose blocks step across a break, the threading behind a
     * broken or herringbone twill. {@code blockSize} ends run straight, then the start jumps by
     * {@code stagger}.
     */
    public static List<Integer> brokenDraw(int shafts, int ends, int blockSize, int stagger) {
        Check.positiveInteger(shafts, "shafts");
        Check.positiveInteger(ends, "ends");
        Check.positiveInteger(blockSize, "blockSize");
        List<Integer> out = new ArrayList<>(ends);
        for (int i = 0; i < ends; i += 1) {
            int block = i / blockSize;
            int within = i % blockSize;
            out.add(Numeric.mod(block * stagger + within, shafts) + 1);
        }
        return List.copyOf(out);
    }

    /**
     * An undulating draw holds each shaft for a run length taken from a repeating list, which is
     * how a wavy twill gets its varying stripe width.
     */
    public static List<Integer> undulatingDraw(int shafts, int ends, List<?> runLengths) {
        Check.positiveInteger(shafts, "shafts");
        Check.positiveInteger(ends, "ends");
        List<Object> runs = Check.array(runLengths, "runLengths", 1);
        List<Integer> lengths = new ArrayList<>(runs.size());
        for (int i = 0; i < runs.size(); i += 1) {
            lengths.add(Check.positiveInteger(runs.get(i), "runLengths[" + i + "]"));
        }
        List<Integer> out = new ArrayList<>(ends);
        int shaft = 0;
        int step = 0;
        while (out.size() < ends) {
            int run = lengths.get(step % lengths.size());
            for (int n = 0; n < run && out.size() < ends; n += 1) {
                out.add(shaft + 1);
            }
            shaft = Numeric.mod(shaft + 1, shafts);
            step += 1;
        }
        return List.copyOf(out);
    }

    /**
     * A divided draw threads two shaft groups alternately, the classic ground and pattern
     * arrangement of an overshot or a summer and winter draft.
     */
    public static List<Integer> dividedDraw(List<?> groundShafts, List<?> patternShafts, int ends) {
        List<Object> ground = Check.array(groundShafts, "groundShafts", 1);
        List<Object> pattern = Check.array(patternShafts, "patternShafts", 1);
        Check.positiveInteger(ends, "ends");
        List<Integer> out = new ArrayList<>(ends);
        for (int i = 0; i < ends; i += 1) {
            List<Object> group = i % 2 == 0 ? ground : pattern;
            out.add(Check.integer(group.get((i / 2) % group.size()), "shaft"));
        }
        return List.copyOf(out);
    }

    /** Build a threading by name, so a draft file can carry a style rather than a list. */
    public static List<Integer> drawByStyle(String style, JsonObject options) {
        Check.oneOf(style, "style", DRAW_STYLES);
        int shafts = Check.positiveInteger(options.get("shafts"), "shafts");
        Json ends = options.get("ends");
        return switch (style) {
            case "straight" ->
                straightDraw(
                        shafts,
                        ends.isNullish() ? shafts : Check.integer(ends, "ends"),
                        optional(options, "start", 1),
                        optional(options, "direction", 1));
            case "point" -> ends.isNullish() ? pointDraw(shafts) : pointDraw(shafts, Check.integer(ends, "ends"));
            case "broken" ->
                brokenDraw(
                        shafts,
                        ends.isNullish() ? shafts * 2 : Check.integer(ends, "ends"),
                        optional(options, "blockSize", 4),
                        optional(options, "stagger", 2));
            case "undulating" ->
                undulatingDraw(
                        shafts,
                        ends.isNullish() ? shafts * 2 : Check.integer(ends, "ends"),
                        options.get("runLengths").isNullish()
                                ? List.of(1, 2)
                                : Check.array(options.get("runLengths"), "runLengths"));
            default ->
                dividedDraw(
                        Check.array(options.get("groundShafts"), "groundShafts"),
                        Check.array(options.get("patternShafts"), "patternShafts"),
                        ends.isNullish() ? shafts * 2 : Check.integer(ends, "ends"));
        };
    }

    private static int optional(JsonObject options, String field, int fallback) {
        Json value = options.get(field);
        return value.isNullish() ? fallback : Check.integer(value, field);
    }

    /** How many ends sit on each shaft, shaft 1 first. */
    public static List<Integer> shaftLoad(List<Integer> threading, int shafts) {
        assertThreading(threading, shafts);
        int[] load = new int[shafts];
        for (int shaft : threading) {
            load[shaft - 1] += 1;
        }
        return java.util.Arrays.stream(load).boxed().toList();
    }

    /** Shafts with nothing threaded on them, which means heddles standing idle. */
    public static List<Integer> idleShafts(List<Integer> threading, int shafts) {
        return Indexing.positionsOf(shaftLoad(threading, shafts), 0);
    }

    /** The 1-based end positions threaded on a given shaft. */
    public static List<Integer> endsOnShaft(List<Integer> threading, int shaft) {
        Check.positiveInteger(shaft, "shaft");
        return Indexing.positionsOf(threading, shaft);
    }

    /** The lowest shaft count a threading could be rewritten onto. */
    public static int shaftsUsed(List<Integer> threading) {
        Check.array(threading, "threading", 1);
        return new HashSet<>(threading).size();
    }
}
