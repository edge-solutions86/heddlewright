package io.github.rkaushikdev.heddlewright.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CoverageTest {

    private static final String SAMPLE = String.join(
            "\n",
            "GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,"
                    + "LINE_MISSED,LINE_COVERED,COMPLEXITY_MISSED,COMPLEXITY_COVERED,METHOD_MISSED,METHOD_COVERED",
            "heddlewright,io.github.rkaushikdev.heddlewright.util,A,10,90,2,8,5,95,1,9,0,2",
            "heddlewright,io.github.rkaushikdev.heddlewright.util,B,40,60,0,4,30,20,4,4,1,1",
            "heddlewright,io.github.rkaushikdev.heddlewright.util,C,0,30,0,0,0,10,0,3,0,3",
            "");

    @Nested
    class ParseCsv {

        private final List<Coverage.ClassCoverage> files = Coverage.parseCsv(SAMPLE);

        @Test
        void readsOneRecordPerClass() {
            assertThat(files)
                    .extracting(Coverage.ClassCoverage::file)
                    .containsExactly(
                            "io/github/rkaushikdev/heddlewright/util/A",
                            "io/github/rkaushikdev/heddlewright/util/B",
                            "io/github/rkaushikdev/heddlewright/util/C");
        }

        @Test
        void worksTheSharesAndTheMissesOut() {
            assertThat(files.get(0).lineShare()).isEqualTo(0.95);
            assertThat(files.get(0).branchShare()).isEqualTo(0.8);
            assertThat(files.get(1).missed()).isEqualTo(30);
        }

        @Test
        void readsAClassWithNoBranchesAsFullyCoveredOnBranches() {
            assertThat(files.get(2).branchShare()).isEqualTo(1);
        }

        @Test
        void isEmptyForAnEmptyReport() {
            assertThat(Coverage.parseCsv("")).isEmpty();
        }

        @Test
        void ignoresLinesThatAreNotRowsOfFigures() {
            assertThat(Coverage.parseCsv("LINE_MISSED,LINE_COVERED\nnot,a,row\n"))
                    .isEmpty();
        }
    }

    @Nested
    class WorstCovered {

        private final List<Coverage.ClassCoverage> files = Coverage.parseCsv(SAMPLE);

        @Test
        void ranksByLinesMissedRatherThanByShare() {
            assertThat(Coverage.worstCovered(files))
                    .extracting(Coverage.ClassCoverage::file)
                    .containsExactly(
                            "io/github/rkaushikdev/heddlewright/util/B", "io/github/rkaushikdev/heddlewright/util/A");
        }

        @Test
        void leavesOutTheClassesWithNothingMissing() {
            assertThat(Coverage.worstCovered(files))
                    .noneMatch(entry -> entry.file().endsWith("/C"));
        }

        @Test
        void takesAShorterList() {
            assertThat(Coverage.worstCovered(files, 1)).hasSize(1);
        }
    }

    @Nested
    class Totals {

        @Test
        void addsThePerClassFiguresUp() {
            Coverage.Totals summary = Coverage.totals(Coverage.parseCsv(SAMPLE));
            assertThat(summary.files()).isEqualTo(3);
            assertThat(summary.lines()).isEqualTo(160);
            assertThat(summary.hit()).isEqualTo(125);
            assertThat(summary.lineShare()).isCloseTo(125.0 / 160, within(0.0000005));
        }

        @Test
        void readsAnEmptyReportAsFullyCoveredRatherThanAsNothing() {
            Coverage.Totals summary = Coverage.totals(List.of());
            assertThat(summary.files()).isEqualTo(0);
            assertThat(summary.lineShare()).isEqualTo(1);
            assertThat(summary.branchShare()).isEqualTo(1);
        }
    }

    @Nested
    class TheRealReport {

        @Test
        void readsBackWhenOneHasBeenWritten() {
            if (!Coverage.hasReport()) {
                return;
            }
            List<Coverage.ClassCoverage> files = Coverage.readReport().orElseThrow();
            assertThat(files).isNotEmpty();
            assertThat(Coverage.totals(files).lineShare()).isGreaterThan(0.8);
        }

        @Test
        void saysPlainlyWhenThereIsNone() {
            assertThat(Coverage.readReport(Path.of("nowhere/jacoco.csv"))).isEmpty();
            assertThat(Coverage.hasReport(Path.of("nowhere/jacoco.csv"))).isFalse();
        }

        @Test
        void doesNotRunItselfOnLoad() {
            String text = Sources.read(Sources.mainSources().resolve("tools/Coverage.java"));
            assertThat(text).contains("public static void main(String[] args)");
            assertThat(text).doesNotContainPattern("(?m)^\\s*static\\s*\\{");
        }
    }

    @Nested
    class TheThresholds {

        @Test
        void areDeclaredInTheBuildRatherThanOnlyInATool() {
            String pom = Sources.read(Sources.root().resolve("pom.xml"));
            assertThat(pom).contains("<goal>check</goal>");
            assertThat(pom).containsPattern("<counter>LINE</counter>\\s*<value>COVEREDRATIO</value>");
            assertThat(pom).containsPattern("<coverage.minimum>0\\.\\d+</coverage.minimum>");
        }
    }
}
