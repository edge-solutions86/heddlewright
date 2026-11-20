package io.github.rkaushikdev.heddlewright.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** The picocli command tree hands every word to the tool's own parser untouched. */
class HeddleTest {

    private record Executed(int code, String out, String err) {}

    private static Executed execute(String... argv) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine line = Heddle.commandLine();
        line.setOut(new PrintWriter(out));
        line.setErr(new PrintWriter(err));
        int code = line.execute(argv);
        return new Executed(code, out.toString(), err.toString());
    }

    @Test
    void makesEveryRegisteredCommandASubcommand() {
        List<String> names =
                Commands.listCommands().stream().map(Registry.Command::name).toList();
        assertThat(Heddle.commandLine().getSubcommands().keySet()).containsExactlyInAnyOrderElementsOf(names);
    }

    @Test
    void passesADoubleDashThroughToTheToolsOwnParser() {
        // After --, a word that looks like an option is a value, so the draft command is not given
        // an option it would refuse and simply ignores the stray word.
        Executed result = execute("check", "--shafts", "4", "--ends", "8", "--", "--shaftz");
        assertThat(result.code()).isEqualTo(Run.EXIT_OK);
        assertThat(result.out().trim()).isEqualTo("nothing wrong with it");
    }

    @Test
    void refusesAnUndeclaredOptionWithTheToolsOwnMessage() {
        Executed result = execute("check", "--shaftz", "4");
        assertThat(result.code()).isEqualTo(Run.EXIT_ERROR);
        assertThat(result.err()).contains("unknown option --shaftz").contains("heddle check");
    }

    @Test
    void readsAWordStartingWithAnAtSignAsTheWordItIs() {
        Executed result = execute("@nothing");
        assertThat(result.code()).isEqualTo(Run.EXIT_USAGE);
        assertThat(result.err()).contains("no command called \"@nothing\"");
    }

    @Test
    void printsTheToolUsageWhenGivenNoWordsAtAll() {
        Executed result = execute();
        assertThat(result.code()).isEqualTo(Run.EXIT_USAGE);
        assertThat(result.out()).startsWith("heddle <command> [options]");
    }
}
