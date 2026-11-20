package io.github.rkaushikdev.heddlewright.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads JSON text into {@link Json}, with the strictness of {@code JSON.parse}. */
final class JsonParser {

    /** The text is not JSON. */
    static final class SyntaxException extends RuntimeException {
        SyntaxException(String message) {
            super(message);
        }
    }

    private final String text;
    private int at;

    JsonParser(String text) {
        this.text = text;
    }

    Json parseDocument() {
        skipSpace();
        Json value = value();
        skipSpace();
        if (at < text.length()) {
            throw unexpected();
        }
        return value;
    }

    private Json value() {
        if (at >= text.length()) {
            throw new SyntaxException("Unexpected end of JSON input");
        }
        char c = text.charAt(at);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> new Json.Str(string());
            case 't' -> literal("true", Json.TRUE);
            case 'f' -> literal("false", Json.FALSE);
            case 'n' -> literal("null", Json.NULL);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield number();
                }
                throw unexpected();
            }
        };
    }

    private Json object() {
        at += 1;
        Map<String, Json> fields = new LinkedHashMap<>();
        skipSpace();
        if (peek() == '}') {
            at += 1;
            return JsonObject.wrap(fields);
        }
        while (true) {
            skipSpace();
            if (peek() != '"') {
                throw unexpected();
            }
            String key = string();
            skipSpace();
            expect(':');
            skipSpace();
            fields.put(key, value());
            skipSpace();
            char next = peek();
            at += 1;
            if (next == '}') {
                return JsonObject.wrap(fields);
            }
            if (next != ',') {
                at -= 1;
                throw unexpected();
            }
        }
    }

    private Json array() {
        at += 1;
        List<Json> items = new ArrayList<>();
        skipSpace();
        if (peek() == ']') {
            at += 1;
            return JsonArray.wrap(items);
        }
        while (true) {
            skipSpace();
            items.add(value());
            skipSpace();
            char next = peek();
            at += 1;
            if (next == ']') {
                return JsonArray.wrap(items);
            }
            if (next != ',') {
                at -= 1;
                throw unexpected();
            }
        }
    }

    private String string() {
        at += 1;
        StringBuilder out = new StringBuilder();
        while (true) {
            if (at >= text.length()) {
                throw new SyntaxException("Unterminated string in JSON at position " + at);
            }
            char c = text.charAt(at);
            at += 1;
            if (c == '"') {
                return out.toString();
            }
            if (c < 0x20) {
                at -= 1;
                throw new SyntaxException("Bad control character in string literal in JSON at position " + at);
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (at >= text.length()) {
                throw new SyntaxException("Unterminated string in JSON at position " + at);
            }
            char escape = text.charAt(at);
            at += 1;
            switch (escape) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (at + 4 > text.length()) {
                        throw new SyntaxException("Bad Unicode escape in JSON at position " + at);
                    }
                    try {
                        out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    } catch (NumberFormatException err) {
                        throw new SyntaxException("Bad Unicode escape in JSON at position " + at);
                    }
                    at += 4;
                }
                default -> {
                    at -= 1;
                    throw new SyntaxException("Bad escaped character in JSON at position " + at);
                }
            }
        }
    }

    private Json number() {
        int start = at;
        if (peek() == '-') {
            at += 1;
        }
        if (peek() == '0') {
            at += 1;
        } else if (isDigit(peek())) {
            digits();
        } else {
            throw unexpected();
        }
        if (peek() == '.') {
            at += 1;
            if (!isDigit(peek())) {
                throw unexpected();
            }
            digits();
        }
        if (peek() == 'e' || peek() == 'E') {
            at += 1;
            if (peek() == '+' || peek() == '-') {
                at += 1;
            }
            if (!isDigit(peek())) {
                throw unexpected();
            }
            digits();
        }
        return new Json.Num(Double.parseDouble(text.substring(start, at)));
    }

    private void digits() {
        while (isDigit(peek())) {
            at += 1;
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private Json literal(String word, Json value) {
        if (!text.startsWith(word, at)) {
            throw unexpected();
        }
        at += word.length();
        return value;
    }

    private void expect(char wanted) {
        if (peek() != wanted) {
            throw unexpected();
        }
        at += 1;
    }

    private char peek() {
        return at < text.length() ? text.charAt(at) : '\0';
    }

    private void skipSpace() {
        while (at < text.length()) {
            char c = text.charAt(at);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            at += 1;
        }
    }

    private SyntaxException unexpected() {
        if (at >= text.length()) {
            return new SyntaxException("Unexpected end of JSON input");
        }
        return new SyntaxException(
                "Unexpected token '" + text.charAt(at) + "', \"" + excerpt() + "\" is not valid JSON");
    }

    private String excerpt() {
        return text.length() <= 10 ? text : text.substring(0, 10) + "...";
    }
}
