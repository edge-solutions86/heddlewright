package io.github.rkaushikdev.heddlewright.cli;

import io.github.rkaushikdev.heddlewright.cli.Parse.Option;
import io.github.rkaushikdev.heddlewright.cli.Parse.Parsed;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Collation;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import io.github.rkaushikdev.heddlewright.util.Table;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The command registry.
 *
 * <p>The commands themselves live in classes grouped by what they are about; this holds the register
 * they put themselves in and everything that reads it. It knows nothing about weaving, which is why
 * it can be read on its own.
 */
public final class Registry {

    /** What a command does with its parsed line: the text it prints. */
    @FunctionalInterface
    public interface Runner {
        /** Run the command. */
        String run(Parsed parsed);
    }

    /**
     * A registered command.
     *
     * @param name what it is called on the command line
     * @param summary one line saying what it does
     * @param spec the options it takes, in the order the usage lists them
     * @param runner what it does
     */
    public record Command(String name, String summary, Map<String, Option> spec, Runner runner) {
        /** Copies the spec, keeping its order. */
        public Command {
            spec = Collections.unmodifiableMap(new LinkedHashMap<>(spec));
        }

        /** Run the command on a parsed line. */
        public String run(Parsed parsed) {
            return runner.run(parsed);
        }
    }

    private static final Map<String, Command> COMMANDS = new LinkedHashMap<>();

    private Registry() {}

    /** Put a command in the register. */
    static synchronized String define(String name, String summary, Map<String, Option> spec, Runner runner) {
        Check.nonEmptyString(name, "name");
        Check.nonEmptyString(summary, "summary");
        Check.required(spec, "spec");
        if (runner == null) {
            throw new NotFoundException("the command \"" + name + "\" has nothing to run", Json.obj("name", name));
        }
        COMMANDS.put(name, new Command(name, summary, spec, runner));
        return name;
    }

    /** The spec a command declared, for a run function that wants to read it back. */
    public static Map<String, Option> specOf(String name) {
        return commandByName(name).spec();
    }

    /** Every command, in name order. */
    public static synchronized List<Command> listCommands() {
        return COMMANDS.values().stream()
                .sorted(Comparator.comparing(Command::name, Collation.ORDER))
                .toList();
    }

    /** Look a command up. */
    public static synchronized Command commandByName(String name) {
        Check.nonEmptyString(name, "name");
        Command found = COMMANDS.get(name);
        if (found == null) {
            throw new NotFoundException(
                    "no command called \"" + name + "\"",
                    Json.obj("name", name, "known", List.copyOf(COMMANDS.keySet())));
        }
        return found;
    }

    /** The usage text for one command. */
    public static String commandUsage(String name) {
        Command command = commandByName(name);
        return Parse.usageFor(command.name(), command.spec(), command.summary());
    }

    /** The usage text for the whole tool. */
    public static String toolUsage() {
        List<Json> rows = listCommands().stream()
                .map(command -> (Json) Json.obj("command", command.name(), "summary", command.summary()))
                .toList();
        return String.join(
                "\n",
                "heddle <command> [options]",
                "",
                Table.renderTable(rows, List.of(Table.Column.of("command"), Table.Column.of("summary"))));
    }
}
