package io.github.rkaushikdev.heddlewright.loom;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Group;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.util.Runs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Drop box motion: weaving more than one weft on a shuttle loom.
 *
 * <p>A shuttle loom picks from whatever box is lifted into line with the race, so a second colour
 * means a second box and a chain to lift it. Two rules govern everything else here.
 *
 * <p>The first is the shuttle's own: a shuttle sent from the left arrives on the right, and it can
 * only be picked again from the box it landed in. On a loom boxed at one end only, every weft
 * therefore has to be picked an even number of times in a row, or its shuttle finishes on the plain
 * side with no box to hold it. A loom boxed at both ends has no such rule and can pick single picks
 * of anything.
 *
 * <p>The second is timing: the box has to be lifted and settled between one pick and the next, so
 * a change costs part of a pick. A pattern that changes on every pick runs measurably slower than
 * one that changes every fourth, and which colour sits in which box decides how far the box
 * travels. The plan itself, which puts the two together, is {@link BoxPlan}.
 */
public final class BoxMotion {

    /** Which ends of the sley carry boxes. */
    public enum Boxing {
        /** Boxes at one end only: every weft is picked in pairs. */
        ONE,
        /** Boxes at both ends: any order can be picked. */
        BOTH;

        /** The two boxings, one end first. */
        public static final List<Boxing> BOXING = List.of(values());

        /** Read a boxing by its name, refusing anything else. */
        public static Boxing parse(Object text) {
            return Check.member(text, "boxedAt", values());
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** A run of one colour: the colour, the pick it starts on, and how many picks it runs. */
    public record WeftRun(String colour, int start, int picks) {}

    /** The box a colour is given, and how many picks of the repeat it carries. */
    public record Allotment(String colour, int box, int picks) {}

    private BoxMotion() {}

    /**
     * Read a weft pattern into runs of one colour.
     *
     * <p>Runs are read straight through rather than round the loop. A pattern is repeated, so the
     * last run and the first do join in the cloth, but the box chain is cut and laced as written and
     * the weaver reads it that way.
     */
    public static List<WeftRun> weftRuns(List<?> pattern) {
        return Runs.runs(colours(pattern)).stream()
                .map(run -> new WeftRun(run.value(), run.start() + 1, run.length()))
                .toList();
    }

    /** Check every pick of a pattern names a colour, and hand the colours back. */
    static List<String> colours(List<?> pattern) {
        List<Object> list = Check.array(pattern, "pattern", 1);
        List<String> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i += 1) {
            Object colour = list.get(i) instanceof Json.Str json ? json.value() : list.get(i);
            if (!(colour instanceof String name) || name.isEmpty()) {
                throw new InvalidArgumentException(
                        "pattern[" + i + "] must be a colour name", Json.obj("got", list.get(i)));
            }
            out.add(name);
        }
        return out;
    }

    /** The distinct colours in a pattern, in the order they are first picked. */
    public static List<String> coloursIn(List<?> pattern) {
        List<String> seen = new ArrayList<>();
        for (Object entry : pattern) {
            String colour = entry instanceof Json.Str json ? json.value() : String.valueOf(entry);
            if (!seen.contains(colour)) {
                seen.add(colour);
            }
        }
        return List.copyOf(seen);
    }

    /**
     * Allot a box to each colour.
     *
     * <p>The colour picked most goes in box one. Box one is the box the motion rests in, so the
     * commonest weft is the one that costs no lift, and the chain works hardest for the colours that
     * appear least. Ties fall back to the order the colours first appear, so the allotment is stable
     * for a given pattern.
     */
    public static List<Allotment> allotBoxes(List<?> pattern, int boxes) {
        List<String> picks = colours(pattern);
        List<String> colours = coloursIn(picks);
        Check.positiveInteger(boxes, "boxes");
        if (colours.size() > boxes) {
            throw new CapacityException(
                    colours.size() + " wefts will not go in " + boxes + " boxes",
                    Json.obj("colours", colours, "boxes", boxes));
        }
        List<Allotment> counted = new ArrayList<>();
        for (String colour : colours) {
            counted.add(new Allotment(
                    colour, 0, (int) picks.stream().filter(colour::equals).count()));
        }
        counted.sort(Comparator.comparingInt(Allotment::picks).reversed());
        List<Allotment> out = new ArrayList<>(counted.size());
        for (int index = 0; index < counted.size(); index += 1) {
            Allotment entry = counted.get(index);
            out.add(new Allotment(entry.colour(), index + 1, entry.picks()));
        }
        return List.copyOf(out);
    }

    /** The box each pick is taken from, one entry per pick. */
    public static List<Integer> boxChain(List<?> pattern, List<Allotment> allotment) {
        Check.array(allotment, "allotment", 1);
        Map<String, Integer> byColour = new HashMap<>();
        allotment.forEach(entry -> byColour.put(entry.colour(), entry.box()));
        List<Integer> chain = new ArrayList<>(pattern.size());
        for (int index = 0; index < pattern.size(); index += 1) {
            Object entry = pattern.get(index);
            String colour = entry instanceof Json.Str json ? json.value() : String.valueOf(entry);
            Integer box = byColour.get(colour);
            if (box == null) {
                throw new InvalidArgumentException(
                        "pick " + (index + 1) + " wants " + colour + ", which has no box",
                        Json.obj("pick", index + 1, "colour", colour));
            }
            chain.add(box);
        }
        return List.copyOf(chain);
    }

    /** How many times the box has to lift over one repeat of the chain, read as a loop. */
    public static int boxChanges(List<Integer> chain) {
        return boxChanges(chain, true);
    }

    /** How many times the box has to lift, read as a loop or straight through. */
    public static int boxChanges(List<Integer> chain, boolean cyclic) {
        Check.array(chain, "chain", 1);
        int changes = 0;
        for (int i = 1; i < chain.size(); i += 1) {
            if (!chain.get(i).equals(chain.get(i - 1))) {
                changes += 1;
            }
        }
        if (cyclic && chain.size() > 1 && !chain.get(0).equals(chain.get(chain.size() - 1))) {
            changes += 1;
        }
        return changes;
    }

    /** The furthest the box has to travel in one change, read as a loop. */
    public static int longestLift(List<Integer> chain) {
        return longestLift(chain, true);
    }

    /** The furthest the box has to travel in one change, in box positions. */
    public static int longestLift(List<Integer> chain, boolean cyclic) {
        Check.array(chain, "chain", 1);
        int worst = 0;
        for (int i = 1; i < chain.size(); i += 1) {
            worst = Math.max(worst, Math.abs(chain.get(i) - chain.get(i - 1)));
        }
        if (cyclic && chain.size() > 1) {
            worst = Math.max(worst, Math.abs(chain.get(0) - chain.get(chain.size() - 1)));
        }
        return worst;
    }

    /**
     * The runs a loom boxed at one end cannot weave.
     *
     * <p>An odd run leaves its shuttle on the far side. The first run is exempt when the pattern is
     * read as a repeat and the run before it carries the shuttle back, but that is a judgement about
     * the whole chain and not about one run, so every odd run is reported and the caller decides.
     */
    public static List<WeftRun> oddRuns(List<?> pattern) {
        return weftRuns(pattern).stream().filter(run -> run.picks() % 2 == 1).toList();
    }

    /** Whether a pattern can be woven on a loom boxed at both ends, which is always. */
    public static boolean shuttlesReturn(List<?> pattern) {
        return shuttlesReturn(pattern, Boxing.BOTH);
    }

    /** Whether a pattern can be woven on a loom boxed the way it is boxed. */
    public static boolean shuttlesReturn(List<?> pattern, Boxing boxedAt) {
        Check.required(boxedAt, "boxedAt");
        return boxedAt == Boxing.BOTH || oddRuns(pattern).isEmpty();
    }

    /** Refuse a pattern the loom's boxing cannot pick, naming the runs at fault. */
    public static <T extends List<?>> T assertShuttlesReturn(T pattern, Boxing boxedAt) {
        if (shuttlesReturn(pattern, boxedAt)) {
            return pattern;
        }
        throw new CapacityException(
                "boxed at one end, so every weft must be picked in pairs",
                Json.obj("boxedAt", boxedAt, "runs", oddRuns(pattern)));
    }

    /** The picks a repeat loses to box changing, at a tenth of a pick a change. */
    public static double picksLostToChanging(List<Integer> chain) {
        return picksLostToChanging(chain, 0.1);
    }

    /**
     * The picks a repeat loses to box changing.
     *
     * <p>A change is quoted as a fraction of a pick because the motion works in the same crank
     * revolution as the pick it precedes. A tenth is the usual figure for a well set drop box; a
     * worn one costs a good deal more.
     */
    public static double picksLostToChanging(List<Integer> chain, double picksPerChange) {
        if (!(picksPerChange >= 0)) {
            throw new InvalidArgumentException("picksPerChange must be zero or more", Json.obj("got", picksPerChange));
        }
        return Numeric.roundTo(boxChanges(chain) * picksPerChange, 3);
    }

    /** The running speed a chain leaves at a tenth of a pick a change. */
    public static double speedWithChanging(List<Integer> chain, double picksMinute) {
        return speedWithChanging(chain, picksMinute, 0.1);
    }

    /** The running speed a chain leaves, given the loom's quoted speed. */
    public static double speedWithChanging(List<Integer> chain, double picksMinute, double picksPerChange) {
        // A register holds a quoted speed that need not be whole: a loom geared at 217.5 picks a
        // minute is a real entry and not a typing mistake.
        Check.positiveNumber(picksMinute, "picksMinute");
        double lost = picksLostToChanging(chain, picksPerChange);
        int perRepeat = chain.size();
        return Numeric.roundTo((picksMinute * perRepeat) / (perRepeat + lost), 1);
    }

    /** How the picks of a repeat divide between the boxes. */
    public static List<JsonObject> pickShare(List<Allotment> allotment) {
        Check.array(allotment, "allotment", 1);
        double total = Numeric.sumBy(allotment, Allotment::picks, "allotment");
        return Group.tally(
                        allotment,
                        Group.<Allotment>by(entry -> "box " + entry.box())
                                .label("box")
                                .field("picks", Allotment::picks))
                .stream()
                .map(row -> row.with("share", Numeric.roundTo(row.get("picks").asDouble() / total, 4)))
                .toList();
    }

    /**
     * Turn a box chain back into the pattern it picks, for reading a chain off a loom that is already
     * gaited and checking it against the order.
     */
    public static List<String> patternFromChain(List<?> chain, List<Allotment> allotment) {
        List<Object> boxes = Check.array(chain, "chain", 1);
        Map<Integer, String> byBox = new HashMap<>();
        allotment.forEach(entry -> byBox.put(entry.box(), entry.colour()));
        List<String> out = new ArrayList<>(boxes.size());
        for (int index = 0; index < boxes.size(); index += 1) {
            int box = Check.integer(boxes.get(index), "chain[" + index + "]", Bounds.atLeast(1));
            String colour = byBox.get(box);
            if (colour == null) {
                throw new InvalidArgumentException(
                        "chain[" + index + "] names box " + box + ", which is empty", Json.obj("box", box));
            }
            out.add(colour);
        }
        return List.copyOf(out);
    }
}
