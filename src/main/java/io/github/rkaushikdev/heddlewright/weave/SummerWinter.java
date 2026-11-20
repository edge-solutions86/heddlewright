package io.github.rkaushikdev.heddlewright.weave;

import io.github.rkaushikdev.heddlewright.draft.Shed;
import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Summer and winter.
 *
 * <p>Two shafts do nothing but tie the pattern weft down; every other shaft carries one block. A
 * unit of a block is threaded tie, pattern, tie, pattern, so a block is four ends wide and no float
 * can run further than the tie-downs allow. That is the whole point of the weave: a coverlet in
 * summer and winter can be woven in as many blocks as the loom has shafts to spare, and none of
 * them floats long enough to catch.
 */
public final class SummerWinter {

    /** The two shafts that only tie the pattern weft down. */
    public static final List<Integer> TIE_SHAFTS = List.of(1, 2);

    private SummerWinter() {}

    /** How many shafts a block count needs, which is the blocks plus the two ties. */
    public static int shaftsForBlocks(int blocks) {
        Check.positiveInteger(blocks, "blocks");
        return blocks + TIE_SHAFTS.size();
    }

    /** How many blocks a loom of this many shafts can carry. */
    public static int blocksForShafts(int shafts) {
        Check.positiveInteger(shafts, "shafts");
        if (shafts <= TIE_SHAFTS.size()) {
            throw new DraftException(
                    "summer and winter needs more than the two tie shafts", Json.obj("shafts", shafts));
        }
        return shafts - TIE_SHAFTS.size();
    }

    /** The pattern shaft a block sits on, 1-based block numbers. */
    public static int shaftForBlock(int block) {
        Check.positiveInteger(block, "block");
        return block + TIE_SHAFTS.size();
    }

    /** Thread a run of blocks one unit each. */
    public static List<Integer> summerWinterThreading(List<Integer> blocks) {
        return summerWinterThreading(blocks, 1);
    }

    /** Thread a run of blocks, four ends to a unit. */
    public static List<Integer> summerWinterThreading(List<Integer> blocks, int units) {
        Check.array(blocks, "blocks", 1);
        Check.positiveInteger(units, "units");
        List<Integer> out = new ArrayList<>();
        for (int block : blocks) {
            int pattern = shaftForBlock(block);
            for (int unit = 0; unit < units; unit += 1) {
                out.addAll(List.of(TIE_SHAFTS.get(0), pattern, TIE_SHAFTS.get(1), pattern));
            }
        }
        return List.copyOf(out);
    }

    /**
     * The tie-up: two treadles per block, one against each tie shaft, then the two tabby treadles.
     * The tabby lifts the ties against everything else, which is what makes the ground plain weave
     * whatever the pattern is doing.
     */
    public static List<List<Integer>> summerWinterTieup(int blockCount) {
        Check.positiveInteger(blockCount, "blockCount");
        List<Integer> patternShafts = new ArrayList<>(blockCount);
        for (int index = 0; index < blockCount; index += 1) {
            patternShafts.add(shaftForBlock(index + 1));
        }
        List<List<Integer>> treadles = new ArrayList<>();
        for (int shaft : patternShafts) {
            treadles.add(List.of(TIE_SHAFTS.get(0), shaft));
            treadles.add(List.of(TIE_SHAFTS.get(1), shaft));
        }
        treadles.add(TIE_SHAFTS);
        treadles.add(List.copyOf(patternShafts));
        return List.copyOf(treadles);
    }

    /** The two treadles that weave a block, 1-based into that tie-up. */
    public static List<Integer> treadlesForBlock(int block) {
        Check.positiveInteger(block, "block");
        return List.of(block * 2 - 1, block * 2);
    }

    /** The two tabby treadles for a given block count. */
    public static List<Integer> tabbyTreadles(int blockCount) {
        Check.positiveInteger(blockCount, "blockCount");
        return List.of(blockCount * 2 + 1, blockCount * 2 + 2);
    }

    /** Treadle a run of blocks one unit each. */
    public static List<List<Integer>> summerWinterTreadling(List<Integer> blocks, int blockCount) {
        return summerWinterTreadling(blocks, blockCount, 1);
    }

    /**
     * Treadle a run of blocks. Each unit takes both of the block's treadles in turn, which is what
     * alternates the tie-down and stops the pattern weft floating over the same tie twice running.
     */
    public static List<List<Integer>> summerWinterTreadling(List<Integer> blocks, int blockCount, int units) {
        Check.array(blocks, "blocks", 1);
        Check.positiveInteger(blockCount, "blockCount");
        Check.positiveInteger(units, "units");
        List<List<Integer>> out = new ArrayList<>();
        for (int block : blocks) {
            if (block > blockCount) {
                throw new DraftException(
                        "that block is past the ones the loom is tied for",
                        Json.obj("block", block, "blockCount", blockCount));
            }
            List<Integer> pair = treadlesForBlock(block);
            for (int unit = 0; unit < units; unit += 1) {
                out.add(List.of(pair.get(0)));
                out.add(List.of(pair.get(1)));
            }
        }
        return List.copyOf(out);
    }

    /** The whole draft for a run of blocks, one unit each. */
    public static JsonObject summerWinterDraft(List<Integer> blocks) {
        return summerWinterDraft(blocks, 1);
    }

    /** The whole draft for a run of blocks, as a plain draft record. */
    public static JsonObject summerWinterDraft(List<Integer> blocks, int units) {
        int blockCount = blocks.stream().mapToInt(Integer::intValue).max().orElse(0);
        return Json.obj(
                "name",
                "summer and winter " + blocks.stream().map(String::valueOf).collect(Collectors.joining()),
                "shafts",
                shaftsForBlocks(blockCount),
                "treadles",
                blockCount * 2 + 2,
                "shed",
                Shed.RISING,
                "threading",
                summerWinterThreading(blocks, units),
                "tieup",
                summerWinterTieup(blockCount),
                "treadling",
                summerWinterTreadling(blocks, blockCount, units));
    }

    /**
     * The longest the pattern weft can float, which is two ends whatever the block size, because a
     * tie-down sits between every pair of pattern ends.
     */
    public static int maximumFloat() {
        return 2;
    }

    /** Refuse a threading that is missing one of its tie shafts. */
    public static List<Integer> assertTiesThreaded(List<Integer> threading) {
        Check.array(threading, "threading", 1);
        for (int shaft : TIE_SHAFTS) {
            if (!threading.contains(shaft)) {
                throw new DraftException("tie shaft " + shaft + " carries no ends", Json.obj("shaft", shaft));
            }
        }
        return threading;
    }

    /** How many ends a run of blocks takes at one unit a block. */
    public static int endsForBlocks(List<Integer> blocks) {
        return endsForBlocks(blocks, 1);
    }

    /** How many ends a run of blocks takes. */
    public static int endsForBlocks(List<Integer> blocks, int units) {
        Check.array(blocks, "blocks", 1);
        Check.integer(units, "units", Bounds.atLeast(1));
        return blocks.size() * units * 4;
    }
}
