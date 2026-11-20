package io.github.rkaushikdev.heddlewright.weave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.analysis.Interlacement;
import io.github.rkaushikdev.heddlewright.draft.Drawdown;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.weave.Crepe.SatinOptions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CrepeTest {

    private static Matrix twill8(String notation) {
        return Twill.twillWeave(notation, Twill.Options.DEFAULT.size(8, 8));
    }

    @Nested
    class LongestLine {

        @Test
        void readsATwillAsRunningTwoThreads() {
            assertThat(Crepe.longestLine(Twill.twillWeave("2/2"))).isEqualTo(new Crepe.Line(2, 2, 2));
        }

        @Test
        void readsPlainWeaveAsRunningOne() {
            assertThat(Crepe.longestLine(Plain.plainWeave()).longest()).isEqualTo(1);
        }

        @Test
        void countsTheDownRunsAsWellAsTheUpOnes() {
            assertThat(Crepe.longestLine(Twill.twillWeave("3/1")).longest()).isEqualTo(3);
        }
    }

    @Nested
    class DiagonalScore {

        @Test
        void isOneForATwillWhereEveryCellContinuesTheOneBelowIt() {
            assertThat(Crepe.diagonalScore(Twill.twillWeave("2/2"))).isEqualTo(1);
            assertThat(Crepe.diagonalScore(Twill.twillWeave("3/1"))).isEqualTo(1);
        }

        @Test
        void readsPlainWeaveAsFullyDiagonalTooBecauseItIsAOneOverOneTwill() {
            assertThat(Crepe.diagonalScore(Plain.plainWeave())).isEqualTo(1);
        }

        @Test
        void isNothingForAClothWhoseThreadsHaveBeenRearrangedOutOfLine() {
            assertThat(Crepe.diagonalScore(Crepe.rearrangedCrepe(twill8("2/2"))))
                    .isEqualTo(0);
        }
    }

    @Nested
    class SatinCrepe {

        private final Matrix crepe = Crepe.satinCrepe(SatinOptions.DEFAULT.threads(8));

        @Test
        void buildsARepeatTheSizeOfTheSatinItCameFrom() {
            assertThat(crepe.dimensions()).isEqualTo(new Matrix.Dimensions(8, 8));
        }

        @Test
        void leavesTheClothBalancedRatherThanWarpOrWeftFaced() {
            assertThat(Drawdown.warpFaceRatio(crepe)).isGreaterThan(0.3).isLessThan(0.7);
        }

        @Test
        void keepsEveryLineShort() {
            assertThat(Crepe.longestLine(crepe).longest()).isLessThanOrEqualTo(3);
        }

        @Test
        void readsAsACrepe() {
            assertThat(Crepe.readsAsCrepe(crepe).reads()).isTrue();
        }

        @Test
        void takesADifferentGround() {
            Matrix other = Crepe.satinCrepe(SatinOptions.DEFAULT.threads(8).ground(twill8("3/1")));
            assertThat(Crepe.longestLine(other).longest()).isLessThanOrEqualTo(3);
        }

        @Test
        void refusesAThreadCountWithNoRegularSatin() {
            assertThatThrownBy(() -> Crepe.satinCrepe(SatinOptions.DEFAULT.threads(6)))
                    .isInstanceOf(DraftException.class);
        }
    }

    @Nested
    class RearrangedCrepe {

        private final Matrix twill = twill8("2/2");
        private final Matrix crepe = Crepe.rearrangedCrepe(twill);

        @Test
        void takesTheDiagonalOutOfATwill() {
            assertThat(Crepe.diagonalScore(twill)).isEqualTo(1);
            assertThat(Crepe.diagonalScore(crepe)).isLessThan(0.5);
        }

        @Test
        void leavesTheInterlacementOfTheClothAlone() {
            assertThat(Interlacement.interlacementFactor(crepe)).isEqualTo(Interlacement.interlacementFactor(twill));
        }

        @Test
        void leavesTheBalanceOfTheClothAlone() {
            assertThat(Drawdown.warpFaceRatio(crepe)).isEqualTo(Drawdown.warpFaceRatio(twill));
        }

        @Test
        void readsAsACrepeWhereTheTwillItCameFromDidNot() {
            assertThat(Crepe.readsAsCrepe(twill).reads()).isFalse();
            assertThat(Crepe.readsAsCrepe(crepe).reads()).isTrue();
        }

        @Test
        void refusesARepeatWithNoSatinOrderToRearrangeBy() {
            assertThatThrownBy(() -> Crepe.rearrangedCrepe(Twill.twillWeave("2/2")))
                    .isInstanceOf(DraftException.class);
        }
    }

    @Nested
    class ReadsAsCrepe {

        @Test
        void namesWhatAClothFailsOn() {
            assertThat(Crepe.readsAsCrepe(twill8("2/2")).findings().get(0).code())
                    .isEqualTo("diagonal");
        }

        @Test
        void failsAClothWithALongLine() {
            Crepe.Reading reading = Crepe.readsAsCrepe(twill8("3/1"), 2, 0.8);
            assertThat(reading.findings().stream().map(Crepe.Finding::code)).contains("long_line");
        }

        @Test
        void takesALooserReadingWhenADesignerWantsOne() {
            assertThat(Crepe.readsAsCrepe(twill8("2/2"), 3, 1).reads()).isTrue();
        }
    }
}
