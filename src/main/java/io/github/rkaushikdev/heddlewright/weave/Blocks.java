package io.github.rkaushikdev.heddlewright.weave;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Profile drafts and block substitution.
 *
 * <p>A profile says nothing about interlacement. It names blocks across the warp and down the weft,
 * and a block that meets itself is figure while a block that meets a different one is ground.
 * Substituting a weave for each of those two states turns the profile into cloth, which is how
 * damask, overshot and summer and winter are all drafted from the same small diagram.
 *
 * <p>Block sizes ({@code units}) are either one thread count for every block or a map giving each
 * block its own, as a profile file writes them.
 */
public final class Blocks {

    /** The thread span one block occupies, 1-based and inclusive. */
    public record Span(String block, int from, int to, int threads) {}

    private Blocks() {}

    /** Check a profile order and return the distinct block names in first-seen order. */
    public static List<String> profileBlocks(List<?> order) {
        List<Object> list = Check.array(order, "order", 1);
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < list.size(); i += 1) {
            String block = Check.nonEmptyString(list.get(i), "order[" + i + "]");
            if (!seen.contains(block)) {
                seen.add(block);
            }
        }
        return List.copyOf(seen);
    }

    /**
     * How many threads each block takes. A number applies to every block; a map gives each block its
     * own size, and any block it does not name is refused rather than defaulted.
     */
    public static Map<String, Integer> blockSizes(List<?> order, Object units) {
        List<String> blocks = profileBlocks(order);
        Map<String, Integer> sizes = new LinkedHashMap<>();
        if (units instanceof Number || units instanceof Json.Num) {
            int size = Check.positiveInteger(units, "units");
            blocks.forEach(block -> sizes.put(block, size));
            return java.util.Collections.unmodifiableMap(sizes);
        }
        JsonObject perBlock = Check.plainObject(units, "units");
        for (String block : blocks) {
            Json size = perBlock.get(block);
            if (size.isMissing()) {
                throw new DraftException(
                        "block " + block + " has no thread count", Json.obj("block", block, "blocks", blocks));
            }
            sizes.put(block, Check.positiveInteger(size, "units." + block));
        }
        return java.util.Collections.unmodifiableMap(sizes);
    }

    /** Expand a block order into one entry per thread. */
    public static List<String> expandProfileOrder(List<?> order, Object units) {
        Map<String, Integer> sizes = blockSizes(order, units);
        List<String> out = new ArrayList<>();
        for (Object entry : order) {
            String block = blockName(entry);
            for (int n = 0; n < sizes.get(block); n += 1) {
                out.add(block);
            }
        }
        return List.copyOf(out);
    }

    private static String blockName(Object entry) {
        return entry instanceof Json.Str json ? json.value() : String.valueOf(entry);
    }

    /** Turn a profile into cloth one thread to a block unit each way. */
    public static Matrix substituteProfile(
            List<?> profileThreading, List<?> profileTreadling, Matrix figure, Matrix ground) {
        return substituteProfile(profileThreading, profileTreadling, figure, ground, 1, 1);
    }

    /** Turn a profile into cloth with the same block units in the warp and the weft. */
    public static Matrix substituteProfile(
            List<?> profileThreading, List<?> profileTreadling, Matrix figure, Matrix ground, Object warpUnits) {
        return substituteProfile(profileThreading, profileTreadling, figure, ground, warpUnits, warpUnits);
    }

    /**
     * Turn a profile into cloth. Every intersection where the warp block equals the weft block takes
     * the figure weave; everywhere else takes the ground.
     */
    public static Matrix substituteProfile(
            List<?> profileThreading,
            List<?> profileTreadling,
            Matrix figure,
            Matrix ground,
            Object warpUnits,
            Object weftUnits) {
        Combine.assertInterlaces(Check.required(figure, "figure"), "figure");
        Combine.assertInterlaces(Check.required(ground, "ground"), "ground");
        List<String> ends = expandProfileOrder(profileThreading, warpUnits);
        List<String> picks = expandProfileOrder(profileTreadling, weftUnits);
        int[][] cloth = new int[picks.size()][ends.size()];
        for (int p = 0; p < picks.size(); p += 1) {
            for (int e = 0; e < ends.size(); e += 1) {
                Matrix weave = ends.get(e).equals(picks.get(p)) ? figure : ground;
                cloth[p][e] = weave.get(p % weave.rows(), e % weave.cols());
            }
        }
        return Matrix.of(cloth);
    }

    /** The block a given thread belongs to, 1-based, for reading a report back. */
    public static String blockAt(List<?> order, Object units, int thread) {
        Check.positiveInteger(thread, "thread");
        List<String> expanded = expandProfileOrder(order, units);
        if (thread > expanded.size()) {
            throw new DraftException(
                    "that thread is past the end of the profile",
                    Json.obj("thread", thread, "threads", expanded.size()));
        }
        return expanded.get(thread - 1);
    }

    /** The 1-based thread span each block occupies, in profile order. */
    public static List<Span> blockSpans(List<?> order, Object units) {
        Map<String, Integer> sizes = blockSizes(order, units);
        List<Span> spans = new ArrayList<>();
        int at = 1;
        for (Object entry : order) {
            String block = blockName(entry);
            int size = sizes.get(block);
            spans.add(new Span(block, at, at + size - 1, size));
            at += size;
        }
        return List.copyOf(spans);
    }

    /**
     * Whether any block sits next to itself in the order. A tie-down profile has none, which is what
     * keeps a figure from running into the next one.
     */
    public static boolean hasAdjacentRepeats(List<?> order) {
        List<Object> list = Check.array(order, "order", 1);
        for (int i = 1; i < list.size(); i += 1) {
            if (list.get(i).equals(list.get(i - 1))) {
                return true;
            }
        }
        return false;
    }

    /** The block diagram itself, as a matrix of figure and ground cells. */
    public static Matrix profileMatrix(List<?> profileThreading, List<?> profileTreadling) {
        List<Object> warp = Check.array(profileThreading, "profileThreading", 1);
        List<Object> weft = Check.array(profileTreadling, "profileTreadling", 1);
        int[][] cells = new int[weft.size()][warp.size()];
        for (int p = 0; p < weft.size(); p += 1) {
            for (int e = 0; e < warp.size(); e += 1) {
                cells[p][e] = warp.get(e).equals(weft.get(p)) ? 1 : 0;
            }
        }
        return Matrix.of(cells);
    }
}
