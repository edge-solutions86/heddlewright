package io.github.rkaushikdev.heddlewright.weave;

import io.github.rkaushikdev.heddlewright.draft.Shed;
import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Overshot.
 *
 * <p>Four shafts, worked in overlapping pairs: shafts one and two make block A, two and three block
 * B, three and four block C, four and one block D. Every block shares a shaft with the two next to
 * it, which is what ties the figure together and what limits a four shaft overshot to those four
 * blocks and no others.
 *
 * <p>The cloth is two wefts. A pattern pick floats over its block; a tabby pick binds the ground
 * underneath it. They alternate, so a coverlet woven this way has twice as many picks as it has
 * pattern picks.
 */
public final class Overshot {

    /** The four blocks four shafts can carry. */
    public static final List<String> BLOCKS = List.of("A", "B", "C", "D");

    private static final Map<String, List<Integer>> BLOCK_SHAFTS =
            Map.of("A", List.of(1, 2), "B", List.of(2, 3), "C", List.of(3, 4), "D", List.of(4, 1));

    /** How many units each block is threaded and trodden for, and which tabby treadle goes first. */
    public record Options(int units, int startTabby) {

        /** Two units a block, starting on the first tabby. */
        public static final Options DEFAULT = new Options(2, 0);

        /** The same options with another unit count. */
        public Options units(int count) {
            return new Options(count, startTabby);
        }

        /** The same options starting on the other tabby. */
        public Options startTabby(int which) {
            return new Options(units, which);
        }
    }

    private Overshot() {}

    /** The two shafts a block is threaded on. */
    public static List<Integer> blockShafts(String block) {
        Check.oneOf(block, "block", BLOCKS);
        return BLOCK_SHAFTS.get(block);
    }

    /** True when two blocks share a shaft, which is what lets them run together. */
    public static boolean blocksAdjoin(String left, String right) {
        List<Integer> b = blockShafts(right);
        return blockShafts(left).stream().anyMatch(b::contains);
    }

    /** Thread a run of blocks two units each. */
    public static List<Integer> overshotThreading(List<String> blocks) {
        return overshotThreading(blocks, Options.DEFAULT);
    }

    /**
     * Thread a run of blocks. Each block is threaded on its two shafts, repeated for the number of
     * units the block is given, and the threading is checked so that neighbouring blocks in the list
     * actually adjoin.
     */
    public static List<Integer> overshotThreading(List<String> blocks, Options options) {
        Check.array(blocks, "blocks", 1);
        Check.positiveInteger(options.units(), "units");
        List<Integer> out = new ArrayList<>();
        for (int index = 0; index < blocks.size(); index += 1) {
            String block = blocks.get(index);
            List<Integer> shafts = blockShafts(block);
            if (index > 0 && !blocksAdjoin(blocks.get(index - 1), block)) {
                throw new DraftException(
                        "those two blocks do not share a shaft",
                        Json.obj("at", index + 1, "from", blocks.get(index - 1), "to", block));
            }
            for (int unit = 0; unit < options.units(); unit += 1) {
                out.add(shafts.get(0));
                out.add(shafts.get(1));
            }
        }
        return List.copyOf(out);
    }

    /**
     * The tie-up a four shaft overshot loom is corded for: four pattern treadles, one per block, then
     * the two tabby treadles.
     */
    public static List<List<Integer>> overshotTieup() {
        List<List<Integer>> tieup = new ArrayList<>();
        BLOCKS.forEach(block -> tieup.add(blockShafts(block)));
        tieup.add(List.of(1, 3));
        tieup.add(List.of(2, 4));
        return List.copyOf(tieup);
    }

    /** Which treadle weaves a block, 1-based into the tie-up above. */
    public static int treadleForBlock(String block) {
        Check.oneOf(block, "block", BLOCKS);
        return BLOCKS.indexOf(block) + 1;
    }

    /** The two tabby treadles, which never change. */
    public static List<Integer> tabbyTreadles() {
        return List.of(BLOCKS.size() + 1, BLOCKS.size() + 2);
    }

    /** Treadle a run of blocks two units each, starting on the first tabby. */
    public static List<List<Integer>> overshotTreadling(List<String> blocks) {
        return overshotTreadling(blocks, Options.DEFAULT);
    }

    /**
     * Treadle a run of blocks with a tabby pick after every pattern pick. The tabby alternates on its
     * own account, so the ground stays plain weave however the pattern is ordered.
     */
    public static List<List<Integer>> overshotTreadling(List<String> blocks, Options options) {
        Check.array(blocks, "blocks", 1);
        Check.positiveInteger(options.units(), "units");
        Check.integer(options.startTabby(), "startTabby", Bounds.between(0, 1));
        List<Integer> tabby = tabbyTreadles();
        List<List<Integer>> out = new ArrayList<>();
        int which = options.startTabby();
        for (String block : blocks) {
            int treadle = treadleForBlock(block);
            for (int unit = 0; unit < options.units(); unit += 1) {
                out.add(List.of(treadle));
                out.add(List.of(tabby.get(which)));
                which = 1 - which;
            }
        }
        return List.copyOf(out);
    }

    /** The whole draft, two units a block, treadled in the order it was threaded. */
    public static JsonObject overshotDraft(List<String> blocks) {
        return overshotDraft(blocks, Options.DEFAULT);
    }

    /** The whole draft as a plain draft record, treadled in the order it was threaded. */
    public static JsonObject overshotDraft(List<String> blocks, Options options) {
        return Json.obj(
                "name",
                "overshot " + String.join("", blocks),
                "shafts",
                4,
                "treadles",
                6,
                "shed",
                Shed.RISING,
                "threading",
                overshotThreading(blocks, options),
                "tieup",
                overshotTieup(),
                "treadling",
                overshotTreadling(blocks, options));
    }

    /** How long the pattern weft floats over a block of two units. */
    public static int patternFloat() {
        return patternFloat(Options.DEFAULT.units());
    }

    /**
     * How long the pattern weft floats over a block, in ends. It is twice the units in the block,
     * because each unit is two ends on the same pair of shafts.
     */
    public static int patternFloat(int units) {
        Check.positiveInteger(units, "units");
        return units * 2;
    }

    /** Refuse a block of two units whose float would be too long to wear. */
    public static int assertFloatWithin(int limit) {
        return assertFloatWithin(limit, Options.DEFAULT.units());
    }

    /** Refuse a block run whose float would be too long to wear. */
    public static int assertFloatWithin(int limit, int units) {
        Check.positiveInteger(limit, "limit");
        int length = patternFloat(units);
        if (length > limit) {
            throw new DraftException(
                    "the pattern float is longer than the cloth will carry", Json.obj("float", length, "limit", limit));
        }
        return length;
    }

    /** Every block run that can follow a given block on four shafts. */
    public static List<String> blocksAfter(String block) {
        return BLOCKS.stream()
                .filter(candidate -> !candidate.equals(block) && blocksAdjoin(block, candidate))
                .toList();
    }
}
