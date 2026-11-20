package io.github.rkaushikdev.heddlewright.weave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.heddlewright.draft.Drawdown;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.weave.Satin.Face;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SatinTest {

    @Nested
    class RegularSatinMoves {

        @Test
        void givesBothMovesForAFiveThreadSatin() {
            assertThat(Satin.regularSatinMoves(5)).containsExactly(2, 3);
        }

        @Test
        void givesTheTwoOddMovesForAnEightThreadSatin() {
            assertThat(Satin.regularSatinMoves(8)).containsExactly(3, 5);
        }

        @Test
        void listsEveryCoprimeMoveOnASevenThreadRepeat() {
            assertThat(Satin.regularSatinMoves(7)).containsExactly(2, 3, 4, 5);
        }

        @Test
        void isEmptyForFourAndSixWhichIsWhyThoseSatinsAreIrregular() {
            assertThat(Satin.regularSatinMoves(4)).isEmpty();
            assertThat(Satin.regularSatinMoves(6)).isEmpty();
            assertThat(Satin.hasRegularSatin(4)).isFalse();
            assertThat(Satin.hasRegularSatin(6)).isFalse();
            assertThat(Satin.hasRegularSatin(5)).isTrue();
            assertThat(Satin.hasRegularSatin(8)).isTrue();
        }
    }

    @Nested
    class AssertSatinMove {

        @Test
        void acceptsALegalMove() {
            assertThat(Satin.assertSatinMove(5, 2)).isEqualTo(2);
            assertThat(Satin.assertSatinMove(8, 3)).isEqualTo(3);
        }

        @Test
        void refusesAMoveOfOneEitherWayWhichWouldDrawATwill() {
            assertThatThrownBy(() -> Satin.assertSatinMove(5, 1)).hasMessageContaining("twill line");
            assertThatThrownBy(() -> Satin.assertSatinMove(5, 4)).hasMessageContaining("twill line");
        }

        @Test
        void refusesAMoveSharingAFactorWithTheRepeatAndSaysWhichFactor() {
            DraftException err = catchThrowableOfType(DraftException.class, () -> Satin.assertSatinMove(8, 2));
            assertThat(err).isNotNull();
            assertThat(err.detail().get("commonFactor")).isEqualTo(Json.of(2));
        }

        @Test
        void refusesARepeatTooSmallToHoldASatinAtAll() {
            assertThatThrownBy(() -> Satin.assertSatinMove(4, 2)).hasMessageContaining("at least five threads");
        }

        @Test
        void refusesAMoveAsLargeAsTheRepeat() {
            assertThatThrownBy(() -> Satin.assertSatinMove(5, 5)).isInstanceOf(DraftException.class);
        }
    }

    @Nested
    class SatinWeave {

        @Test
        void bindsOncePerPickOnAFiveThreadSatinWithAMoveOfTwo() {
            assertThat(Satin.satinWeave(5, 2).format('o', '.')).isEqualTo(".oooo\noo.oo\noooo.\no.ooo\nooo.o");
        }

        @Test
        void showsFourFifthsWarpOnAFiveThreadWarpSatin() {
            assertThat(Drawdown.warpFaceRatio(Satin.satinWeave(5, 2))).isEqualTo(0.8);
        }

        @Test
        void aSateenIsTheSameClothWithTheFaceSwapped() {
            Matrix sateen = Satin.satinWeave(5, 2, Face.WEFT);
            assertThat(Drawdown.warpFaceRatio(sateen)).isEqualTo(0.2);
            assertThat(sateen).isEqualTo(Satin.reverseSatin(Satin.satinWeave(5, 2)));
        }

        @Test
        void showsSevenEighthsWarpOnAnEightThreadSatin() {
            assertThat(Drawdown.warpFaceRatio(Satin.satinWeave(8, 3))).isEqualTo(0.875);
        }

        @Test
        void namesTheTwoFaces() {
            assertThat(Face.SATIN_FACES.stream().map(Face::toString)).containsExactly("warp", "weft");
            assertThatThrownBy(() -> Satin.satinWeave(5, 2, Face.parse("both")))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class BindingPoints {

        @Test
        void walksTheEndsByTheMoveNumber() {
            assertThat(Satin.bindingPoints(Satin.satinWeave(5, 2))).containsExactly(1, 3, 5, 2, 4);
            assertThat(Satin.bindingPoints(Satin.satinWeave(5, 3))).containsExactly(1, 4, 2, 5, 3);
        }

        @Test
        void visitsEveryEndExactlyOnce() {
            for (int move : Satin.regularSatinMoves(8)) {
                assertThat(Satin.bindingPoints(Satin.satinWeave(8, move)).stream()
                                .sorted())
                        .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
            }
        }

        @Test
        void readsTheWeftFaceFromTheOtherSide() {
            assertThat(Satin.bindingPoints(Satin.satinWeave(5, 2, Face.WEFT), Face.WEFT))
                    .containsExactly(1, 3, 5, 2, 4);
        }
    }

    @Nested
    class BindingPointsIsolated {

        @Test
        void isTrueForEveryRegularSatin() {
            for (int threads : List.of(5, 7, 8, 9, 10)) {
                for (int move : Satin.regularSatinMoves(threads)) {
                    assertThat(Satin.bindingPointsIsolated(Satin.satinWeave(threads, move)))
                            .isTrue();
                }
            }
        }

        @Test
        void isFalseForATwillWhereThePointsSitSideBySide() {
            Matrix twillLike = Matrix.of(
                    new int[][] {{0, 1, 1, 1, 1}, {1, 0, 1, 1, 1}, {1, 1, 0, 1, 1}, {1, 1, 1, 0, 1}, {1, 1, 1, 1, 0}});
            assertThat(Satin.bindingPointsIsolated(twillLike)).isFalse();
        }
    }

    @Nested
    class IrregularSatin {

        @Test
        void buildsTheFourThreadSatinFromItsWrittenMoveSequence() {
            Matrix repeat = Satin.irregularSatin(4, Satin.fourThreadSatinMoves());
            assertThat(Drawdown.warpFaceRatio(repeat)).isEqualTo(0.75);
            assertThat(Satin.bindingPoints(repeat)).containsExactly(1, 3, 2, 4);
        }

        @Test
        void buildsTheSixThreadSatinAndKeepsItsBindingPointsApart() {
            Matrix repeat = Satin.irregularSatin(6, Satin.sixThreadSatinMoves());
            assertThat(Satin.bindingPoints(repeat)).containsExactly(1, 3, 5, 2, 6, 4);
            assertThat(Satin.bindingPointsIsolated(repeat)).isTrue();
            assertThat(Drawdown.warpFaceRatio(repeat)).isCloseTo(5.0 / 6, within(0.5e-10));
        }

        @Test
        void cannotKeepTheFourThreadPointsApartWhichIsTheHonestReading() {
            assertThat(Satin.bindingPointsIsolated(Satin.irregularSatin(4, Satin.fourThreadSatinMoves())))
                    .isFalse();
        }

        @Test
        void refusesASequenceThatDoesNotReachEveryThread() {
            assertThatThrownBy(() -> Satin.irregularSatin(6, List.of(2, 2, 2, 2, 2, 2)))
                    .hasMessageContaining("does not reach every thread");
        }

        @Test
        void refusesASequenceOfTheWrongLength() {
            assertThatThrownBy(() -> Satin.irregularSatin(6, List.of(2, 3)))
                    .hasMessageContaining("one move per thread");
        }

        @Test
        void canBuildARegularSatinTooGivenItsConstantMove() {
            assertThat(Satin.irregularSatin(5, List.of(2, 2, 2, 2, 2))).isEqualTo(Satin.satinWeave(5, 2));
        }
    }

    @Nested
    class ReverseSatin {

        @Test
        void isItsOwnInverse() {
            Matrix repeat = Satin.satinWeave(7, 3);
            assertThat(Satin.reverseSatin(Satin.reverseSatin(repeat))).isEqualTo(repeat);
        }
    }
}
