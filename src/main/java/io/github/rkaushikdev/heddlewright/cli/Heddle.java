package io.github.rkaushikdev.heddlewright.cli;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * The {@code heddle} command line.
 *
 * <p>picocli holds the command tree: every registered command is a real subcommand, so
 * {@code heddle draft} is routed by picocli. The rest of the line is then read by the tool's own
 * parser, {@link Parse}, because its grammar is part of what the tool promises: an option it does not
 * declare is refused by name, {@code --} ends the options, and the usage text is built from the same
 * spec the parser checks. picocli is therefore set to pass every word through untouched.
 *
 * <p>Nothing here exits except {@link #main}: {@link #commandLine} gives a {@link CommandLine} whose
 * {@code execute} returns the exit code, 0 for success, 1 for a line that names no command and 2 for
 * a refused command.
 */
@Command(
        name = "heddle",
        description = "Weaving drafts, cloth and the shed floor, from the command line.",
        usageHelpAutoWidth = true)
public final class Heddle implements Callable<Integer> {

    /**
     * The end-of-options word picocli is given. A NUL cannot appear in a real command line, so
     * picocli never acts on it, and a {@code --} reaches the tool's own parser.
     */
    static final String NO_DELIMITER = String.valueOf((char) 0);

    @Spec
    private CommandSpec spec;

    @Parameters(arity = "0..*", paramLabel = "WORD", description = "The command and what it is given.")
    private List<String> words = new ArrayList<>();

    /** One registered command as a picocli subcommand. */
    @Command(description = "A registered heddle command.")
    static final class Subcommand implements Callable<Integer> {

        private final String name;

        @Spec
        private CommandSpec spec;

        @Parameters(arity = "0..*", paramLabel = "WORD", description = "What the command is given.")
        private List<String> words = new ArrayList<>();

        Subcommand(String name) {
            this.name = name;
        }

        @Override
        public Integer call() {
            List<String> argv = new ArrayList<>();
            argv.add(name);
            argv.addAll(words);
            return answer(spec.commandLine(), argv);
        }
    }

    @Override
    public Integer call() {
        return answer(spec.commandLine(), words);
    }

    /** Run a line, print what it says, and give back the exit code. */
    static int answer(CommandLine line, List<String> argv) {
        Run.Result result = Run.run(argv);
        PrintWriter out = line.getOut();
        PrintWriter err = line.getErr();
        if (!result.out().isEmpty()) {
            out.println(result.out());
            out.flush();
        }
        if (!result.err().isEmpty()) {
            err.println(result.err());
            err.flush();
        }
        return result.code();
    }

    /** The command line, with every registered command as a subcommand. */
    public static CommandLine commandLine() {
        CommandLine line = new CommandLine(new Heddle());
        for (Registry.Command command : Commands.listCommands()) {
            line.addSubcommand(command.name(), new CommandLine(new Subcommand(command.name())));
        }
        line.setUnmatchedOptionsArePositionalParams(true);
        line.setStopAtPositional(true);
        line.setExpandAtFiles(false);
        line.setPosixClusteredShortOptionsAllowed(false);
        line.setEndOfOptionsDelimiter(NO_DELIMITER);
        return line;
    }

    /** Run the tool and exit with its code. */
    public static void main(String[] args) {
        System.exit(commandLine().execute(args));
    }
}
