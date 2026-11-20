package io.github.rkaushikdev.heddlewright.cli;

import io.github.rkaushikdev.heddlewright.cli.Parse.Parsed;
import io.github.rkaushikdev.heddlewright.util.Errors;
import io.github.rkaushikdev.heddlewright.util.HeddleException;
import io.github.rkaushikdev.heddlewright.util.Json;
import java.util.List;

/**
 * Running a command.
 *
 * <p>{@code run} never throws and never writes anywhere. It returns the exit code and the two streams
 * as text, so the entry point is a few lines and everything about what the tool says can be tested
 * without starting a process.
 */
public final class Run {

    /** The command ran. */
    public static final int EXIT_OK = 0;

    /** The line named no command the tool has, or could not be read. */
    public static final int EXIT_USAGE = 1;

    /** The command was refused. */
    public static final int EXIT_ERROR = 2;

    /** What one run would print, and the code it would exit with. */
    public record Result(int code, String out, String err) {}

    private Run() {}

    /** Run one invocation and report what it would print. */
    public static Result run(List<String> argv) {
        Parsed parsed;
        try {
            parsed = Parse.parseArgs(argv);
        } catch (RuntimeException err) {
            return new Result(EXIT_USAGE, "", message(err));
        }

        if (parsed.command().isEmpty() || parsed.command().get().equals("help")) {
            if (!parsed.positional().isEmpty()) {
                try {
                    return new Result(
                            EXIT_OK, Commands.commandUsage(parsed.positional().get(0)), "");
                } catch (RuntimeException err) {
                    return new Result(EXIT_USAGE, "", message(err));
                }
            }
            return new Result(parsed.command().isEmpty() ? EXIT_USAGE : EXIT_OK, Commands.toolUsage(), "");
        }

        Registry.Command command;
        try {
            command = Commands.commandByName(parsed.command().get());
        } catch (RuntimeException err) {
            return new Result(EXIT_USAGE, "", message(err) + "\n\n" + Commands.toolUsage());
        }

        try {
            return new Result(EXIT_OK, command.run(parsed), "");
        } catch (RuntimeException err) {
            String usage =
                    Errors.isHeddleError(err, "invalid_argument") ? "\n\n" + Commands.commandUsage(command.name()) : "";
            return new Result(EXIT_ERROR, "", message(err) + usage);
        }
    }

    private static String message(RuntimeException err) {
        if (err instanceof HeddleException ours) {
            String detail = ours.detail().size() == 0 ? "" : " " + Json.stringify(ours.detail());
            return ours.getMessage() + detail;
        }
        return err.getMessage() == null ? String.valueOf(err) : err.getMessage();
    }
}
