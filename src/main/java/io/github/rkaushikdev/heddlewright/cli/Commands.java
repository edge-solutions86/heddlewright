package io.github.rkaushikdev.heddlewright.cli;

import io.github.rkaushikdev.heddlewright.cli.Parse.Option;
import io.github.rkaushikdev.heddlewright.draft.Draft;
import java.util.List;
import java.util.Map;

/**
 * The commands the tool offers.
 *
 * <p>This class does one thing: it loads the classes that define the commands, so that using it is
 * enough to have them, and hands on the register for everything that reads it. Each of the two
 * {@code define} calls below puts one file's commands in the register.
 */
public final class Commands {

    static {
        Draw.define();
        Shop.define();
    }

    /** The options every drawing command shares. */
    public static final Map<String, Option> DRAW_OPTIONS = Draw.DRAW_OPTIONS;

    private Commands() {}

    /** Build the draft the drawing commands draw. */
    public static Draft buildDraft(Object options) {
        return Draw.buildDraft(options);
    }

    /** Every command, in name order. */
    public static List<Registry.Command> listCommands() {
        return Registry.listCommands();
    }

    /** Look a command up. */
    public static Registry.Command commandByName(String name) {
        return Registry.commandByName(name);
    }

    /** The usage text for one command. */
    public static String commandUsage(String name) {
        return Registry.commandUsage(name);
    }

    /** The usage text for the whole tool. */
    public static String toolUsage() {
        return Registry.toolUsage();
    }

    /** The spec a command declared. */
    public static Map<String, Option> specOf(String name) {
        return Registry.specOf(name);
    }
}
