package io.github.rkaushikdev.heddlewright.weave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.weave.Combine.Overlay;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CombineTest {

    @Nested
    class ExpandWeave {

        @Test
        void repeatsEachThreadWhichIsHowPlainWeaveBecomesBasket() {
            assertThat(Combine.expandWeave(Plain.plainWeave(), 2, 2)).isEqualTo(Plain.basketWeave(2));
        }

        @Test
        void takesDifferentFactorsDownAndAcross() {
            assertThat(Combine.expandWeave(Plain.plainWeave(), 1, 3).format('o', '.'))
                    .isEqualTo("ooo...\n...ooo");
        }

        @Test
        void aFactorOfOneLeavesTheWeaveAlone() {
            assertThat(Combine.expandWeave(Plain.plainWeave(), 1, 1).format()).isEqualTo("X.\n.X");
        }

        @Test
        void refusesAFactorOfZero() {
            assertThatThrownBy(() -> Combine.expandWeave(Plain.plainWeave(), 0))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class AlignWeaves {

        @Test
        void growsBothToTheSmallestCommonRepeat() {
            Combine.Aligned aligned = Combine.alignWeaves(Plain.plainWeave(), Twill.twillWeave("2/2"));
            assertThat(aligned.picks()).isEqualTo(4);
            assertThat(aligned.ends()).isEqualTo(4);
            assertThat(aligned.a().format()).isEqualTo("X.X.\n.X.X\nX.X.\n.X.X");
        }

        @Test
        void usesTheLeastCommonMultipleNotTheLargerOfTheTwo() {
            Combine.Aligned aligned = Combine.alignWeaves(Matrix.parse("X.\n.X"), Matrix.parse("X..\n.X.\n..X"));
            assertThat(aligned.picks()).isEqualTo(6);
            assertThat(aligned.ends()).isEqualTo(6);
        }
    }

    @Nested
    class OverlayWeaves {

        private final Matrix twill = Twill.twillWeave("2/2");

        @Test
        void namesItsThreeModes() {
            assertThat(Overlay.OVERLAY_MODES.stream().map(Overlay::toString))
                    .containsExactly("over", "under", "either");
        }

        @Test
        void raisesACellWhenEitherWeaveRaisesIt() {
            assertThat(Combine.overlayWeaves(Plain.plainWeave(), twill, Overlay.OVER)
                            .format('o', '.'))
                    .isEqualTo("ooo.\n.ooo\no.oo\noo.o");
        }

        @Test
        void raisesACellOnlyWhenBothDoWhichLeavesTheSharedDiagonal() {
            assertThat(Combine.overlayWeaves(Plain.plainWeave(), twill, Overlay.UNDER)
                            .format())
                    .isEqualTo("X...\n.X..\n..X.\n...X");
        }

        @Test
        void raisesACellWhenExactlyOneDoesWhichDrawsACrepe() {
            assertThat(Combine.overlayWeaves(Plain.plainWeave(), twill, Overlay.EITHER)
                            .format())
                    .isEqualTo(".XX.\n..XX\nX..X\nXX..");
        }

        @Test
        void theThreeModesAgreeOverLessUnderIsExactlyEither() {
            Matrix over = Combine.overlayWeaves(Plain.plainWeave(), twill, Overlay.OVER);
            Matrix under = Combine.overlayWeaves(Plain.plainWeave(), twill, Overlay.UNDER);
            Matrix either = Combine.overlayWeaves(Plain.plainWeave(), twill, Overlay.EITHER);
            for (int p = 0; p < 4; p += 1) {
                for (int e = 0; e < 4; e += 1) {
                    assertThat(over.get(p, e) - under.get(p, e)).isEqualTo(either.get(p, e));
                }
            }
        }

        @Test
        void defaultsToTheOverMode() {
            assertThat(Combine.overlayWeaves(Plain.plainWeave(), twill))
                    .isEqualTo(Combine.overlayWeaves(Plain.plainWeave(), twill, Overlay.OVER));
        }

        @Test
        void rejectsAModeItDoesNotKnow() {
            assertThatThrownBy(() -> Combine.overlayWeaves(Plain.plainWeave(), twill, Overlay.parse("beside")))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class InterleaveEnds {

        @Test
        void takesAlternateEndsFromEachWeave() {
            Matrix doubled = Combine.interleaveEnds(Plain.plainWeave(), Twill.twillWeave("2/2"));
            assertThat(doubled.dimensions()).isEqualTo(new Matrix.Dimensions(4, 8));
        }

        @Test
        void followsAnUnevenOrderTwoEndsOfTheFaceToOneOfTheBack() {
            Matrix cloth = Combine.interleaveEnds(Matrix.parse("XX\nXX"), Matrix.parse("..\n.."), List.of(0, 0, 1));
            assertThat(cloth.format()).isEqualTo("XX.XX.\nXX.XX.");
        }

        @Test
        void rejectsAnOrderNamingAWeaveThatIsNotThere() {
            assertThatThrownBy(() -> Combine.interleaveEnds(Plain.plainWeave(), Plain.plainWeave(), List.of(0, 2)))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class InterleavePicks {

        @Test
        void takesAlternatePicksFromEachWeave() {
            assertThat(Combine.interleavePicks(Matrix.parse("XX\nXX"), Matrix.parse("..\n.."))
                            .format())
                    .isEqualTo("XX\n..\nXX\n..");
        }

        @Test
        void doublesThePickCountAgainstTheAlignedRepeat() {
            assertThat(Combine.interleavePicks(Plain.plainWeave(), Twill.twillWeave("2/2"))
                            .dimensions())
                    .isEqualTo(new Matrix.Dimensions(8, 4));
        }
    }

    @Nested
    class StitchWeave {

        @Test
        void forcesTheNamedCellsDown() {
            assertThat(Combine.stitchWeave(Matrix.parse("XX\nXX"), Matrix.parse("X.\n.."))
                            .format())
                    .isEqualTo(".X\nXX");
        }

        @Test
        void forcesThemUpWhenAsked() {
            assertThat(Combine.stitchWeave(Matrix.parse("..\n.."), Matrix.parse("X.\n.."), true)
                            .format())
                    .isEqualTo("X.\n..");
        }

        @Test
        void leavesCellsTheStitchWeaveDoesNotName() {
            Matrix cloth = Twill.twillWeave("2/2");
            assertThat(Combine.stitchWeave(cloth, Matrix.parse("....\n....\n....\n....")))
                    .isEqualTo(cloth);
        }
    }

    @Nested
    class CutWeave {

        private final Matrix twill = Twill.twillWeave("2/2");

        @Test
        void takesAWindowOutOfTheRepeat() {
            assertThat(Combine.cutWeave(twill, 2, 2).format()).isEqualTo("XX\n.X");
        }

        @Test
        void startsWhereItIsToldAndWrapsPastTheEdge() {
            assertThat(Combine.cutWeave(twill, 3, 3, OptionalInt.of(2), OptionalInt.of(2))
                            .format())
                    .isEqualTo("XX\n.X");
        }

        @Test
        void returnsTheWholeRepeatWhenNoSizeIsGiven() {
            assertThat(Combine.cutWeave(twill)).isEqualTo(twill);
        }
    }

    @Nested
    class AssertInterlaces {

        @Test
        void passesARealWeave() {
            assertThat(Combine.assertInterlaces(Plain.plainWeave())).isEqualTo(Plain.plainWeave());
        }

        @Test
        void refusesARepeatThatIsAllWarpOrAllWeft() {
            assertThatThrownBy(() -> Combine.assertInterlaces(Matrix.parse("XX\nXX")))
                    .isInstanceOf(DraftException.class);
            assertThatThrownBy(() -> Combine.assertInterlaces(Matrix.parse("..\n.."), "backing"))
                    .hasMessageContaining("backing");
        }
    }
}
