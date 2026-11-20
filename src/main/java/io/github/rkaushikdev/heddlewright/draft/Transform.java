package io.github.rkaushikdev.heddlewright.draft;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Transforming a draft.
 *
 * <p>A weaver rewrites a draft far more often than they invent one: turn it so the warp becomes the
 * weft, mirror it, shift the threading along, or fold it back on itself. Each of these is a small
 * change to the three lists, and each one has a different effect on the cloth, so they are kept
 * apart rather than gathered behind one clever function with a mode argument.
 *
 * <p>Each takes a draft or a plain draft record and hands back the plain record with the change
 * made, which {@link Draft#create} builds into a draft again.
 */
public final class Transform {

    /** The two ways a draft folds; {@code both} folds both. */
    public static final List<String> DIRECTIONS = List.of("warp", "weft");

    private Transform() {}

    private static JsonObject record(Object draft) {
        return Check.plainObject(draft instanceof Draft built ? built.toRecord() : draft, "draft");
    }

    private static int shafts(JsonObject draft) {
        return Check.positiveInteger(draft.get("shafts"), "shafts");
    }

    private static int treadles(JsonObject draft) {
        return Check.positiveInteger(draft.get("treadles"), "treadles");
    }

    private static <T> List<T> reversed(List<T> list) {
        List<T> copy = new ArrayList<>(list);
        Collections.reverse(copy);
        return copy;
    }

    /** Reverse the threading, which mirrors the cloth left to right. */
    public static JsonObject mirrorThreading(Object draft) {
        JsonObject record = record(draft);
        List<Integer> threading = Threading.assertThreading(record.get("threading"), shafts(record));
        return record.with("threading", reversed(threading));
    }

    /** Reverse the treadling, which turns the cloth upside down. */
    public static JsonObject mirrorTreadling(Object draft) {
        JsonObject record = record(draft);
        Treadling.normaliseTreadling(record.get("treadling"), treadles(record));
        return record.with("treadling", reversed(Check.array(record.get("treadling"), "treadling")));
    }

    /** Shift the threading along by a number of ends, wrapping round. */
    public static JsonObject shiftThreading(Object draft, int by) {
        JsonObject record = record(draft);
        List<Integer> threading = Threading.assertThreading(record.get("threading"), shafts(record));
        return record.with("threading", rotate(threading, by));
    }

    /** Shift the treadling along by a number of picks, wrapping round. */
    public static JsonObject shiftTreadling(Object draft, int by) {
        JsonObject record = record(draft);
        List<List<Integer>> treadling = Treadling.normaliseTreadling(record.get("treadling"), treadles(record));
        return record.with("treadling", rotate(treadling, by));
    }

    private static <T> List<T> rotate(List<T> list, int by) {
        int at = Numeric.mod(by, list.size());
        List<T> out = new ArrayList<>(list.subList(at, list.size()));
        out.addAll(list.subList(0, at));
        return out;
    }

    /**
     * Turn the draft: the threading becomes the treadling and the tie-up is transposed, so what was
     * warp is now weft. It needs as many treadles as the old draft had shafts and the other way
     * round, which is where a turned draft usually stops being weavable.
     */
    public static JsonObject turnDraft(Object draft) {
        JsonObject record = record(draft);
        int shafts = shafts(record);
        int treadles = treadles(record);
        List<Integer> threading = Threading.assertThreading(record.get("threading"), shafts);
        List<List<Integer>> tieup = Tieup.normaliseTieup(record.get("tieup"), shafts);
        List<List<Integer>> treadling = Treadling.normaliseTreadling(record.get("treadling"), treadles);
        if (treadling.stream().anyMatch(pick -> pick.size() != 1)) {
            throw new DraftException("a draft with a doubled pick cannot be turned", JsonObject.EMPTY);
        }
        List<List<Integer>> turnedTieup = new ArrayList<>(shafts);
        for (int shaft = 1; shaft <= shafts; shaft += 1) {
            List<Integer> lifting = new ArrayList<>();
            for (int treadle = 0; treadle < tieup.size(); treadle += 1) {
                if (tieup.get(treadle).contains(shaft)) {
                    lifting.add(treadle + 1);
                }
            }
            turnedTieup.add(lifting);
        }
        return record.with(
                "name",
                Text.string(record.get("name")) + " turned",
                "shafts",
                treadles,
                "treadles",
                shafts,
                "threading",
                treadling.stream().map(pick -> pick.get(0)).toList(),
                "tieup",
                turnedTieup,
                "treadling",
                threading.stream().map(List::of).toList());
    }

    /**
     * Fold a list back on itself without repeating the turning thread, which is how a straight draw
     * becomes a point.
     */
    public static <T> List<T> foldBack(List<T> list) {
        Check.array(list, "list", 2);
        List<T> back = reversed(list);
        List<T> out = new ArrayList<>(list);
        out.addAll(back.subList(1, back.size() - 1));
        return List.copyOf(out);
    }

    /** Fold the threading ({@code warp}), the treadling ({@code weft}), or {@code both}. */
    public static JsonObject foldDraft(Object draft, String direction) {
        List<String> allowed = new ArrayList<>(DIRECTIONS);
        allowed.add("both");
        Check.oneOf(direction, "direction", allowed);
        JsonObject folded = record(draft);
        if (direction.equals("warp") || direction.equals("both")) {
            folded = folded.with("threading", foldBack(Check.array(folded.get("threading"), "threading")));
        }
        if (direction.equals("weft") || direction.equals("both")) {
            folded = folded.with("treadling", foldBack(Check.array(folded.get("treadling"), "treadling")));
        }
        return folded;
    }

    /** Repeat the threading and the treadling until they reach a given size. */
    public static JsonObject extendDraft(Object draft, int ends, int picks) {
        Check.positiveInteger(ends, "ends");
        Check.positiveInteger(picks, "picks");
        JsonObject record = record(draft);
        List<Integer> threading = Threading.assertThreading(record.get("threading"), shafts(record));
        List<List<Integer>> treadling = Treadling.normaliseTreadling(record.get("treadling"), treadles(record));
        List<Integer> wideThreading = new ArrayList<>(ends);
        for (int i = 0; i < ends; i += 1) {
            wideThreading.add(threading.get(i % threading.size()));
        }
        List<List<Integer>> longTreadling = new ArrayList<>(picks);
        for (int i = 0; i < picks; i += 1) {
            longTreadling.add(treadling.get(i % treadling.size()));
        }
        return record.with("threading", wideThreading, "treadling", longTreadling);
    }

    /**
     * Rewrite a draft onto the lowest shaft numbers it actually uses, which is what lets a draft
     * written for sixteen shafts go on an eight shaft loom when only eight of them were ever
     * threaded.
     */
    public static JsonObject compactShafts(Object draft) {
        JsonObject record = record(draft);
        int shafts = shafts(record);
        List<Integer> threading = Threading.assertThreading(record.get("threading"), shafts);
        List<Integer> used = new ArrayList<>(new TreeSet<>(threading));
        Map<Integer, Integer> renumber = new TreeMap<>();
        for (int index = 0; index < used.size(); index += 1) {
            renumber.put(used.get(index), index + 1);
        }
        List<List<Integer>> tieup = new ArrayList<>();
        for (List<Integer> entry : Tieup.normaliseTieup(record.get("tieup"), shafts)) {
            tieup.add(entry.stream()
                    .filter(renumber::containsKey)
                    .map(renumber::get)
                    .toList());
        }
        return record.with(
                "shafts", used.size(),
                "threading", threading.stream().map(renumber::get).toList(),
                "tieup", tieup);
    }
}
