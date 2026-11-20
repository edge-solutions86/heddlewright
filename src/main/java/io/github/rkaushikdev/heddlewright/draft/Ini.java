package io.github.rkaushikdev.heddlewright.draft;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The keyed-section text format that weaving files are written in.
 *
 * <p>A file is a run of {@code [SECTION]} headers, each holding {@code key=value} lines. Comments
 * start with a semicolon. Section names and keys are compared without regard to case, because
 * thirty years of files have written them every way, but the values are kept exactly as they were
 * typed. A parsed file is a map of section name to a map of keys, both in the order the file
 * gave them.
 */
public final class Ini {

    private static final Pattern SECTION = Pattern.compile("^\\[(.+)\\]$");
    private static final Pattern WHOLE = Pattern.compile("^-?\\d+$");
    private static final Pattern NUMBERED = Pattern.compile("^\\d+$");
    private static final Pattern YES = Pattern.compile("^(yes|true|on|1)$", Pattern.CASE_INSENSITIVE);

    private Ini() {}

    /** Parse the whole file into a map of section name to a map of keys. */
    public static Map<String, Map<String, String>> parseIni(String text) {
        if (text == null) {
            throw new InvalidArgumentException("parseIni needs a string", Json.obj("got", "object"));
        }
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        String current = null;
        String[] lines = text.split("\r?\n", -1);
        for (int index = 0; index < lines.length; index += 1) {
            String line = Text.trim(stripComment(lines[index]));
            if (line.isEmpty()) {
                continue;
            }
            Matcher header = SECTION.matcher(line);
            if (header.matches()) {
                current = upper(Text.trim(header.group(1)));
                sections.computeIfAbsent(current, name -> new LinkedHashMap<>());
                continue;
            }
            int split = line.indexOf('=');
            if (split == -1) {
                throw new InvalidArgumentException(
                        "line " + (index + 1) + " is neither a section nor a key",
                        Json.obj("line", index + 1, "text", line));
            }
            if (current == null) {
                throw new InvalidArgumentException(
                        "line " + (index + 1) + " sits outside any section", Json.obj("line", index + 1));
            }
            sections.get(current).put(upper(Text.trim(line.substring(0, split))), Text.trim(line.substring(split + 1)));
        }
        Map<String, Map<String, String>> frozen = new LinkedHashMap<>();
        sections.forEach((name, entries) -> frozen.put(name, Collections.unmodifiableMap(entries)));
        return Collections.unmodifiableMap(frozen);
    }

    private static String stripComment(String line) {
        int at = line.indexOf(';');
        return at == -1 ? line : line.substring(0, at);
    }

    private static String upper(String text) {
        return text.toUpperCase(Locale.ROOT);
    }

    /** True when the file carries that section. */
    public static boolean hasSection(Map<String, Map<String, String>> sections, String name) {
        Check.nonEmptyString(name, "name");
        return sections.containsKey(upper(name));
    }

    /** A section, or an empty map when the file does not carry it. */
    public static Map<String, String> section(Map<String, Map<String, String>> sections, String name) {
        Check.nonEmptyString(name, "name");
        return sections.getOrDefault(upper(name), Map.of());
    }

    /** One value, or empty when the key is missing. */
    public static Optional<String> value(Map<String, Map<String, String>> sections, String name, String key) {
        Check.nonEmptyString(key, "key");
        return Optional.ofNullable(section(sections, name).get(upper(key)));
    }

    /** One value, with a fallback when the key is missing. */
    public static String value(Map<String, Map<String, String>> sections, String name, String key, String fallback) {
        return value(sections, name, key).orElse(fallback);
    }

    /** A value read as an integer, or empty when it is missing or unreadable. */
    public static OptionalInt intValue(Map<String, Map<String, String>> sections, String name, String key) {
        Optional<String> raw = value(sections, name, key);
        if (raw.isEmpty() || !WHOLE.matcher(raw.get()).matches()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(raw.get()));
    }

    /** A value read as an integer, or the fallback when it is missing or unreadable. */
    public static int intValue(Map<String, Map<String, String>> sections, String name, String key, int fallback) {
        return intValue(sections, name, key).orElse(fallback);
    }

    /** A value read as a yes or no flag, false when it is missing. */
    public static boolean boolValue(Map<String, Map<String, String>> sections, String name, String key) {
        return boolValue(sections, name, key, false);
    }

    /** A value read as a yes or no flag. */
    public static boolean boolValue(
            Map<String, Map<String, String>> sections, String name, String key, boolean fallback) {
        Optional<String> raw = value(sections, name, key);
        return raw.map(text -> isYes(text)).orElse(fallback);
    }

    static boolean isYes(String text) {
        return YES.matcher(Text.trim(text)).matches();
    }

    /** A comma-separated value read as a list of integers. */
    public static List<Integer> intListValue(Map<String, Map<String, String>> sections, String name, String key) {
        Optional<String> raw = value(sections, name, key);
        if (raw.isEmpty() || Text.trim(raw.get()).isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (String piece : Text.split(raw.get(), ",")) {
            String part = Text.trim(piece);
            if (part.isEmpty()) {
                continue;
            }
            if (!WHOLE.matcher(part).matches()) {
                throw new InvalidArgumentException(
                        "\"" + part + "\" in " + name + "." + key + " is not a whole number",
                        Json.obj("section", name, "key", key, "part", part));
            }
            out.add(Integer.parseInt(part));
        }
        return List.copyOf(out);
    }

    /**
     * A numbered section read as a dense list. Weaving files key threadings and treadlings by
     * 1-based thread number, and they are allowed to be sparse, so anything missing is empty.
     */
    public static List<Optional<Integer>> numberedList(
            Map<String, Map<String, String>> sections, String name, int count) {
        List<Optional<Integer>> out = new ArrayList<>(Collections.nCopies(count, Optional.empty()));
        for (Map.Entry<String, String> entry : numbered(sections, name, count)) {
            out.set(Integer.parseInt(entry.getKey()) - 1, Optional.of((int) Text.parseInt(entry.getValue())));
        }
        return List.copyOf(out);
    }

    /** A numbered section read as a dense list of integers, with the gaps filled. */
    public static List<Integer> numberedList(
            Map<String, Map<String, String>> sections, String name, int count, int fill) {
        return numberedList(sections, name, count).stream()
                .map(at -> at.orElse(fill))
                .toList();
    }

    /** A numbered section whose values are comma separated lists, with the gaps left empty. */
    public static List<List<Integer>> numberedLists(Map<String, Map<String, String>> sections, String name, int count) {
        List<List<Integer>> out = new ArrayList<>(Collections.nCopies(count, List.of()));
        for (Map.Entry<String, String> entry : numbered(sections, name, count)) {
            out.set(Integer.parseInt(entry.getKey()) - 1, intListValue(sections, name, entry.getKey()));
        }
        return List.copyOf(out);
    }

    private static List<Map.Entry<String, String>> numbered(
            Map<String, Map<String, String>> sections, String name, int count) {
        List<Map.Entry<String, String>> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : section(sections, name).entrySet()) {
            if (!NUMBERED.matcher(entry.getKey()).matches()) {
                continue;
            }
            int at = Integer.parseInt(entry.getKey());
            if (at >= 1 && at <= count) {
                out.add(entry);
            }
        }
        return out;
    }

    /** Write a map of maps back out as text, sections in their order. */
    public static String formatIni(Map<String, Map<String, String>> sections) {
        List<String> blocks = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> section : sections.entrySet()) {
            List<String> lines = new ArrayList<>();
            lines.add("[" + section.getKey() + "]");
            section.getValue().forEach((key, entry) -> lines.add(key + "=" + entry));
            blocks.add(String.join("\n", lines));
        }
        return String.join("\n\n", blocks) + "\n";
    }

    /** Build a map of maps from a plain object, for callers writing a file. */
    public static Map<String, Map<String, String>> iniFromObject(Object source) {
        JsonObject object = Check.plainObject(source, "source");
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        for (Map.Entry<String, Json> section : object) {
            JsonObject entries = Check.plainObject(section.getValue(), "source." + section.getKey());
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, Json> entry : entries) {
                values.put(entry.getKey(), Text.string(entry.getValue()));
            }
            sections.put(section.getKey(), Collections.unmodifiableMap(values));
        }
        return Collections.unmodifiableMap(sections);
    }
}
