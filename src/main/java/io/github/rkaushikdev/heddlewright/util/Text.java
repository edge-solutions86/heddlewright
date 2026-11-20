package io.github.rkaushikdev.heddlewright.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Text the way the shed has always printed it.
 *
 * <p>The figures on a docket, in an error message and in a JSON body were all written by the
 * original JavaScript, so a whole number prints without a decimal point, a fraction prints in its
 * shortest exact form, and {@code toFixed} rounds the binary value rather than its decimal echo.
 * Every number that reaches text goes through here so the bytes do not change.
 */
public final class Text {

    private Text() {}

    /** A number as JavaScript's {@code String(n)} writes it: 3, 0.5, 1e+21, -0.000001, 1e-7. */
    public static String number(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (value == 0) {
            return "0";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        if (value < 0) {
            return "-" + number(-value);
        }
        BigDecimal exact = new BigDecimal(Double.toString(value)).stripTrailingZeros();
        String digits = exact.unscaledValue().toString();
        int k = digits.length();
        int n = k - exact.scale();
        if (k <= n && n <= 21) {
            return digits + "0".repeat(n - k);
        }
        if (0 < n && n <= 21) {
            return digits.substring(0, n) + "." + digits.substring(n);
        }
        if (-6 < n && n <= 0) {
            return "0." + "0".repeat(-n) + digits;
        }
        String exponent = (n - 1 >= 0 ? "+" : "-") + Math.abs(n - 1);
        if (k == 1) {
            return digits + "e" + exponent;
        }
        return digits.charAt(0) + "." + digits.substring(1) + "e" + exponent;
    }

    /** JavaScript's {@code toFixed}: the exact binary value, rounded half away from zero. */
    public static String fixed(double value, int places) {
        if (!Double.isFinite(value) || Math.abs(value) >= 1e21) {
            return number(value);
        }
        if (value < 0) {
            return "-" + fixed(-value, places);
        }
        return new BigDecimal(value).setScale(places, RoundingMode.HALF_UP).toPlainString();
    }

    /** JavaScript's {@code String(value)} over a JSON value or a plain Java one. */
    public static String string(Object value) {
        Json json = Json.of(value);
        return switch (json) {
            case Json.Str text -> text.value();
            case Json.Num num -> number(num.value());
            case JsonArray array ->
                String.join(
                        ",",
                        array.stream()
                                .map(item -> item.isNullish() ? "" : string(item))
                                .toList());
            case JsonObject ignored -> "[object Object]";
            default -> json.toString();
        };
    }

    /** Pad on the left with spaces to a width, leaving a longer text alone. */
    public static String padStart(String text, int width) {
        return padStart(text, width, ' ');
    }

    /** Pad on the left with a character to a width, leaving a longer text alone. */
    public static String padStart(String text, int width, char fill) {
        int missing = width - text.length();
        return missing <= 0 ? text : String.valueOf(fill).repeat(missing) + text;
    }

    /** Pad on the right with spaces to a width, leaving a longer text alone. */
    public static String padEnd(String text, int width) {
        int missing = width - text.length();
        return missing <= 0 ? text : text + " ".repeat(missing);
    }

    /** Whether a character is one JavaScript's {@code trim} and {@code \s} treat as white space. */
    public static boolean isSpace(char c) {
        return switch (c) {
            case 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x20, 0xa0, 0x1680, 0x2028, 0x2029, 0x202f, 0x205f, 0x3000, 0xfeff ->
                true;
            default -> c >= 0x2000 && c <= 0x200a;
        };
    }

    /** JavaScript's {@code trim}. */
    public static String trim(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.charAt(start))) {
            start += 1;
        }
        while (end > start && isSpace(text.charAt(end - 1))) {
            end -= 1;
        }
        return text.substring(start, end);
    }

    /** JavaScript's {@code trimEnd}. */
    public static String trimEnd(String text) {
        int end = text.length();
        while (end > 0 && isSpace(text.charAt(end - 1))) {
            end -= 1;
        }
        return text.substring(0, end);
    }

    /** JavaScript's {@code split} on a literal separator, keeping the empty parts. */
    public static List<String> split(String text, String separator) {
        List<String> parts = new ArrayList<>();
        if (separator.isEmpty()) {
            for (int i = 0; i < text.length(); i += 1) {
                parts.add(String.valueOf(text.charAt(i)));
            }
            return parts;
        }
        int from = 0;
        int found = text.indexOf(separator, from);
        while (found >= 0) {
            parts.add(text.substring(from, found));
            from = found + separator.length();
            found = text.indexOf(separator, from);
        }
        parts.add(text.substring(from));
        return parts;
    }

    /** Split on runs of JavaScript white space, dropping the empty ends, as {@code split(/\s+/)} with a filter does. */
    public static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < text.length(); i += 1) {
            char c = text.charAt(i);
            if (isSpace(c)) {
                if (!word.isEmpty()) {
                    words.add(word.toString());
                    word.setLength(0);
                }
            } else {
                word.append(c);
            }
        }
        if (!word.isEmpty()) {
            words.add(word.toString());
        }
        return words;
    }

    /** JavaScript's {@code Number(text)}: trimmed, empty is zero, anything else unreadable is NaN. */
    public static double toNumber(String text) {
        String trimmed = trim(text);
        if (trimmed.isEmpty()) {
            return 0;
        }
        switch (trimmed) {
            case "Infinity", "+Infinity" -> {
                return Double.POSITIVE_INFINITY;
            }
            case "-Infinity" -> {
                return Double.NEGATIVE_INFINITY;
            }
            default -> {
                // Handled below.
            }
        }
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        try {
            if (lower.startsWith("0x")) {
                return Long.parseLong(trimmed.substring(2), 16);
            }
            if (lower.startsWith("0o")) {
                return Long.parseLong(trimmed.substring(2), 8);
            }
            if (lower.startsWith("0b")) {
                return Long.parseLong(trimmed.substring(2), 2);
            }
        } catch (NumberFormatException err) {
            return Double.NaN;
        }
        if (!trimmed.matches("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")) {
            return Double.NaN;
        }
        return Double.parseDouble(trimmed);
    }

    /** JavaScript's {@code Number.parseInt(text, 10)}: leading digits after white space, else NaN. */
    public static double parseInt(String text) {
        String trimmed = trim(text);
        int at = 0;
        boolean negative = false;
        if (at < trimmed.length() && (trimmed.charAt(at) == '-' || trimmed.charAt(at) == '+')) {
            negative = trimmed.charAt(at) == '-';
            at += 1;
        }
        int start = at;
        while (at < trimmed.length() && trimmed.charAt(at) >= '0' && trimmed.charAt(at) <= '9') {
            at += 1;
        }
        if (at == start) {
            return Double.NaN;
        }
        double value = Double.parseDouble(trimmed.substring(start, at));
        return negative ? -value : value;
    }
}
