package io.github.rkaushikdev.heddlewright.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Column-aligned plain text tables for the command line and for printed shed dockets.
 *
 * <p>No colour, and every column measured before anything is written, so a long value widens the
 * column instead of breaking the row. A row is any value with a JSON form: a record, a map or a
 * {@link JsonObject}, read by the column's key.
 */
public final class Table {

    /** Which side of its column a value sits on. */
    public enum Align {
        /** Padded on the right. */
        LEFT,
        /** Padded on the left. */
        RIGHT;

        /** Read an alignment by name, refusing anything else with the list of the ones it knows. */
        public static Align parse(String text) {
            return Check.member(text, "align", values());
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One column: the key it reads, the label over it, and its alignment. Without an alignment a
     * column is right aligned when every value under it is a number, and left aligned otherwise.
     */
    public record Column(String key, String label, Optional<Align> align) {

        /** A column labelled with its key. */
        public static Column of(String key) {
            return new Column(key, key, Optional.empty());
        }

        /** The same column under another label. */
        public Column labelled(String text) {
            return new Column(key, text, align);
        }

        /** The same column with a fixed alignment. */
        public Column aligned(Align side) {
            return new Column(key, label, Optional.of(side));
        }
    }

    private Table() {}

    /** Render rows under the given columns, two spaces apart, with a rule under the header. */
    public static String renderTable(List<?> rows, List<Column> columns) {
        return renderTable(rows, columns, 2, true);
    }

    /** Render rows under the given columns with a chosen gap and an optional rule. */
    public static String renderTable(List<?> rows, List<Column> columns, int pad, boolean rule) {
        Check.array(rows, "rows");
        Check.array(columns, "columns", 1);
        List<JsonObject> records = new ArrayList<>(rows.size());
        for (Object row : rows) {
            records.add(Json.of(row) instanceof JsonObject object ? object : JsonObject.EMPTY);
        }
        List<Align> aligns = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i += 1) {
            Column column = Check.required(columns.get(i), "columns[" + i + "]");
            boolean numeric = !records.isEmpty()
                    && records.stream().allMatch(row -> row.get(column.key()).isNumber());
            aligns.add(column.align().orElse(numeric ? Align.RIGHT : Align.LEFT));
        }

        List<List<String>> cells = new ArrayList<>(records.size());
        for (JsonObject row : records) {
            cells.add(columns.stream()
                    .map(column -> formatCell(row.get(column.key())))
                    .toList());
        }
        int[] widths = new int[columns.size()];
        for (int c = 0; c < columns.size(); c += 1) {
            widths[c] = columns.get(c).label().length();
            for (List<String> line : cells) {
                widths[c] = Math.max(widths[c], line.get(c).length());
            }
        }
        String gap = " ".repeat(Math.max(0, pad));

        List<String> out = new ArrayList<>();
        List<String> header = new ArrayList<>();
        for (int c = 0; c < columns.size(); c += 1) {
            header.add(alignCell(columns.get(c).label(), widths[c], aligns.get(c)));
        }
        out.add(String.join(gap, header));
        if (rule) {
            List<String> dashes = new ArrayList<>();
            for (int width : widths) {
                dashes.add("-".repeat(width));
            }
            out.add(String.join(gap, dashes));
        }
        for (List<String> line : cells) {
            List<String> aligned = new ArrayList<>();
            for (int c = 0; c < line.size(); c += 1) {
                aligned.add(alignCell(line.get(c), widths[c], aligns.get(c)));
            }
            out.add(String.join(gap, aligned));
        }
        return String.join("\n", out.stream().map(Text::trimEnd).toList());
    }

    /** One cell as the table writes it: whole numbers plain, fractions to four places, lists spaced. */
    static String formatCell(Json value) {
        return switch (value) {
            case Json.Null ignored -> "";
            case Json.Missing ignored -> "";
            case Json.Num number ->
                number.value() == Math.rint(number.value())
                        ? Text.number(number.value())
                        : Text.number(Double.parseDouble(Text.fixed(number.value(), 4)));
            case JsonArray array ->
                String.join(
                        " ",
                        array.stream()
                                .map(item -> item.isNullish() ? "" : Text.string(item))
                                .toList());
            default -> Text.string(value);
        };
    }

    private static String alignCell(String text, int width, Align align) {
        return align == Align.RIGHT ? Text.padStart(text, width) : Text.padEnd(text, width);
    }

    /** A two-column label and value block, used for single-record summaries. */
    public static String renderPairs(Object pairs) {
        return renderPairs(pairs, ": ");
    }

    /** A two-column label and value block with a chosen separator. */
    public static String renderPairs(Object pairs, String separator) {
        Map<String, Json> entries = new LinkedHashMap<>();
        if (pairs != null && Json.of(pairs) instanceof JsonObject object) {
            entries.putAll(object.asMap());
        }
        if (entries.isEmpty()) {
            return "";
        }
        int width = 0;
        for (String label : entries.keySet()) {
            width = Math.max(width, label.length());
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Json> entry : entries.entrySet()) {
            lines.add(Text.padEnd(entry.getKey(), width) + separator + formatCell(entry.getValue()));
        }
        return String.join("\n", lines);
    }

    /** Wrap prose to 78 columns without breaking inside a word. */
    public static String wrapText(String text) {
        return wrapText(text, 78);
    }

    /** Wrap prose to a column width without breaking inside a word. */
    public static String wrapText(String text, int width) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : Text.words(String.valueOf(text))) {
            if (line.isEmpty()) {
                line = word;
            } else if (line.length() + 1 + word.length() <= width) {
                line = line + " " + word;
            } else {
                lines.add(line);
                line = word;
            }
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        return String.join("\n", lines);
    }
}
