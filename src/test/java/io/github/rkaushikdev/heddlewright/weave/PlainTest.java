package io.github.rkaushikdev.heddlewright.weave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.draft.Drawdown;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Runs;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlainTest {

    @Nested
    class PlainWeave {

        @Test
        void isTheTwoByTwoChequer() {
            assertThat(Plain.plainWeave().format()).isEqualTo("X.\n.X");
        }

        @Test
        void isBalanced() {
            assertThat(Drawdown.warpFaceRatio(Plain.plainWeave())).isEqualTo(0.5);
        }

        @Test
        void hasNoFloatLongerThanOneThreadEitherWay() {
            Matrix repeat = Plain.plainWeave();
            assertThat(Runs.longestRun(repeat.row(0), 1)).isEqualTo(1);
            assertThat(Runs.longestRun(List.of(repeat.get(0, 0), repeat.get(1, 0)), 1))
                    .isEqualTo(1);
        }
    }

    @Nested
    class BasketWeave {

        @Test
        void buildsTheTwoByTwoBasket() {
            assertThat(Plain.basketWeave(2).format()).isEqualTo("XX..\nXX..\n..XX\n..XX");
        }

        @Test
        void buildsAHalfBasketWithTwoEndsAndOnePick() {
            assertThat(Plain.basketWeave(2, 1).format()).isEqualTo("XX..\n..XX");
        }

        @Test
        void aOneByOneBasketIsPlainWeave() {
            assertThat(Plain.basketWeave(1, 1)).isEqualTo(Plain.plainWeave());
        }

        @Test
        void staysBalancedWhateverTheGroupSize() {
            assertThat(Drawdown.warpFaceRatio(Plain.basketWeave(3))).isEqualTo(0.5);
            assertThat(Drawdown.warpFaceRatio(Plain.basketWeave(4, 2))).isEqualTo(0.5);
        }

        @Test
        void floatsExactlyAsFarAsTheGroupIsWide() {
            Matrix repeat = Plain.basketWeave(3);
            assertThat(Runs.longestRun(repeat.row(0), 1)).isEqualTo(3);
            assertThat(repeat.dimensions()).isEqualTo(new Matrix.Dimensions(6, 6));
        }

        @Test
        void refusesAGroupOfNoThreads() {
            assertThatThrownBy(() -> Plain.basketWeave(0)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class WarpRib {

        @Test
        void holdsEachShedForTheRunOfPicks() {
            assertThat(Plain.warpRib(2).format()).isEqualTo("X.\nX.\n.X\n.X");
            assertThat(Plain.warpRib(3).format()).isEqualTo("X.\nX.\nX.\n.X\n.X\n.X");
        }

        @Test
        void isPlainWeaveWhenTheRunIsOne() {
            assertThat(Plain.warpRib(1)).isEqualTo(Plain.plainWeave());
        }

        @Test
        void keepsTheRepeatTwoEndsWideHoweverLongTheRun() {
            assertThat(Plain.warpRib(5).dimensions()).isEqualTo(new Matrix.Dimensions(10, 2));
        }
    }

    @Nested
    class WeftRib {

        @Test
        void groupsTheEndsInsteadOfThePicks() {
            assertThat(Plain.weftRib(2).format()).isEqualTo("XX..\n..XX");
        }

        @Test
        void isTheTransposeOfTheMatchingWarpRib() {
            assertThat(Plain.weftRib(3).dimensions()).isEqualTo(new Matrix.Dimensions(2, 6));
            assertThat(Plain.warpRib(3).dimensions()).isEqualTo(new Matrix.Dimensions(6, 2));
        }
    }

    @Nested
    class IrregularWarpRib {

        @Test
        void runsEachShedForItsOwnCount() {
            assertThat(Plain.irregularWarpRib(List.of(3, 1)).format()).isEqualTo("X.\nX.\nX.\n.X");
        }

        @Test
        void alternatesTheShedOnEveryRun() {
            assertThat(Plain.irregularWarpRib(List.of(1, 2, 2, 1)).format()).isEqualTo("X.\n.X\n.X\nX.\nX.\n.X");
        }

        @Test
        void refusesAnOddNumberOfRunsWhichWouldNotCloseTheRepeat() {
            assertThatThrownBy(() -> Plain.irregularWarpRib(List.of(2, 1, 3))).isInstanceOf(DraftException.class);
        }

        @Test
        void refusesARunOfNothing() {
            assertThatThrownBy(() -> Plain.irregularWarpRib(List.of(2, 0)))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class PlainOnShafts {

        @Test
        void splitsTheOddAndEvenShaftsOntoTwoTreadles() {
            assertThat(Plain.plainOnShafts(4))
                    .isEqualTo(Json.obj(
                            "tieup",
                            List.of(List.of(1, 3), List.of(2, 4)),
                            "treadling",
                            List.of(List.of(1), List.of(2)),
                            "shafts",
                            4));
        }

        @Test
        void handlesThePlainTwoShaftCase() {
            assertThat(Plain.plainOnShafts(2).get("tieup")).isEqualTo(Json.of(List.of(List.of(1), List.of(2))));
        }

        @Test
        void refusesAnOddShaftCount() {
            assertThatThrownBy(() -> Plain.plainOnShafts(5)).isInstanceOf(DraftException.class);
        }
    }

    @Nested
    class IsPlainDerived {

        @Test
        void acceptsTheFamily() {
            assertThat(Plain.isPlainDerived(Plain.plainWeave())).isTrue();
            assertThat(Plain.isPlainDerived(Plain.basketWeave(2))).isTrue();
            assertThat(Plain.isPlainDerived(Plain.warpRib(2))).isTrue();
        }

        @Test
        void rejectsARepeatWithARowThatNeverBinds() {
            assertThat(Plain.isPlainDerived(Matrix.of(new int[][] {{1, 1}, {0, 1}})))
                    .isFalse();
        }
    }
}
