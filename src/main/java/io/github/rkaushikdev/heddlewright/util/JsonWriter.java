package io.github.rkaushikdev.heddlewright.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes {@link Json} out as RFC 8259 text in a fixed form: keys in the order the object holds
 * them, numbers as {@link Text#number} writes them, non-finite numbers and absent array items as
 * null, absent object fields left out, and only the characters {@link #quote} lists escaped. The
 * canonical form, with keys sorted, is what the content ids and the journal hash, so its bytes
 * never vary.
 */
final class JsonWriter {

    private JsonWriter() {}

    /** The value as JSON text, indented by that many spaces a level; an indent of zero is the compact form. */
    static String write(Json value, int indent) {
        StringBuilder out = new StringBuilder();
        if (value.isMissing()) {
            return "undefined";
        }
        write(value, indent, "", out);
        return out.toString();
    }

    private static void write(Json value, int indent, String margin, StringBuilder out) {
        switch (value) {
            case Json.Null ignored -> out.append("null");
            case Json.Missing ignored -> out.append("null");
            case Json.Bool bool -> out.append(bool.value());
            case Json.Num number -> out.append(Double.isFinite(number.value()) ? Text.number(number.value()) : "null");
            case Json.Str text -> out.append(quote(text.value()));
            case JsonArray array -> {
                if (array.isEmpty()) {
                    out.append("[]");
                    return;
                }
                String inner = margin + " ".repeat(indent);
                out.append('[');
                boolean first = true;
                for (Json item : array) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    breakLine(indent, inner, out);
                    write(item, indent, inner, out);
                }
                breakLine(indent, margin, out);
                out.append(']');
            }
            case JsonObject object -> {
                if (object.isEmpty()) {
                    out.append("{}");
                    return;
                }
                String inner = margin + " ".repeat(indent);
                out.append('{');
                boolean first = true;
                for (Map.Entry<String, Json> entry : object) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    breakLine(indent, inner, out);
                    out.append(quote(entry.getKey())).append(indent > 0 ? ": " : ":");
                    write(entry.getValue(), indent, inner, out);
                }
                breakLine(indent, margin, out);
                out.append('}');
            }
        }
    }

    private static void breakLine(int indent, String margin, StringBuilder out) {
        if (indent > 0) {
            out.append('\n').append(margin);
        }
    }

    /** The canonical form: keys sorted, no whitespace, and nothing JSON cannot carry honestly. */
    static String canonical(Json value) {
        StringBuilder out = new StringBuilder();
        canonical(value, out);
        return out.toString();
    }

    private static void canonical(Json value, StringBuilder out) {
        switch (value) {
            case Json.Null ignored -> out.append("null");
            case Json.Missing ignored ->
                throw new InvalidArgumentException("canonical JSON cannot hold a undefined", JsonObject.EMPTY);
            case Json.Bool bool -> out.append(bool.value());
            case Json.Num number -> {
                if (!Double.isFinite(number.value())) {
                    throw new InvalidArgumentException(
                            "canonical JSON cannot hold a non-finite number", Json.obj("got", number));
                }
                out.append(Text.number(number.value()));
            }
            case Json.Str text -> out.append(quote(text.value()));
            case JsonArray array -> {
                out.append('[');
                boolean first = true;
                for (Json item : array) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    canonical(item, out);
                }
                out.append(']');
            }
            case JsonObject object -> {
                List<String> keys = new ArrayList<>(object.keys());
                keys.sort(null);
                out.append('{');
                boolean first = true;
                for (String key : keys) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    out.append(quote(key)).append(':');
                    canonical(object.get(key), out);
                }
                out.append('}');
            }
        }
    }

    /**
     * A string as a JSON string literal: quote, backslash, the control characters and any unpaired
     * surrogate escaped, the common ones by their short escapes and the rest as backslash-u and four
     * hex digits, and every other character as itself.
     */
    static String quote(String text) {
        StringBuilder out = new StringBuilder(text.length() + 2);
        out.append('"');
        for (int i = 0; i < text.length(); i += 1) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || isLoneSurrogate(text, i)) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static boolean isLoneSurrogate(String text, int at) {
        char c = text.charAt(at);
        if (Character.isHighSurrogate(c)) {
            return at + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(at + 1));
        }
        if (Character.isLowSurrogate(c)) {
            return at == 0 || !Character.isHighSurrogate(text.charAt(at - 1));
        }
        return false;
    }
}
