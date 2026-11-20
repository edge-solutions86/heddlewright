package io.github.rkaushikdev.heddlewright.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class RunTest {

    private static Run.Result run(String... argv) {
        return Run.run(List.of(argv));
    }

    /** What one run of the real command line printed, and the code it exited with. */
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

    @Nested
    class RunOneLine {

        @Test
        void runsACommandAndReportsSuccess() {
            Run.Result result = run("weave", "--notation", "2/2");
            assertThat(result.code()).isEqualTo(Run.EXIT_OK);
            assertThat(result.out()).isEqualTo("XX..\n.XX.\n..XX\nX..X");
            assertThat(result.err()).isEmpty();
        }

        @Test
        void printsTheToolUsageAndFailsWhenNothingIsAskedFor() {
            Run.Result result = run();
            assertThat(result.code()).isEqualTo(Run.EXIT_USAGE);
            assertThat(result.out()).contains("heddle <command>");
        }

        @Test
        void printsTheToolUsageAndSucceedsOnHelp() {
            Run.Result result = run("help");
            assertThat(result.code()).isEqualTo(Run.EXIT_OK);
            assertThat(result.out()).contains("weave");
        }

        @Test
        void printsOneCommandUsageOnHelpWithAName() {
            Run.Result result = run("help", "count");
            assertThat(result.code()).isEqualTo(Run.EXIT_OK);
            assertThat(result.out()).contains("--value <number> (required)");
        }

        @Test
        void failsOnHelpForACommandItDoesNotHave() {
            Run.Result result = run("help", "nothing");
            assertThat(result.code()).isEqualTo(Run.EXIT_USAGE);
            assertThat(result.err()).contains("no command called");
        }

        @Test
        void namesTheCommandsItDoesHaveWhenGivenAnUnknownOne() {
            Run.Result result = run("nothing");
            assertThat(result.code()).isEqualTo(Run.EXIT_USAGE);
            assertThat(result.err()).contains("heddle <command>");
        }

        @Test
        void showsTheCommandUsageWhenAnArgumentIsWrong() {
            Run.Result result = run("count", "--value", "lots", "--from", "ne");
            assertThat(result.code()).isEqualTo(Run.EXIT_ERROR);
            assertThat(result.err()).contains("--value must be a number");
            assertThat(result.err()).contains("heddle count");
        }

        @Test
        void reportsADomainErrorWithoutTheUsageBecauseTheArgumentsWereFine() {
            Run.Result result = run("weave", "--kind", "satin", "--threads", "5", "--move", "1");
            assertThat(result.code()).isEqualTo(Run.EXIT_ERROR);
            assertThat(result.err()).contains("twill line");
            assertThat(result.err()).doesNotContain("heddle weave");
        }

        @Test
        void putsTheErrorDetailBesideTheMessage() {
            assertThat(run("weave", "--kind", "satin", "--threads", "5", "--move", "1")
                            .err())
                    .contains("\"threads\":5");
        }

        @Test
        void neverThrowsWhateverItIsGiven() {
            for (List<String> argv : List.of(
                    List.<String>of(),
                    List.of("--=1"),
                    List.of("nothing"),
                    List.of("sett"),
                    List.of("draft", "--shafts", "x"))) {
                assertThatCode(() -> Run.run(argv)).doesNotThrowAnyException();
            }
        }
    }

    @Nested
    class TheEntryPoint {

        @Test
        void isAClassWithAMainMethodRatherThanAScript() throws NoSuchMethodException {
            // A main method needs no interpreter line and no executable bit, so no checkout can
            // break it by converting line endings.
            Method main = Heddle.class.getMethod("main", String[].class);
            assertThat(Modifier.isStatic(main.getModifiers())).isTrue();
        }

        @Test
        void isTheClassTheBuildNamesAsTheMainClass() throws IOException {
            // The positive control for the two below: the jar runs this class, so these tests run
            // what a user runs.
            String pom = Files.readString(Path.of("pom.xml"));
            assertThat(pom).contains("<main.class>" + Heddle.class.getName() + "</main.class>");
        }

        @Test
        void printsWhatRunGivesIt() {
            assertThat(execute("weave").out().trim()).isEqualTo("XX..\n.XX.\n..XX\nX..X");
        }

        @Test
        void setsTheUsageExitCodeWhenTheCommandIsNotOne() {
            Executed result = execute("nothing");
            assertThat(result.code()).isEqualTo(Run.EXIT_USAGE);
            // Without this the test passes on an entry point that could not even start.
            assertThat(result.err()).contains("nothing");
        }

        @Test
        void setsTheErrorExitCodeWhenTheCommandIsOneButTheOptionsAreWrong() {
            assertThat(execute("sett").code()).isEqualTo(Run.EXIT_ERROR);
        }
    }
}
