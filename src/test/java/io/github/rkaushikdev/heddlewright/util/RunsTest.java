package io.github.rkaushikdev.heddlewright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.Runs.Run;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RunsTest {

    @Nested
    class RunsOf {

        @Test
        void splitsASequenceIntoMaximalBlocks() {
            assertThat(Runs.runs(List.of(1, 1, 0, 0, 0, 1)))
                    .containsExactly(new Run<>(1, 0, 2, false), new Run<>(0, 2, 3, false), new Run<>(1, 5, 1, false));
        }

        @Test
        void givesOneRunForAConstantSequenceAndNoneForAnEmptyOne() {
            assertThat(Runs.runs(List.of(1, 1, 1))).containsExactly(new Run<>(1, 0, 3, false));
            assertThat(Runs.runs(List.of())).isEmpty();
        }

        @Test
        void comparesStrictlySo0AndFalseAreDifferentValues() {
            assertThat(Runs.runs(List.<Object>of(0, false))).hasSize(2);
        }
    }

    @Nested
    class CyclicRuns {

        @Test
        void joinsARunThatWrapsTheEndBackToTheStart() {
            assertThat(Runs.cyclicRuns(List.of(1, 0, 0, 1, 1)))
                    .containsExactly(new Run<>(0, 1, 2, false), new Run<>(1, 3, 3, true));
        }

        @Test
        void leavesTheRunsAloneWhenTheEndsDoNotMatch() {
            assertThat(Runs.cyclicRuns(List.of(1, 1, 0)))
                    .containsExactly(new Run<>(1, 0, 2, false), new Run<>(0, 2, 1, false));
        }

        @Test
        void reportsAConstantSequenceAsASingleNonWrappingRunOfTheWholeLength() {
            assertThat(Runs.cyclicRuns(List.of(1, 1, 1, 1))).containsExactly(new Run<>(1, 0, 4, false));
        }

        @Test
        void isEmptyForAnEmptySequence() {
            assertThat(Runs.cyclicRuns(List.of())).isEmpty();
        }

        @Test
        void neverReportsARunLongerThanTheSequence() {
            for (List<Integer> seq : List.of(List.of(1, 0, 1), List.of(1, 1, 0, 1), List.of(0, 0, 0, 1, 0))) {
                for (Run<Integer> run : Runs.cyclicRuns(seq)) {
                    assertThat(run.length()).isLessThanOrEqualTo(seq.size());
                }
            }
        }
    }

    @Nested
    class LongestRun {

        @Test
        void countsTheWrapByDefaultWhichIsWhatAFloatInARepeatDoes() {
            assertThat(Runs.longestRun(List.of(1, 0, 0, 1, 1), 1)).isEqualTo(3);
            assertThat(Runs.longestRun(List.of(1, 0, 0, 1, 1), 1, false)).isEqualTo(2);
        }

        @Test
        void isZeroWhenTheValueNeverAppears() {
            assertThat(Runs.longestRun(List.of(0, 0, 0), 1)).isEqualTo(0);
        }

        @Test
        void readsAPlainWeaveColumnAsAFloatOfOne() {
            assertThat(Runs.longestRun(List.of(1, 0, 1, 0, 1, 0), 1)).isEqualTo(1);
            assertThat(Runs.longestRun(List.of(1, 0, 1, 0, 1, 0), 0)).isEqualTo(1);
        }
    }

    @Nested
    class CountRuns {

        @Test
        void countsInterlacementsMergingTheWrap() {
            assertThat(Runs.countRuns(List.of(1, 0, 0, 1, 1), 1)).isEqualTo(1);
            assertThat(Runs.countRuns(List.of(1, 0, 0, 1, 1), 1, false)).isEqualTo(2);
            assertThat(Runs.countRuns(List.of(1, 0, 1, 0), 1)).isEqualTo(2);
        }
    }

    @Nested
    class RunHistogram {

        @Test
        void bucketsRunsByLength() {
            Map<Integer, Integer> hist = Runs.runHistogram(List.of(1, 1, 0, 1, 0, 1, 1, 0), 1);
            assertThat(hist.get(2)).isEqualTo(2);
            assertThat(hist.get(1)).isEqualTo(1);
            assertThat(hist.keySet()).containsExactly(1, 2);
        }

        @Test
        void isEmptyWhenNothingMatches() {
            assertThat(Runs.runHistogram(List.of(0, 0), 1)).isEmpty();
        }
    }

    @Nested
    class RunsOver {

        @Test
        void findsFloatsLongerThanTheLimitAndExcludesTheLimitItself() {
            List<Run<Integer>> found = Runs.runsOver(List.of(1, 1, 1, 0, 1, 1, 1, 1), 1, 3);
            assertThat(found).hasSize(1);
            assertThat(found.get(0).length()).isEqualTo(7);
            assertThat(Runs.runsOver(List.of(1, 1, 1, 0), 1, 3)).isEmpty();
        }
    }

    @Nested
    class ExpandRuns {

        @Test
        void roundTripsASequenceThroughItsRunForm() {
            List<Integer> seq = List.of(1, 1, 0, 0, 0, 1, 0);
            assertThat(Runs.expandRuns(Runs.runs(seq))).isEqualTo(seq);
        }

        @Test
        void rejectsARunWithNoLength() {
            assertThatThrownBy(() -> Runs.expandRuns(List.of(new Run<>(1, 0, 0, false))))
                    .hasMessageContaining("at least 1");
        }
    }

    @Nested
    class FormatRuns {

        @Test
        void writesCompactNotationByDefault() {
            assertThat(Runs.formatRuns(List.of(1, 1, 0, 1))).isEqualTo("1x2 0x1 1x1");
        }

        @Test
        void writesTheLongFormWhenAsked() {
            assertThat(Runs.formatRuns(List.of(1, 1, 0), Runs.Style.LONG)).isEqualTo("2 of 1, 1 of 0");
        }

        @Test
        void rejectsAnUnknownStyle() {
            assertThatThrownBy(() -> Runs.formatRuns(List.of(1), Runs.Style.parse("fancy")))
                    .hasMessageContaining("compact, long");
        }
    }
}
