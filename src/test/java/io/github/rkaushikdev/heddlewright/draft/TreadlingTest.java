package io.github.rkaushikdev.heddlewright.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TreadlingTest {

    @Nested
    class NormaliseTreadling {

        @Test
        void wrapsABareNumberIntoASingleTreadlePick() {
            assertThat(Treadling.normaliseTreadling(List.of(1, 2, 3), 4))
                    .isEqualTo(List.of(List.of(1), List.of(2), List.of(3)));
        }

        @Test
        void sortsAndDeduplicatesATwoTreadlePick() {
            assertThat(Treadling.normaliseTreadling(List.of(List.of(3, 1, 3)), 4))
                    .isEqualTo(List.of(List.of(1, 3)));
        }

        @Test
        void keepsABlankPickRatherThanDroppingIt() {
            assertThat(Treadling.normaliseTreadling(List.of(List.of(), List.of(1)), 4))
                    .isEqualTo(List.of(List.of(), List.of(1)));
        }

        @Test
        void rejectsATreadleTheLoomDoesNotHave() {
            assertThatThrownBy(() -> Treadling.normaliseTreadling(List.of(1, 9), 4))
                    .hasMessageContaining("treadling[1][0]");
        }
    }

    @Nested
    class AssertNoBlankPicks {

        @Test
        void passesATreadlingWithEveryPickPressed() {
            assertThat(Treadling.assertNoBlankPicks(List.of(1, 2), 4)).isEqualTo(List.of(List.of(1), List.of(2)));
        }

        @Test
        void namesTheBlankPicksBy1BasedPosition() {
            DraftException err = catchThrowableOfType(
                    DraftException.class,
                    () -> Treadling.assertNoBlankPicks(List.of(List.of(1), List.of(), List.of(2), List.of()), 4));
            assertThat(err).isNotNull();
            assertThat(err.detail().get("picks")).isEqualTo(Json.arr(2, 4));
        }
    }

    @Nested
    class StraightTreadling {

        @Test
        void walksTheTreadlesInOrder() {
            assertThat(Treadling.straightTreadling(4))
                    .isEqualTo(List.of(List.of(1), List.of(2), List.of(3), List.of(4)));
            assertThat(Treadling.straightTreadling(3, 5))
                    .isEqualTo(List.of(List.of(1), List.of(2), List.of(3), List.of(1), List.of(2)));
        }

        @Test
        void startsElsewhereAndRunsBackwardsWhenAsked() {
            assertThat(Treadling.straightTreadling(4, 4, 2, 1))
                    .isEqualTo(List.of(List.of(2), List.of(3), List.of(4), List.of(1)));
            assertThat(Treadling.straightTreadling(4, 4, 1, -1))
                    .isEqualTo(List.of(List.of(1), List.of(4), List.of(3), List.of(2)));
        }
    }

    @Nested
    class PointTreadling {

        @Test
        void turnsWithoutRepeatingTheEndPick() {
            assertThat(Treadling.pointTreadling(4))
                    .isEqualTo(List.of(List.of(1), List.of(2), List.of(3), List.of(4), List.of(3), List.of(2)));
        }

        @Test
        void mirrorsTheShapeOfAPointDrawOnTheSameCount() {
            assertThat(Treadling.pointTreadling(5).stream().map(pick -> pick.get(0)))
                    .containsExactlyElementsOf(Threading.pointDraw(5));
        }

        @Test
        void needsTwoTreadles() {
            assertThatThrownBy(() -> Treadling.pointTreadling(1)).isInstanceOf(DraftException.class);
        }
    }

    @Nested
    class TrompAsWrit {

        @Test
        void treadlesInThreadingOrder() {
            assertThat(Treadling.trompAsWrit(List.of(1, 2, 3, 2)))
                    .isEqualTo(List.of(List.of(1), List.of(2), List.of(3), List.of(2)));
        }

        @Test
        void takesAShaftToTreadleMapForALoomTiedDifferently() {
            assertThat(Treadling.trompAsWrit(List.of(1, 2, 3, 4), (shaft, index) -> 5 - shaft))
                    .isEqualTo(List.of(List.of(4), List.of(3), List.of(2), List.of(1)));
        }

        @Test
        void passesTheEndIndexToTheMap() {
            List<Integer> seen = new ArrayList<>();
            Treadling.trompAsWrit(List.of(1, 1, 1), (shaft, index) -> {
                seen.add(index);
                return shaft;
            });
            assertThat(seen).containsExactly(0, 1, 2);
        }

        @Test
        void rejectsAMapThatReturnsSomethingOffTheLoom() {
            assertThatThrownBy(() -> Treadling.trompAsWrit(List.of(1), (shaft, index) -> 0))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class AdvancingTreadling {

        @Test
        void rollsEachBlockOnByTheAdvance() {
            assertThat(Treadling.advancingTreadling(8, 8, 4, 1))
                    .isEqualTo(List.of(
                            List.of(1),
                            List.of(2),
                            List.of(3),
                            List.of(4),
                            List.of(2),
                            List.of(3),
                            List.of(4),
                            List.of(5)));
        }

        @Test
        void anAdvanceEqualToTheBlockSizeIsAStraightTreadling() {
            assertThat(Treadling.advancingTreadling(4, 8, 4, 4)).isEqualTo(Treadling.straightTreadling(4, 8));
        }
    }

    @Nested
    class Boustrophedon {

        @Test
        void runsBackDownWithoutRepeatingTheTurningPicks() {
            assertThat(Treadling.boustrophedon(List.of(List.of(1), List.of(2), List.of(3))))
                    .isEqualTo(List.of(List.of(1), List.of(2), List.of(3), List.of(2)));
        }

        @Test
        void repeatsTheTurnsWhenAsked() {
            assertThat(Treadling.boustrophedon(List.of(List.of(1), List.of(2), List.of(3)), true))
                    .isEqualTo(List.of(List.of(1), List.of(2), List.of(3), List.of(3), List.of(2), List.of(1)));
        }

        @Test
        void leavesTheInputAlone() {
            List<List<Integer>> source = new ArrayList<>(List.of(List.of(1), List.of(2)));
            Treadling.boustrophedon(source);
            assertThat(source).isEqualTo(List.of(List.of(1), List.of(2)));
        }
    }

    @Nested
    class TreadlingRepeat {

        @Test
        void findsTheShortestBlockThatRebuildsTheSequence() {
            assertThat(Treadling.treadlingRepeat(List.of(1, 2, 1, 2, 1, 2), 4)).isEqualTo(2);
            assertThat(Treadling.treadlingRepeat(Treadling.straightTreadling(4, 8), 4))
                    .isEqualTo(4);
        }

        @Test
        void isTheWholeLengthWhenNothingRepeats() {
            assertThat(Treadling.treadlingRepeat(List.of(1, 2, 3), 4)).isEqualTo(3);
        }

        @Test
        void seesATwoTreadlePickAsOneSymbol() {
            assertThat(Treadling.treadlingRepeat(List.of(List.of(1, 2), List.of(3), List.of(1, 2), List.of(3)), 4))
                    .isEqualTo(2);
        }
    }

    @Nested
    class TreadleUsage {

        @Test
        void countsThePicksEachTreadleCarries() {
            assertThat(Treadling.treadleUsage(List.of(1, 1, 2), 4)).containsExactly(2, 1, 0, 0);
        }

        @Test
        void countsBothTreadlesOfADoubledPick() {
            assertThat(Treadling.treadleUsage(List.of(List.of(1, 2)), 2)).containsExactly(1, 1);
        }

        @Test
        void listsTheTreadlesNeverPressed() {
            assertThat(Treadling.unusedTreadles(List.of(1, 2), 4)).containsExactly(3, 4);
            assertThat(Treadling.unusedTreadles(List.of(1, 2, 3, 4), 4)).isEmpty();
        }
    }

    @Nested
    class ExtendTreadling {

        @Test
        void repeatsToTheRequestedPickCount() {
            assertThat(Treadling.extendTreadling(List.of(List.of(1), List.of(2)), 5))
                    .isEqualTo(List.of(List.of(1), List.of(2), List.of(1), List.of(2), List.of(1)));
        }

        @Test
        void acceptsBareNumbersAndNormalisesThem() {
            assertThat(Treadling.extendTreadling(List.of(1, 2), 3))
                    .isEqualTo(List.of(List.of(1), List.of(2), List.of(1)));
        }

        @Test
        void givesEachPickItsOwnArraySoEditingOneLeavesTheRestAlone() {
            List<List<Integer>> extended = Treadling.extendTreadling(List.of(List.of(1)), 3);
            assertThatThrownBy(() -> extended.get(0).add(2)).isInstanceOf(UnsupportedOperationException.class);
            assertThat(extended.get(1)).containsExactly(1);
        }
    }
}
