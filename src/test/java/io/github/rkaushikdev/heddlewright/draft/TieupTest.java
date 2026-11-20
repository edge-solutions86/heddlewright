package io.github.rkaushikdev.heddlewright.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TieupTest {

    private static final List<List<Integer>> TWILL_2_2 =
            List.of(List.of(1, 2), List.of(2, 3), List.of(3, 4), List.of(1, 4));

    @Nested
    class NormaliseTieup {

        @Test
        void sortsAndDeduplicatesEachTreadle() {
            assertThat(Tieup.normaliseTieup(List.of(List.of(3, 1, 3)), 4)).isEqualTo(List.of(List.of(1, 3)));
        }

        @Test
        void keepsAnUntiedTreadleAsAnEmptyList() {
            assertThat(Tieup.normaliseTieup(List.of(List.of(), List.of(1)), 4))
                    .isEqualTo(List.of(List.of(), List.of(1)));
        }

        @Test
        void rejectsAShaftOffTheLoomNamingTheTreadle() {
            assertThatThrownBy(() -> Tieup.normaliseTieup(List.of(List.of(1), List.of(5)), 4))
                    .hasMessageContaining("tieup[1][0]");
        }

        @Test
        void doesNotTouchTheCallerList() {
            List<List<Integer>> source = List.of(new ArrayList<>(List.of(3, 1)));
            Tieup.normaliseTieup(source, 4);
            assertThat(source).isEqualTo(List.of(List.of(3, 1)));
        }
    }

    @Nested
    class AssertTieupUsable {

        @Test
        void passesATieupWithAtLeastOneTiedTreadleAndReportsTheUntiedOnes() {
            Tieup.Usable result = Tieup.assertTieupUsable(List.of(List.of(1, 2), List.of(), List.of(3)), 4);
            assertThat(result.untied()).containsExactly(2);
            assertThat(result.tieup().get(0)).containsExactly(1, 2);
        }

        @Test
        void refusesATieupWhereNothingIsTiedAtAll() {
            assertThatThrownBy(() -> Tieup.assertTieupUsable(List.of(List.of(), List.of()), 4))
                    .isInstanceOf(DraftException.class);
        }
    }

    @Nested
    class TieupMatrixAndTieupFromMatrix {

        @Test
        void setsACellForEveryTiedShaft() {
            assertThat(Tieup.tieupMatrix(TWILL_2_2, 4).format()).isEqualTo("XX..\n.XX.\n..XX\nX..X");
        }

        @Test
        void roundTripsThroughTheMatrixForm() {
            assertThat(Tieup.tieupFromMatrix(Tieup.tieupMatrix(TWILL_2_2, 4))).isEqualTo(TWILL_2_2);
        }

        @Test
        void keepsAnUntiedTreadleAsAnEmptyRow() {
            List<List<Integer>> tieup = List.of(List.of(), List.of(2));
            assertThat(Tieup.tieupMatrix(tieup, 3).format()).isEqualTo("...\n.X.");
            assertThat(Tieup.tieupFromMatrix(Tieup.tieupMatrix(tieup, 3))).isEqualTo(tieup);
        }
    }

    @Nested
    class ShaftMatrix {

        @Test
        void isTheTransposeOneRowPerShaft() {
            assertThat(Tieup.shaftMatrix(TWILL_2_2, 4).format()).isEqualTo("X..X\nXX..\n.XX.\n..XX");
        }
    }

    @Nested
    class ConvertShed {

        @Test
        void namesTheTwoShedTypes() {
            assertThat(Shed.TYPES.stream().map(Shed::toString)).containsExactly("rising", "sinking");
        }

        @Test
        void takesTheComplementWhenTheLoomChanges() {
            assertThat(Tieup.convertShed(TWILL_2_2, 4, Shed.RISING, Shed.SINKING))
                    .isEqualTo(List.of(List.of(3, 4), List.of(1, 4), List.of(1, 2), List.of(2, 3)));
        }

        @Test
        void isItsOwnInverse() {
            List<List<Integer>> sunk = Tieup.convertShed(TWILL_2_2, 4, Shed.RISING, Shed.SINKING);
            assertThat(Tieup.convertShed(sunk, 4, Shed.SINKING, Shed.RISING)).isEqualTo(TWILL_2_2);
        }

        @Test
        void onlyNormalisesWhenTheShedDoesNotChange() {
            assertThat(Tieup.convertShed(List.of(List.of(2, 1)), 4, Shed.RISING, Shed.RISING))
                    .isEqualTo(List.of(List.of(1, 2)));
        }

        @Test
        void turnsAnUntiedTreadleIntoOneThatLiftsEverything() {
            assertThat(Tieup.convertShed(List.of(List.of()), 3, Shed.RISING, Shed.SINKING))
                    .isEqualTo(List.of(List.of(1, 2, 3)));
        }

        @Test
        void rejectsAShedTypeItDoesNotKnow() {
            assertThatThrownBy(() -> Tieup.convertShed(TWILL_2_2, 4, Shed.RISING, Shed.parse("floating")))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class TreadleReports {

        @Test
        void findsUntiedTreadlesByNumber() {
            assertThat(Tieup.untiedTreadles(List.of(List.of(1), List.of(), List.of(2), List.of()), 4))
                    .containsExactly(2, 4);
            assertThat(Tieup.untiedTreadles(TWILL_2_2, 4)).isEmpty();
        }

        @Test
        void groupsTreadlesTiedTheSameWay() {
            List<List<Integer>> groups =
                    Tieup.duplicateTreadles(List.of(List.of(1, 2), List.of(3), List.of(2, 1), List.of(3)), 4);
            assertThat(groups).isEqualTo(List.of(List.of(1, 3), List.of(2, 4)));
        }

        @Test
        void reportsNothingWhenEveryTreadleDiffers() {
            assertThat(Tieup.duplicateTreadles(TWILL_2_2, 4)).isEmpty();
        }

        @Test
        void countsTheShaftsEachTreadleLifts() {
            assertThat(Tieup.treadleLoad(TWILL_2_2, 4)).containsExactly(2, 2, 2, 2);
            assertThat(Tieup.treadleLoad(List.of(List.of(1, 2, 3), List.of()), 4))
                    .containsExactly(3, 0);
        }
    }

    @Nested
    class TwillTieup {

        @Test
        void buildsThe22TwillTieupOnFourShafts() {
            assertThat(Tieup.twillTieup(4, 2)).isEqualTo(TWILL_2_2);
        }

        @Test
        void buildsA31TwillByRaisingThree() {
            assertThat(Tieup.twillTieup(4, 3))
                    .isEqualTo(List.of(List.of(1, 2, 3), List.of(2, 3, 4), List.of(1, 3, 4), List.of(1, 2, 4)));
        }

        @Test
        void buildsThe13TieupOnASingleRaise() {
            assertThat(Tieup.twillTieup(4, 1)).isEqualTo(List.of(List.of(1), List.of(2), List.of(3), List.of(4)));
        }

        @Test
        void refusesARaiseThatWouldLiftEveryShaft() {
            assertThatThrownBy(() -> Tieup.twillTieup(4, 4)).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Tieup.twillTieup(4, 0)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class DirectTieup {

        @Test
        void tiesEachTreadleToItsOwnShaft() {
            assertThat(Tieup.directTieup(3)).isEqualTo(List.of(List.of(1), List.of(2), List.of(3)));
        }

        @Test
        void isTheIdentityMatrix() {
            assertThat(Tieup.tieupMatrix(Tieup.directTieup(3), 3).format()).isEqualTo("X..\n.X.\n..X");
        }

        @Test
        void matchesAOneRaiseTwillTieup() {
            assertThat(Tieup.directTieup(4)).isEqualTo(Tieup.twillTieup(4, 1));
        }
    }
}
