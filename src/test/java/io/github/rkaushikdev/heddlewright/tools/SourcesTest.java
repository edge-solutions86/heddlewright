package io.github.rkaushikdev.heddlewright.tools;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.heddlewright.tools.Sources.SourceFile;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * House rules, checked against the bytes of every file.
 *
 * <p>Every rule here is a predicate over one file's text. A predicate that has quietly stopped
 * matching anything reports a clean sweep, which is indistinguishable from a clean repository, so
 * each one is also run against a planted string that it must reject. A rule with no positive control
 * is a rule nobody can tell is still working.
 */
class SourcesTest {

    /**
     * This file is the one exemption, and it has to be. Every rule below carries a planted string
     * that the rule must reject, so this file holds a hand-built path, a pasted drive letter and the
     * markers of unfinished work on purpose. Sweeping it would report its own controls as
     * violations.
     */
    private static final String EXEMPT = "src/test/java/io/github/rkaushikdev/heddlewright/tools/SourcesTest.java";

    private static final String SHIPPED = "src/main/java/";

    private static final List<SourceFile> FILES = Sources.readAll().stream()
            .filter(file -> !file.name().equals(EXEMPT))
            .toList();

    private static final List<SourceFile> SOURCE =
            FILES.stream().filter(file -> file.name().startsWith(SHIPPED)).toList();

    /** The shipped library, which is the source less the tools that are meant to print. */
    private static final List<SourceFile> LIBRARY =
            SOURCE.stream().filter(file -> !file.name().contains("/tools/")).toList();

    /** The two markers of unfinished work, spelt apart so this file carries neither whole. */
    private static final String UNFINISHED = "TO" + "DO";

    private static final String BROKEN = "FIX" + "ME";

    private static List<String> offenders(List<SourceFile> files, Predicate<String> rule) {
        return files.stream()
                .filter(file -> rule.test(file.text()))
                .map(SourceFile::name)
                .toList();
    }

    private static List<String> offenders(List<SourceFile> files, BiPredicate<String, String> rule) {
        return files.stream()
                .filter(file -> rule.test(file.text(), file.name()))
                .map(SourceFile::name)
                .toList();
    }

    private static boolean finds(String pattern, String text) {
        return Pattern.compile(pattern).matcher(text).find();
    }

    @Nested
    class TheFileListItself {

        @Test
        void isNotEmptyWhichWouldMakeEveryRuleBelowPassForFree() {
            assertThat(FILES.size()).isGreaterThan(80);
            assertThat(SOURCE.size()).isGreaterThan(40);
        }

        @Test
        void coversEveryPartOfThePackage() {
            String base = SHIPPED + Sources.PACKAGE_PATH + "/";
            for (String area :
                    List.of(base + "draft/", base + "weave/", base + "jacquard/", base + "loom/", "src/test/java/")) {
                assertThat(FILES).anyMatch(file -> file.name().startsWith(area));
            }
        }

        @Test
        void leavesExactlyOneFileOutAndThatFileIsThisOne() {
            Set<String> swept =
                    new HashSet<>(FILES.stream().map(SourceFile::name).toList());
            List<String> missing = Sources.sourceFiles().stream()
                    .map(Sources::name)
                    .filter(name -> !swept.contains(name))
                    .toList();
            assertThat(missing).containsExactly(EXEMPT);
        }
    }

    @Nested
    class NoCarriageReturnsAnywhere {

        private final Predicate<String> rule = text -> text.indexOf('\r') >= 0;

        @Test
        void holdsAcrossTheRepository() {
            assertThat(offenders(FILES, rule)).isEmpty();
        }

        @Test
        void wouldCatchOneIfItWereThere() {
            assertThat(rule.test("a\r\nb\n")).isTrue();
            assertThat(rule.test("a\nb\n")).isFalse();
        }
    }

    @Nested
    class NoTabsInTheIndentation {

        private final Predicate<String> rule = text -> finds("(?m)^\t", text);

        @Test
        void holdsAcrossTheRepository() {
            assertThat(offenders(FILES, rule)).isEmpty();
        }

        @Test
        void wouldCatchOneIfItWereThere() {
            assertThat(rule.test("int f() {\n\treturn 1;\n}\n")).isTrue();
            assertThat(rule.test("int f() {\n    return 1;\n}\n")).isFalse();
        }
    }

    @Nested
    class EveryFileEndsWithExactlyOneNewline {

        private final Predicate<String> rule = text -> !text.endsWith("\n") || text.endsWith("\n\n");

        @Test
        void holdsAcrossTheRepository() {
            assertThat(offenders(FILES, rule)).isEmpty();
        }

        @Test
        void wouldCatchAMissingOrADoubledOne() {
            assertThat(rule.test("a")).isTrue();
            assertThat(rule.test("a\n\n")).isTrue();
            assertThat(rule.test("a\n")).isFalse();
        }
    }

    @Nested
    class NothingInTheLibraryWritesToTheConsole {

        private final Predicate<String> rule = text -> finds("\\bSystem\\.(out|err)\\.print", text);

        @Test
        void holdsAcrossTheLibrary() {
            assertThat(offenders(LIBRARY, rule)).isEmpty();
        }

        @Test
        void wouldCatchOneIfItWereThere() {
            assertThat(rule.test("System.out.println(\"hello\");\n")).isTrue();
            assertThat(rule.test("boolean logged = false;\n")).isFalse();
        }

        @Test
        void allowsItInTheToolsWhichAreMeantToPrint() {
            List<String> printing = offenders(FILES, rule);
            assertThat(printing).isNotEmpty();
            assertThat(printing).allMatch(name -> name.contains("/tools/"));
        }
    }

    @Nested
    class NoUnfinishedWorkIsLeftLyingAbout {

        // The usual third marker, three X in a row, is not in this list. A drawdown printed as a
        // string of X and dots carries it in any cloth wider than two ends, so a rule looking for it
        // would report every such cloth as unfinished work.
        private final Predicate<String> rule = text -> finds("\\b(" + UNFINISHED + "|" + BROKEN + ")\\b", text);

        @Test
        void holdsAcrossTheRepository() {
            assertThat(offenders(FILES, rule)).isEmpty();
        }

        @Test
        void wouldCatchEitherMarker() {
            assertThat(rule.test("// " + UNFINISHED + ": later\n")).isTrue();
            assertThat(rule.test("// " + BROKEN + "\n")).isTrue();
            assertThat(rule.test("// done\n")).isFalse();
        }

        @Test
        void doesNotReadADrawdownAsAMarker() {
            assertThat(rule.test("assertThat(cloth).isEqualTo(\"" + "X".repeat(3) + ".\");\n"))
                    .isFalse();
        }
    }

    @Nested
    class EveryShippedFileOpensWithACommentSayingWhatItIsFor {

        private final Predicate<String> rule = text -> !text.contains("/**");

        @Test
        void holdsAcrossTheSource() {
            assertThat(offenders(SOURCE, rule)).isEmpty();
        }

        @Test
        void wouldCatchAFileWithNoDocCommentAtAll() {
            assertThat(rule.test("final class X {}\n")).isTrue();
            assertThat(rule.test("/** What it is for. */\nfinal class X {}\n")).isFalse();
        }
    }

    @Nested
    class NoShippedFileHasGrownPastWhatOneSittingCanRead {

        private final Predicate<String> rule = text -> text.split("\n", -1).length > 400;

        @Test
        void holdsAcrossTheSource() {
            assertThat(offenders(SOURCE, rule)).isEmpty();
        }

        @Test
        void wouldCatchOneThatHad() {
            assertThat(rule.test("x\n".repeat(401))).isTrue();
            assertThat(rule.test("x\n".repeat(399))).isFalse();
        }
    }

    @Nested
    class ImportsNameAClassRatherThanAPackage {

        private final Predicate<String> rule = text -> finds("(?m)^import\\s+(static\\s+)?[\\w.]+\\.\\*\\s*;", text);

        @Test
        void holdsAcrossTheRepository() {
            assertThat(offenders(FILES, rule)).isEmpty();
        }

        @Test
        void wouldCatchAWildcardImport() {
            assertThat(rule.test("import java.util.*;\n")).isTrue();
            assertThat(rule.test("import java.util.List;\n")).isFalse();
        }

        @Test
        void takesAStaticImportTooWhichNamesAMember() {
            assertThat(rule.test("import static java.lang.Math.max;\n")).isFalse();
        }
    }

    @Nested
    class NobodyAddsAFieldUpByHand {

        /**
         * {@code Numeric.sum} and {@code Numeric.sumBy} name the record at fault when a field is not a
         * number. A total folded by hand from zero gives a wrong figure instead of a refusal.
         * {@code util/Matrix.java} is the exemption because counting its cells is the primitive the
         * rest of the sweep rests on.
         */
        private final BiPredicate<String, String> rule =
                (text, name) -> !name.endsWith("/util/Matrix.java") && finds("\\.reduce\\(\\s*0(\\.0)?\\s*,", text);

        @Test
        void holdsAcrossTheSource() {
            assertThat(offenders(SOURCE, rule)).isEmpty();
        }

        @Test
        void wouldCatchOneWrittenOutLonghand() {
            assertThat(rule.test("double t = rows.stream().map(Row::yards).reduce(0.0, Double::sum);\n", "src/X.java"))
                    .isTrue();
            assertThat(rule.test("double t = Numeric.sumBy(rows, Row::yards, \"rows\");\n", "src/X.java"))
                    .isFalse();
        }

        @Test
        void leavesTheOneFileThePrimitiveLivesInAlone() {
            String text = "int t = cells.stream().reduce(0, Integer::sum);\n";
            assertThat(rule.test(text, "src/main/java/x/util/Matrix.java")).isFalse();
            assertThat(rule.test(text, "src/main/java/x/util/Numeric.java")).isTrue();
        }
    }

    @Nested
    class NobodyTurnsAUrlIntoAPathByHand {

        /**
         * The path of a resource URL keeps its percent escapes and, on Windows, a slash before the
         * drive letter, so a path read that way fails on a directory with a space in it or on the
         * other platform. A URI turned into a Path knows both.
         */
        private final Predicate<String> rule = text -> finds("\\.getPath\\(\\)", text);

        @Test
        void holdsAcrossTheRepository() {
            assertThat(offenders(FILES, rule)).isEmpty();
        }

        @Test
        void wouldCatchTheWayItIsUsuallyWritten() {
            assertThat(rule.test("new File(getClass().getResource(\"x\").getPath());\n"))
                    .isTrue();
            assertThat(rule.test("Path.of(getClass().getResource(\"x\").toURI());\n"))
                    .isFalse();
        }
    }

    @Nested
    class APathIsBuiltRatherThanPastedTogether {

        /**
         * A drive letter in a string literal is a Windows path that will not resolve on a Linux
         * runner. Only the drive letter is looked for: a backslash on its own is no evidence at all
         * here, because a drawdown is a string of dots and newline escapes and half the weave tests
         * carry one.
         */
        private final Predicate<String> rule = text -> finds("['\"][A-Za-z]:\\\\", text);

        @Test
        void holdsAcrossTheRepository() {
            assertThat(offenders(FILES, rule)).isEmpty();
        }

        @Test
        void wouldCatchAWindowsPathWrittenIntoTheSource() {
            assertThat(rule.test("String root = \"C:\\\\repo\\\\src\";\n")).isTrue();
            assertThat(rule.test("Path root = here.resolve(\"src\");\n")).isFalse();
        }

        @Test
        void saysNothingAboutADrawdownWhichIsDotsAndNewlines() {
            assertThat(rule.test("Matrix.parse(\"..\\n..\")")).isFalse();
            assertThat(rule.test("cloth.format().split(\"\\n\")")).isFalse();
        }
    }
}
