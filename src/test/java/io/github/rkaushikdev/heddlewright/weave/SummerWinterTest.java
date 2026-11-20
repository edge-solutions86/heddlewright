package io.github.rkaushikdev.heddlewright.weave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.analysis.Floats;
import io.github.rkaushikdev.heddlewright.draft.Draft;
import io.github.rkaushikdev.heddlewright.draft.Drawdown;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SummerWinterTest {

    @Nested
    class ShaftsAndBlocks {

        @Test
        void keepsTwoShaftsBackForTheTies() {
            assertThat(SummerWinter.TIE_SHAFTS).containsExactly(1, 2);
            assertThat(SummerWinter.shaftsForBlocks(4)).isEqualTo(6);
            assertThat(SummerWinter.blocksForShafts(6)).isEqualTo(4);
        }

        @Test
        void putsEachBlockOnTheNextShaftAfterTheTies() {
            assertThat(SummerWinter.shaftForBlock(1)).isEqualTo(3);
            assertThat(SummerWinter.shaftForBlock(4)).isEqualTo(6);
        }

        @Test
        void refusesALoomWithNothingLeftAfterTheTies() {
            assertThatThrownBy(() -> SummerWinter.blocksForShafts(2)).isInstanceOf(DraftException.class);
            assertThatThrownBy(() -> SummerWinter.blocksForShafts(0)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class SummerWinterThreading {

        @Test
        void threadsAUnitAsTiePatternTiePattern() {
            assertThat(SummerWinter.summerWinterThreading(List.of(1))).containsExactly(1, 3, 2, 3);
        }

        @Test
        void takesFourEndsPerUnit() {
            assertThat(SummerWinter.summerWinterThreading(List.of(1), 3)).hasSize(12);
            assertThat(SummerWinter.endsForBlocks(List.of(1, 2), 3)).isEqualTo(24);
        }

        @Test
        void changesOnlyThePatternShaftBetweenBlocks() {
            assertThat(SummerWinter.summerWinterThreading(List.of(1, 2))).containsExactly(1, 3, 2, 3, 1, 4, 2, 4);
        }

        @Test
        void alwaysThreadsBothTies() {
            assertThat(SummerWinter.assertTiesThreaded(SummerWinter.summerWinterThreading(List.of(1, 2, 3))))
                    .isNotEmpty();
            assertThatThrownBy(() -> SummerWinter.assertTiesThreaded(List.of(3, 4, 3, 4)))
                    .hasMessageContaining("tie shaft 1");
        }
    }

    @Nested
    class SummerWinterTieup {

        @Test
        void givesTwoTreadlesPerBlockAndTwoForTheTabby() {
            assertThat(SummerWinter.summerWinterTieup(2))
                    .isEqualTo(List.of(
                            List.of(1, 3), List.of(2, 3), List.of(1, 4), List.of(2, 4), List.of(1, 2), List.of(3, 4)));
        }

        @Test
        void numbersTheTreadlesForABlock() {
            assertThat(SummerWinter.treadlesForBlock(1)).containsExactly(1, 2);
            assertThat(SummerWinter.treadlesForBlock(3)).containsExactly(5, 6);
            assertThat(SummerWinter.tabbyTreadles(3)).containsExactly(7, 8);
        }

        @Test
        void liftsEveryPatternShaftOnTheSecondTabbyTreadle() {
            List<List<Integer>> tieup = SummerWinter.summerWinterTieup(4);
            assertThat(tieup.get(tieup.size() - 1)).containsExactly(3, 4, 5, 6);
        }
    }

    @Nested
    class SummerWinterTreadling {

        @Test
        void takesBothTreadlesOfABlockInTurn() {
            assertThat(SummerWinter.summerWinterTreadling(List.of(1), 2)).isEqualTo(List.of(List.of(1), List.of(2)));
            assertThat(SummerWinter.summerWinterTreadling(List.of(2), 2)).isEqualTo(List.of(List.of(3), List.of(4)));
        }

        @Test
        void repeatsForEachUnit() {
            assertThat(SummerWinter.summerWinterTreadling(List.of(1), 2, 2))
                    .isEqualTo(List.of(List.of(1), List.of(2), List.of(1), List.of(2)));
        }

        @Test
        void refusesABlockTheLoomIsNotTiedFor() {
            assertThatThrownBy(() -> SummerWinter.summerWinterTreadling(List.of(3), 2))
                    .hasMessageContaining("past the ones the loom is tied for");
        }
    }

    @Nested
    class SummerWinterDraft {

        private final Draft draft = Draft.create(SummerWinter.summerWinterDraft(List.of(1, 2, 1), 2));

        @Test
        void sizesTheLoomFromTheHighestBlockUsed() {
            assertThat(draft.shafts()).isEqualTo(4);
            assertThat(draft.treadles()).isEqualTo(6);
        }

        @Test
        void threadsFourEndsPerUnitAndWeavesTwoPicksPerUnit() {
            assertThat(draft.ends()).isEqualTo(24);
            assertThat(draft.picks()).isEqualTo(12);
        }

        @Test
        void neverFloatsTheWeftFurtherThanTwoEnds() {
            assertThat(Floats.longestFloat(Drawdown.drawdownFromDraft(draft)).weft())
                    .isLessThanOrEqualTo(SummerWinter.maximumFloat() + 1);
        }

        @Test
        void namesItselfAfterTheBlockRun() {
            assertThat(draft.name()).isEqualTo("summer and winter 121");
        }

        @Test
        void bindsEveryEndIntoTheCloth() {
            Matrix cloth = Drawdown.drawdownFromDraft(draft);
            for (List<Integer> row : cloth.allRows()) {
                assertThat(row).contains(1);
            }
        }
    }
}
