package io.github.rkaushikdev.heddlewright.draft;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Reading a weaving interchange file.
 *
 * <p>The format keeps the draft in four numbered sections: the threading by end, the tie-up by
 * treadle, the treadling by pick and, on a dobby draft, a liftplan by pick instead of the last two.
 * What it does not keep is any guarantee that those agree with the shaft and treadle counts in the
 * header, so everything is checked on the way in rather than trusted.
 */
public final class Wif {

    /** The version of the format this package writes. */
    public static final String WIF_VERSION = "1.1";

    /** What a file declares, read before any draft is built from it. */
    public record Read(
            String version,
            String program,
            String title,
            String author,
            int shafts,
            OptionalInt treadles,
            boolean risingShed,
            int warpThreads,
            int weftThreads,
            List<Integer> threading,
            List<List<Integer>> tieup,
            List<List<Integer>> treadling,
            List<List<Integer>> liftplan,
            boolean hasTieup,
            boolean hasLiftplan) {

        /** Copies the lists. */
        public Read {
            threading = List.copyOf(threading);
            tieup = List.copyOf(tieup);
            treadling = List.copyOf(treadling);
            liftplan = List.copyOf(liftplan);
        }
    }

    private Wif() {}

    /** Read a file into the pieces it declares, before any draft is built. */
    public static Read readWif(String text) {
        Map<String, Map<String, String>> sections = Ini.parseIni(text);
        if (Ini.section(sections, "WIF").isEmpty()) {
            throw new DraftException("this file has no WIF section, so it is not a weaving file", JsonObject.EMPTY);
        }
        OptionalInt shafts = Ini.intValue(sections, "WEAVING", "Shafts");
        OptionalInt treadles = Ini.intValue(sections, "WEAVING", "Treadles");
        if (shafts.isEmpty()) {
            throw new DraftException("the file does not say how many shafts the draft needs", JsonObject.EMPTY);
        }
        int warpThreads = Ini.intValue(sections, "WARP", "Threads", 0);
        int weftThreads = Ini.intValue(sections, "WEFT", "Threads", 0);
        return new Read(
                Ini.value(sections, "WIF", "Version", ""),
                Ini.value(sections, "WIF", "Source Program", ""),
                Ini.value(sections, "TEXT", "Title", ""),
                Ini.value(sections, "TEXT", "Author", ""),
                shafts.getAsInt(),
                treadles,
                Ini.boolValue(sections, "WEAVING", "Rising Shed", true),
                warpThreads,
                weftThreads,
                Ini.numberedList(sections, "THREADING", warpThreads, 0),
                treadles.isEmpty() ? List.of() : Ini.numberedLists(sections, "TIEUP", treadles.getAsInt()),
                Ini.numberedLists(sections, "TREADLING", weftThreads),
                Ini.numberedLists(sections, "LIFTPLAN", weftThreads),
                !Ini.section(sections, "TIEUP").isEmpty(),
                !Ini.section(sections, "LIFTPLAN").isEmpty());
    }

    /**
     * A weaving file writes an absent title as an empty string as often as it leaves the key out, so
     * a plain fallback picks the empty one up and hands it on to a check that refuses it.
     */
    private static String firstNonEmpty(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !Text.trim(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "untitled";
    }

    /** Turn a file into a draft named by its title, checking everything the header promised. */
    public static Draft draftFromWif(String text) {
        return draftFromWif(text, null);
    }

    /** Turn a file into a draft under the given name, checking everything the header promised. */
    public static Draft draftFromWif(String text, String name) {
        Read read = readWif(text);
        if (read.warpThreads() == 0) {
            throw new DraftException("the file does not say how many warp threads it has", JsonObject.EMPTY);
        }
        List<Integer> missing = new ArrayList<>();
        for (int index = 0; index < read.threading().size(); index += 1) {
            if (read.threading().get(index) == 0) {
                missing.add(index + 1);
            }
        }
        if (!missing.isEmpty()) {
            throw new DraftException(
                    "the threading has ends with no shaft",
                    Json.obj("ends", missing.subList(0, Math.min(10, missing.size())), "missing", missing.size()));
        }
        if (read.hasLiftplan()) {
            return draftFromLiftplanFile(read, name);
        }
        if (read.treadles().isEmpty() || !read.hasTieup()) {
            throw new DraftException("the file has neither a tie-up nor a liftplan", JsonObject.EMPTY);
        }
        return Draft.create(Json.obj(
                "name", firstNonEmpty(name, read.title()),
                "shafts", read.shafts(),
                "treadles", read.treadles().getAsInt(),
                "shed", read.risingShed() ? Shed.RISING : Shed.SINKING,
                "threading", read.threading(),
                "tieup", read.tieup(),
                "treadling", read.treadling(),
                "notes", read.author().isEmpty() ? "" : "after " + read.author()));
    }

    /**
     * A dobby file carries a liftplan instead of a tie-up. Each distinct lift becomes a treadle, in
     * the order the picks first use it.
     */
    private static Draft draftFromLiftplanFile(Read read, String name) {
        Map<List<Integer>, Integer> seen = new LinkedHashMap<>();
        List<List<Integer>> tieup = new ArrayList<>();
        List<List<Integer>> treadling = new ArrayList<>();
        for (List<Integer> lift : read.liftplan()) {
            List<Integer> sorted = lift.stream().sorted().toList();
            Integer treadle = seen.get(sorted);
            if (treadle == null) {
                treadle = tieup.size() + 1;
                seen.put(sorted, treadle);
                tieup.add(sorted);
            }
            treadling.add(List.of(treadle));
        }
        return Draft.create(Json.obj(
                "name", firstNonEmpty(name, read.title()),
                "shafts", read.shafts(),
                "treadles", tieup.size(),
                "shed", read.risingShed() ? Shed.RISING : Shed.SINKING,
                "threading", read.threading(),
                "tieup", tieup,
                "treadling", treadling));
    }

    /** What a file declares it carries, without reading any of it. */
    public static List<String> wifContents(String text) {
        Map<String, Map<String, String>> sections = Ini.parseIni(text);
        List<String> carried = new ArrayList<>();
        for (Map.Entry<String, String> entry : Ini.section(sections, "CONTENTS").entrySet()) {
            if (Ini.isYes(entry.getValue())) {
                carried.add(entry.getKey());
            }
        }
        carried.sort(null);
        return List.copyOf(carried);
    }

    /** True when the file says it carries the section, and actually does. */
    public static boolean declaresAndCarries(String text, String name) {
        Check.nonEmptyString(name, "name");
        Map<String, Map<String, String>> sections = Ini.parseIni(text);
        return Ini.boolValue(sections, "CONTENTS", name, false)
                && !Ini.section(sections, name).isEmpty();
    }
}
