package io.github.rkaushikdev.heddlewright.cli;

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

/**
 * Command line parsing.
 *
 * <p>Arguments are a command, then positional values, then options. An option is {@code --name
 * value} or {@code --name=value}, and a flag is {@code --name} on its own. A single {@code --} stops
 * option parsing, so a value that looks like an option can still be passed. Nothing is guessed: an
 * option the command does not declare is an error rather than a silent extra.
 */
public final class Parse {

    /** The types an option can be declared with. */
    public enum Type {
        /** Text, as given. */
        STRING,
        /** A finite number. */
        NUMBER,
        /** A whole number, written as digits. */
        INTEGER,
        /** Comma-separated words, empty ones dropped. */
        LIST,
        /** Present or not; {@code yes} and {@code true} also read as present. */
        FLAG;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One option a command declares.
     *
     * @param type how its value is read
     * @param required whether it must be given
     * @param fallback the value when it is not given, or null
     * @param describe a line for the usage text, or empty
     */
    public record Option(Type type, boolean required, Json fallback, String describe) {
        /** Fills in the null fallback and the empty description. */
        public Option {
            fallback = fallback == null ? Json.NULL : fallback;
            describe = describe == null ? "" : describe;
        }

        /** An optional option with no default. */
        public static Option of(Type type) {
            return new Option(type, false, Json.NULL, "");
        }

        /** An optional option with a default. */
        public static Option of(Type type, Object fallback) {
            return new Option(type, false, Json.of(fallback), "");
        }

        /** An option that must be given. */
        public static Option required(Type type) {
            return new Option(type, true, Json.NULL, "");
        }

        /** The same option with a line for the usage text. */
        public Option describe(String text) {
            return new Option(type, required, fallback, text);
        }
    }

    /**
     * A command line split up: the command, its positional values, and its options. An option's
     * value is its text, or {@link Boolean#TRUE} for a flag given with no value.
     */
    public record Parsed(Optional<String> command, List<String> positional, Map<String, Object> options) {
        /** Copies the lists, keeping the options in the order they were given. */
        public Parsed {
            positional = List.copyOf(positional);
            options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
        }
    }

    private Parse() {}

    /** Split argv into a command, its positional values and its options. */
    public static Parsed parseArgs(List<String> argv) {
        Check.array(argv, "argv");
        List<String> positional = new ArrayList<>();
        Map<String, Object> options = new LinkedHashMap<>();
        boolean literal = false;
        for (int i = 0; i < argv.size(); i += 1) {
            String arg = String.valueOf(argv.get(i));
            if (literal || !arg.startsWith("--")) {
                positional.add(arg);
                continue;
            }
            if (arg.equals("--")) {
                literal = true;
                continue;
            }
            String body = arg.substring(2);
            int equals = body.indexOf('=');
            // A bare -- is the separator and is handled above, so anything reaching here with no
            // name is --=value, which is a typo rather than an option.
            String name = equals == -1 ? body : body.substring(0, equals);
            if (name.isEmpty()) {
                throw new InvalidArgumentException("an option needs a name", Json.obj("arg", arg));
            }
            if (equals != -1) {
                options.put(name, body.substring(equals + 1));
                continue;
            }
            String next = i + 1 < argv.size() ? String.valueOf(argv.get(i + 1)) : null;
            if (next == null || next.startsWith("--")) {
                options.put(name, Boolean.TRUE);
                continue;
            }
            options.put(name, next);
            i += 1;
        }
        Optional<String> command = positional.isEmpty() ? Optional.empty() : Optional.of(positional.get(0));
        return new Parsed(
                command, positional.isEmpty() ? List.of() : positional.subList(1, positional.size()), options);
    }

    /**
     * Check the parsed options against what a command declares, and read each to its type. An
     * unknown option is refused with the list of the ones it does take, because a typo in an option
     * name is otherwise silently ignored. An option left out takes its default, or null.
     */
    public static JsonObject checkOptions(Parsed parsed, Map<String, Option> spec) {
        Check.required(spec, "spec");
        List<String> known = List.copyOf(spec.keySet());
        List<String> unknown = parsed.options().keySet().stream()
                .filter(name -> !known.contains(name))
                .toList();
        if (!unknown.isEmpty()) {
            throw new InvalidArgumentException(
                    "unknown option --" + unknown.get(0), Json.obj("unknown", unknown, "known", known));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Option> entry : spec.entrySet()) {
            String name = entry.getKey();
            Option declared = entry.getValue();
            Object raw = parsed.options().get(name);
            if (raw == null) {
                if (declared.required()) {
                    throw new InvalidArgumentException("--" + name + " is required", Json.obj("option", name));
                }
                out.put(name, declared.fallback());
                continue;
            }
            out.put(name, coerce(name, raw, declared.type()));
        }
        return (JsonObject) Json.of(out);
    }

    private static Object coerce(String name, Object raw, Type type) {
        if (type == Type.FLAG) {
            return raw == Boolean.TRUE || "yes".equals(raw) || "true".equals(raw);
        }
        if (raw == Boolean.TRUE) {
            throw new InvalidArgumentException("--" + name + " needs a value", Json.obj("option", name, "type", type));
        }
        String text = (String) raw;
        return switch (type) {
            case NUMBER -> {
                double value = Text.toNumber(text);
                if (!Double.isFinite(value)) {
                    throw new InvalidArgumentException(
                            "--" + name + " must be a number", Json.obj("option", name, "got", text));
                }
                yield value;
            }
            case INTEGER -> {
                if (!text.matches("-?\\d+")) {
                    throw new InvalidArgumentException(
                            "--" + name + " must be a whole number", Json.obj("option", name, "got", text));
                }
                yield Text.parseInt(text);
            }
            case LIST ->
                Text.split(text, ",").stream()
                        .map(Text::trim)
                        .filter(part -> !part.isEmpty())
                        .toList();
            default -> text;
        };
    }

    /** Usage text for a command with no summary. */
    public static String usageFor(String name, Map<String, Option> spec) {
        return usageFor(name, spec, "");
    }

    /** Usage text for one command, built from the same spec the parser checks. */
    public static String usageFor(String name, Map<String, Option> spec, String summary) {
        Check.nonEmptyString(name, "name");
        Check.required(spec, "spec");
        List<String> lines = new ArrayList<>();
        lines.add("heddle " + name + (summary.isEmpty() ? "" : " - " + summary));
        for (Map.Entry<String, Option> entry : spec.entrySet()) {
            Option declared = entry.getValue();
            String type = declared.type() == Type.FLAG ? "" : " <" + declared.type() + ">";
            String mark = declared.required() ? " (required)" : "";
            lines.add(Text.trimEnd("  --" + entry.getKey() + type + mark + "  " + declared.describe()));
        }
        return String.join("\n", lines);
    }
}
