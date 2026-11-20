package io.github.rkaushikdev.heddlewright.draft;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Cycle;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Indexing;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Treadling: which treadles are pressed for each pick, in weaving order.
 *
 * <p>A pick is a list because two feet can be down at once on a countermarch loom, and because a
 * single-entry list keeps every consumer from having to handle both a number and a list.
 */
public final class Treadling {

    private Treadling() {}

    /** Accept a number, or a list of numbers, per pick and return the list form. */
    public static List<List<Integer>> normaliseTreadling(Object treadling, int treadles) {
        List<Object> picks = Check.array(treadling, "treadling", 1);
        Check.positiveInteger(treadles, "treadles");
        List<List<Integer>> out = new ArrayList<>(picks.size());
        for (int p = 0; p < picks.size(); p += 1) {
            out.add(Tieup.sortedUnique(entries(picks.get(p)), "treadling[" + p + "]", treadles));
        }
        return List.copyOf(out);
    }

    private static List<Object> entries(Object pick) {
        if (pick instanceof List<?> || pick instanceof JsonArray) {
            return Check.array(pick, "pick");
        }
        List<Object> single = new ArrayList<>(1);
        single.add(pick);
        return single;
    }

    /** A pick pressing no treadle leaves the weft lying loose across the warp. */
    public static List<List<Integer>> assertNoBlankPicks(Object treadling, int treadles) {
        List<List<Integer>> normalised = normaliseTreadling(treadling, treadles);
        List<Integer> blanks = Indexing.positionsWhere(normalised, List::isEmpty);
        if (!blanks.isEmpty()) {
            throw new DraftException("a pick presses no treadle", Json.obj("picks", blanks));
        }
        return normalised;
    }

    /** Treadle 1, 2, 3 ... once round. */
    public static List<List<Integer>> straightTreadling(int treadles) {
        return straightTreadling(treadles, treadles);
    }

    /** Treadle 1, 2, 3 ... and round again, the plain treadling of a straight draw. */
    public static List<List<Integer>> straightTreadling(int treadles, int picks) {
        return straightTreadling(treadles, picks, 1, 1);
    }

    /** A straight treadling from a chosen treadle, running up ({@code 1}) or down ({@code -1}). */
    public static List<List<Integer>> straightTreadling(int treadles, int picks, int start, int direction) {
        Check.positiveInteger(treadles, "treadles");
        Check.positiveInteger(picks, "picks");
        Check.integer(start, "start", Bounds.between(1, treadles));
        List<List<Integer>> out = new ArrayList<>(picks);
        for (int i = 0; i < picks; i += 1) {
            out.add(List.of(Numeric.mod(start - 1 + direction * i, treadles) + 1));
        }
        return List.copyOf(out);
    }

    /** One full point: up and back down without repeating the turn. */
    public static List<List<Integer>> pointTreadling(int treadles) {
        Check.positiveInteger(treadles, "treadles");
        requirePoint(treadles);
        return pointTreadling(treadles, 2 * treadles - 2);
    }

    /** Up and back down without repeating the turn, mirroring a point draw. */
    public static List<List<Integer>> pointTreadling(int treadles, int picks) {
        Check.positiveInteger(treadles, "treadles");
        requirePoint(treadles);
        int cycle = 2 * treadles - 2;
        Check.positiveInteger(picks, "picks");
        List<List<Integer>> out = new ArrayList<>(picks);
        for (int i = 0; i < picks; i += 1) {
            int at = Numeric.mod(i, cycle);
            out.add(List.of(at < treadles ? at + 1 : cycle - at + 1));
        }
        return List.copyOf(out);
    }

    private static void requirePoint(int treadles) {
        if (treadles < 2) {
            throw new DraftException("a point treadling needs at least two treadles", Json.obj("treadles", treadles));
        }
    }

    /** Tromp as writ with shaft n on treadle n. */
    public static List<List<Integer>> trompAsWrit(List<Integer> threading) {
        return trompAsWrit(threading, (shaft, index) -> shaft);
    }

    /**
     * Tromp as writ: treadle in the order the warp was threaded, which is what turns a point
     * threading into a diamond rather than a stripe. The map sees each shaft and its end index.
     */
    public static List<List<Integer>> trompAsWrit(
            List<Integer> threading, BiFunction<Integer, Integer, Integer> shaftToTreadle) {
        Check.array(threading, "threading", 1);
        List<List<Integer>> out = new ArrayList<>(threading.size());
        for (int i = 0; i < threading.size(); i += 1) {
            Integer treadle = shaftToTreadle.apply(threading.get(i), i);
            out.add(List.of(Check.integer(treadle, "treadle for end " + (i + 1), Bounds.atLeast(1))));
        }
        return List.copyOf(out);
    }

    /** An advancing treadling in blocks of four, each starting one treadle on. */
    public static List<List<Integer>> advancingTreadling(int treadles, int picks) {
        return advancingTreadling(treadles, picks, 4, 1);
    }

    /**
     * An advancing twill moves the start of each block on by {@code advance} picks, the device
     * behind a rolling twill.
     */
    public static List<List<Integer>> advancingTreadling(int treadles, int picks, int blockSize, int advance) {
        Check.positiveInteger(treadles, "treadles");
        Check.positiveInteger(picks, "picks");
        Check.positiveInteger(blockSize, "blockSize");
        List<List<Integer>> out = new ArrayList<>(picks);
        for (int i = 0; i < picks; i += 1) {
            int block = i / blockSize;
            int within = i % blockSize;
            out.add(List.of(Numeric.mod(block * advance + within, treadles) + 1));
        }
        return List.copyOf(out);
    }

    /** Weave the sequence forwards then back, without repeating the turning picks. */
    public static <T> List<T> boustrophedon(List<T> treadling) {
        return boustrophedon(treadling, false);
    }

    /** Weave the sequence forwards then backwards, the way a boustrophedon runs. */
    public static <T> List<T> boustrophedon(List<T> treadling, boolean repeatTurn) {
        Check.array(treadling, "treadling", 1);
        List<T> back = new ArrayList<>(treadling);
        Collections.reverse(back);
        List<T> out = new ArrayList<>(treadling);
        out.addAll(repeatTurn ? back : back.subList(Math.min(1, back.size()), Math.max(1, back.size() - 1)));
        return List.copyOf(out);
    }

    /** How many picks before the treadling comes back to where it started. */
    public static int treadlingRepeat(Object treadling, int treadles) {
        List<String> symbols = normaliseTreadling(treadling, treadles).stream()
                .map(pick -> pick.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .toList();
        return Cycle.period(symbols);
    }

    /** How many picks each treadle carries, treadle 1 first. */
    public static List<Integer> treadleUsage(Object treadling, int treadles) {
        int[] usage = new int[treadles];
        for (List<Integer> pick : normaliseTreadling(treadling, treadles)) {
            for (int treadle : pick) {
                usage[treadle - 1] += 1;
            }
        }
        return java.util.Arrays.stream(usage).boxed().toList();
    }

    /** Treadles the treadling never presses, so their cords could be freed. */
    public static List<Integer> unusedTreadles(Object treadling, int treadles) {
        return Indexing.positionsOf(treadleUsage(treadling, treadles), 0);
    }

    /** Repeat a treadling until it reaches {@code picks}, cutting mid-repeat if it must. */
    public static List<List<Integer>> extendTreadling(List<?> treadling, int picks) {
        Check.array(treadling, "treadling", 1);
        Check.positiveInteger(picks, "picks");
        List<List<Integer>> out = new ArrayList<>(picks);
        for (int i = 0; i < picks; i += 1) {
            List<Integer> pick = new ArrayList<>();
            for (Object treadle : entries(treadling.get(i % treadling.size()))) {
                pick.add(Check.integer(treadle, "treadle"));
            }
            out.add(List.copyOf(pick));
        }
        return List.copyOf(out);
    }
}
