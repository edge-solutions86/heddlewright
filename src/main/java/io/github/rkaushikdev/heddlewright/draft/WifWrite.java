package io.github.rkaushikdev.heddlewright.draft;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Writing a weaving interchange file.
 *
 * <p>The contents section is the part that matters and the part most writers get wrong: it
 * promises what the file holds, and a promise the file does not keep makes every reader guess.
 * Here the contents are worked out from the draft rather than declared by the caller, so the two
 * cannot drift apart.
 */
public final class WifWrite {

    /** Who wrote the file, any notes to put in place of the draft's own, and the date to stamp. */
    public record Options(String author, String notes, Optional<String> date) {

        /** No author, the draft's own notes, and an unspecified date. */
        public static final Options DEFAULT = new Options("", "", Optional.empty());

        /** The same options with an author. */
        public Options author(String name) {
            return new Options(name, notes, date);
        }

        /** The same options with notes. */
        public Options notes(String text) {
            return new Options(author, text, date);
        }

        /** The same options with a date. */
        public Options date(String day) {
            return new Options(author, notes, Optional.of(day));
        }
    }

    private static final String PROGRAM = "heddlewright";

    private WifWrite() {}

    /** Write a draft out as a weaving file. */
    public static String writeWif(Draft draft) {
        return writeWif(draft, Options.DEFAULT);
    }

    /** Write a draft out as a weaving file, with an author, notes or a date. */
    public static String writeWif(Draft draft, Options options) {
        Check.object(draft, "draft");
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        sections.put(
                "WIF",
                ordered(
                        "Version",
                        Wif.WIF_VERSION,
                        "Date",
                        options.date().orElse("unspecified"),
                        "Developers",
                        PROGRAM,
                        "Source Program",
                        PROGRAM));

        Map<String, String> contents = new LinkedHashMap<>();
        for (String name : List.of("TEXT", "WEAVING", "WARP", "WEFT", "THREADING", "TIEUP", "TREADLING")) {
            contents.put(name, "yes");
        }
        sections.put("CONTENTS", contents);
        sections.put(
                "TEXT",
                ordered(
                        "Title", draft.name(),
                        "Author", options.author(),
                        "Notes", options.notes().isEmpty() ? draft.notes() : options.notes()));
        sections.put(
                "WEAVING",
                ordered(
                        "Shafts", String.valueOf(draft.shafts()),
                        "Treadles", String.valueOf(draft.treadles()),
                        "Rising Shed", draft.shed() == Shed.RISING ? "yes" : "no"));
        sections.put("WARP", ordered("Threads", String.valueOf(draft.threading().size())));
        sections.put("WEFT", ordered("Threads", String.valueOf(draft.treadling().size())));
        sections.put(
                "THREADING",
                numbered(draft.threading().stream().map(String::valueOf).toList()));
        sections.put(
                "TIEUP", numbered(draft.tieup().stream().map(WifWrite::joined).toList()));
        sections.put(
                "TREADLING",
                numbered(draft.treadling().stream().map(WifWrite::joined).toList()));
        return Ini.formatIni(sections);
    }

    private static Map<String, String> ordered(String... keysAndValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static Map<String, String> numbered(List<String> values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int index = 0; index < values.size(); index += 1) {
            map.put(String.valueOf(index + 1), values.get(index));
        }
        return map;
    }

    private static String joined(List<Integer> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** Write a dobby draft with its liftplan in place of the tie-up and treadling. */
    public static String writeDobbyWif(Draft draft, Matrix liftplan) {
        return writeDobbyWif(draft, liftplan, Options.DEFAULT);
    }

    /**
     * Write a dobby draft with a liftplan instead of a tie-up and treadling, which is what a loom
     * with no treadles wants to read.
     */
    public static String writeDobbyWif(Draft draft, Matrix liftplan, Options options) {
        String withoutTreadles = writeWif(draft, options)
                .replaceFirst("\\[TIEUP\\][\\s\\S]*?(?=\\n\\[|\\z)", "")
                .replaceFirst("\\[TREADLING\\][\\s\\S]*?(?=\\n\\[|\\z)", "")
                .replaceFirst("TIEUP=yes\\n", "")
                .replaceFirst("TREADLING=yes\\n", "LIFTPLAN=yes\n")
                .replaceAll("\\n{3,}", "\n\n");
        List<String> lines = new ArrayList<>(liftplan.rows());
        for (int index = 0; index < liftplan.rows(); index += 1) {
            List<String> lifted = new ArrayList<>();
            for (int shaft = 0; shaft < liftplan.cols(); shaft += 1) {
                if (liftplan.isSet(index, shaft)) {
                    lifted.add(String.valueOf(shaft + 1));
                }
            }
            lines.add((index + 1) + "=" + String.join(",", lifted));
        }
        return Text.trimEnd(withoutTreadles) + "\n\n[LIFTPLAN]\n" + String.join("\n", lines) + "\n";
    }

    /** The sections a written file will carry, for a caller checking before writing. */
    public static List<String> contentsFor(Draft draft) {
        Check.object(draft, "draft");
        List<String> carried = new ArrayList<>(List.of("TEXT", "WEAVING", "WARP", "WEFT", "THREADING"));
        if (!draft.tieup().isEmpty()) {
            carried.add("TIEUP");
        }
        if (!draft.treadling().isEmpty()) {
            carried.add("TREADLING");
        }
        return List.copyOf(carried);
    }
}
