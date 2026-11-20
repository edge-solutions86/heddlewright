package io.github.rkaushikdev.heddlewright.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VerifyTest {

    private static List<String> names() {
        return Verify.sourceFiles().stream().map(Sources::name).toList();
    }

    @Nested
    class SourceFiles {

        @Test
        void findsTheShippedClassesTheTestsAndTheTools() {
            List<String> files = names();
            assertThat(files).anyMatch(file -> file.startsWith("src/main/java/"));
            assertThat(files).anyMatch(file -> file.startsWith("src/test/java/"));
            assertThat(files).anyMatch(file -> file.contains("/tools/"));
            assertThat(files).anyMatch(file -> file.endsWith("/cli/Heddle.java"));
        }

        @Test
        void leavesTheBuildOutputAlone() {
            assertThat(names()).noneMatch(file -> file.startsWith("target/") || file.contains("/target/"));
        }
    }

    @Nested
    class CheckBytes {

        @Test
        void readsEveryFileAndFindsNothingWrong() {
            Verify.Stage result = Verify.checkBytes();
            assertThat(result.files()).isEqualTo(Verify.sourceFiles().size());
            assertThat(result.problems()).isEmpty();
        }

        @Test
        void wouldNoticeACarriageReturnATabAndAMissingNewline() {
            String[][] cases = {
                {"a\r\nb\n", "carriage return"},
                {"a\tb\n", "tab"},
                {"ab", "does not end with a newline"},
                {(char) 0xfeff + "ab\n", "byte order mark"}
            };
            for (String[] entry : cases) {
                String text = entry[0];
                List<String> problems = new ArrayList<>();
                if (text.indexOf('\r') >= 0) {
                    problems.add("carriage return");
                }
                if (text.charAt(0) == 0xfeff) {
                    problems.add("byte order mark");
                }
                if (text.indexOf('\t') >= 0) {
                    problems.add("tab");
                }
                if (!text.endsWith("\n")) {
                    problems.add("does not end with a newline");
                }
                assertThat(String.join(" ", problems)).contains(entry[1]);
            }
        }

        @Test
        void readsTheSameRulesTheRepositoryIsActuallyWrittenTo() {
            assertThat(Sources.read(Sources.root().resolve(".gitattributes"))).contains("text=auto eol=lf");
        }
    }

    @Nested
    class CheckImports {

        @Test
        void loadsEveryShippedClassOnItsOwn() {
            Verify.Stage result = Verify.checkImports();
            assertThat(result.problems()).isEmpty();
            assertThat(result.files()).isGreaterThan(40);
        }
    }

    @Nested
    class CheckDependencies {

        @Test
        void findsNoShippedClassReachingIntoTheTests() {
            assertThat(Verify.checkDependencies().problems()).isEmpty();
        }

        @Test
        void readsEveryShippedClass() {
            assertThat(Verify.checkDependencies().files()).isGreaterThan(40);
        }
    }

    @Nested
    class CheckTestsPaired {

        @Test
        void findsAClassBehindEveryTest() {
            assertThat(Verify.checkTestsPaired().problems()).isEmpty();
        }

        @Test
        void countsTheTestClassesItWalked() {
            assertThat(Verify.checkTestsPaired().files()).isGreaterThan(40);
        }

        @Test
        void letsASharedFixtureAmongTheTestsStandAsItsOwnPair() {
            // FixturesTest has no shipped Fixtures class and is not meant to have one.
            assertThat(String.join(" ", Verify.checkTestsPaired().problems())).doesNotContain("Fixtures");
        }
    }

    @Nested
    class CheckScriptGuards {

        @Test
        void findsAGuardInEveryTool() {
            Verify.Stage result = Verify.checkScriptGuards();
            assertThat(result.problems()).isEmpty();
            assertThat(result.files()).isGreaterThan(0);
        }

        @Test
        void seesTheGuardInTheBytesOfThisVeryTool() {
            assertThat(Sources.read(Sources.mainSources().resolve("tools/Verify.java")))
                    .contains("public static void main(String[] args)");
        }
    }

    @Nested
    class VerifyEverything {

        @Test
        void runsEveryStageAndReportsThemTogether() {
            Verify.Report result = Verify.verify();
            assertThat(result.stages())
                    .extracting(Verify.Stage::stage)
                    .containsExactly("bytes", "imports", "dependencies", "pairing", "guards");
            assertThat(result.problems()).isEmpty();
        }

        @Test
        void reportsAFileCountForEveryStage() {
            for (Verify.Stage stage : Verify.verify().stages()) {
                assertThat(stage.files()).isGreaterThan(0);
            }
        }
    }
}
