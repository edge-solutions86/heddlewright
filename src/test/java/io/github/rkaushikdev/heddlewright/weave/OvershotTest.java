package io.github.rkaushikdev.heddlewright.weave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.heddlewright.analysis.Floats;
import io.github.rkaushikdev.heddlewright.draft.Draft;
import io.github.rkaushikdev.heddlewright.draft.Drawdown;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.weave.Overshot.Options;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OvershotTest {

    @Nested
    class TheBlocks {

        @Test
        void namesFourEachOnAnOverlappingPairOfShafts() {
            assertThat(Overshot.BLOCKS).containsExactly("A", "B", "C", "D");
            assertThat(Overshot.blockShafts("A")).containsExactly(1, 2);
            assertThat(Overshot.blockShafts("D")).containsExactly(4, 1);
        }

        @Test
        void hasEveryBlockSharingAShaftWithTheNextOneRound() {
            assertThat(Overshot.blocksAdjoin("A", "B")).isTrue();
            assertThat(Overshot.blocksAdjoin("D", "A")).isTrue();
            assertThat(Overshot.blocksAdjoin("A", "C")).isFalse();
        }

        @Test
        void namesWhatCanFollowABlock() {
            assertThat(Overshot.blocksAfter("A")).containsExactly("B", "D");
            assertThat(Overshot.blocksAfter("B")).containsExactly("A", "C");
        }

        @Test
        void refusesABlockItDoesNotHave() {
            assertThatThrownBy(() -> Overshot.blockShafts("E")).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class OvershotThreading {

        @Test
        void threadsEachBlockOnItsPairForAsManyUnitsAsItIsGiven() {
            assertThat(Overshot.overshotThreading(List.of("A"), Options.DEFAULT.units(2)))
                    .containsExactly(1, 2, 1, 2);
            assertThat(Overshot.overshotThreading(List.of("A", "B"), Options.DEFAULT.units(1)))
                    .containsExactly(1, 2, 2, 3);
        }

        @Test
        void refusesTwoBlocksThatDoNotShareAShaft() {
            DraftException err =
                    catchThrowableOfType(DraftException.class, () -> Overshot.overshotThreading(List.of("A", "C")));
            assertThat(err).isNotNull();
            assertThat(err.detail().get("from")).isEqualTo(Json.of("A"));
            assertThat(err.detail().get("to")).isEqualTo(Json.of("C"));
        }

        @Test
        void threadsTwoEndsPerUnit() {
            assertThat(Overshot.overshotThreading(List.of("A", "B", "C"), Options.DEFAULT.units(3)))
                    .hasSize(3 * 3 * 2);
        }
    }

    @Nested
    class OvershotTieup {

        @Test
        void givesFourPatternTreadlesAndTwoTabbyOnes() {
            assertThat(Overshot.overshotTieup())
                    .isEqualTo(List.of(
                            List.of(1, 2), List.of(2, 3), List.of(3, 4), List.of(4, 1), List.of(1, 3), List.of(2, 4)));
        }

        @Test
        void numbersThePatternTreadlesInBlockOrder() {
            assertThat(Overshot.treadleForBlock("A")).isEqualTo(1);
            assertThat(Overshot.treadleForBlock("D")).isEqualTo(4);
            assertThat(Overshot.tabbyTreadles()).containsExactly(5, 6);
        }
    }

    @Nested
    class OvershotTreadling {

        @Test
        void putsATabbyPickAfterEveryPatternPick() {
            assertThat(Overshot.overshotTreadling(List.of("A"), Options.DEFAULT.units(2)))
                    .isEqualTo(List.of(List.of(1), List.of(5), List.of(1), List.of(6)));
        }

        @Test
        void alternatesTheTabbyAcrossABlockChange() {
            assertThat(Overshot.overshotTreadling(List.of("A", "B"), Options.DEFAULT.units(1)))
                    .isEqualTo(List.of(List.of(1), List.of(5), List.of(2), List.of(6)));
        }

        @Test
        void startsOnTheOtherTabbyWhenAsked() {
            assertThat(Overshot.overshotTreadling(List.of("A"), new Options(1, 1)))
                    .isEqualTo(List.of(List.of(1), List.of(6)));
        }

        @Test
        void weavesTwiceAsManyPicksAsThereArePatternPicks() {
            assertThat(Overshot.overshotTreadling(List.of("A", "B", "C"), Options.DEFAULT.units(2)))
                    .hasSize(3 * 2 * 2);
        }

        @Test
        void refusesATabbyStartThatIsNeitherOfTheTwo() {
            assertThatThrownBy(() -> Overshot.overshotTreadling(List.of("A"), Options.DEFAULT.startTabby(2)))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class OvershotDraft {

        private final Draft draft =
                Draft.create(Overshot.overshotDraft(List.of("A", "B", "C", "D"), Options.DEFAULT.units(2)));

        @Test
        void isAFourShaftDraftOnSixTreadles() {
            assertThat(draft.shafts()).isEqualTo(4);
            assertThat(draft.treadles()).isEqualTo(6);
            assertThat(draft.ends()).isEqualTo(16);
            assertThat(draft.picks()).isEqualTo(16);
        }

        @Test
        void weavesAClothWithMoreWarpDownThanUpBecauseThePatternFloats() {
            Matrix cloth = Drawdown.drawdownFromDraft(draft);
            assertThat(Drawdown.warpFaceRatio(cloth)).isGreaterThan(0.4).isLessThan(0.6);
        }

        @Test
        void floatsThePatternWeftOverTheBlockItIsWeaving() {
            assertThat(Floats.longestFloat(Drawdown.drawdownFromDraft(draft)).weft())
                    .isGreaterThanOrEqualTo(4);
        }

        @Test
        void namesItselfAfterTheBlockRun() {
            assertThat(draft.name()).isEqualTo("overshot ABCD");
        }
    }

    @Nested
    class PatternFloat {

        @Test
        void isTwoEndsPerUnit() {
            assertThat(Overshot.patternFloat(2)).isEqualTo(4);
            assertThat(Overshot.patternFloat(5)).isEqualTo(10);
        }

        @Test
        void passesAFloatInsideTheLimitAndRefusesOnePastIt() {
            assertThat(Overshot.assertFloatWithin(6, 3)).isEqualTo(6);
            assertThatThrownBy(() -> Overshot.assertFloatWithin(5, 3)).hasMessageContaining("longer than the cloth");
        }
    }
}
