package io.github.rkaushikdev.heddlewright.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CrimpTest {

    @Nested
    class CrimpPercentAndTakeUpPercent {

        @Test
        void measureTheSameFactAgainstDifferentLengths() {
            assertThat(Crimp.crimpPercent(110, 100)).isEqualTo(10);
            assertThat(Crimp.takeUpPercent(110, 100)).isCloseTo(9.0909, within(0.5e-3));
        }

        @Test
        void areBothZeroForAThreadThatLiesStraight() {
            assertThat(Crimp.crimpPercent(100, 100)).isEqualTo(0);
            assertThat(Crimp.takeUpPercent(100, 100)).isEqualTo(0);
        }

        @Test
        void refuseYarnShorterThanTheClothWhichCannotHappen() {
            assertThatThrownBy(() -> Crimp.crimpPercent(90, 100)).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Crimp.takeUpPercent(90, 100)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class TakeUpFromCrimpAndCrimpFromTakeUp {

        @Test
        void agreeWithTheDirectMeasurements() {
            assertThat(Crimp.takeUpFromCrimp(10)).isCloseTo(Crimp.takeUpPercent(110, 100), within(0.5e-4));
            assertThat(Crimp.crimpFromTakeUp(Crimp.takeUpPercent(110, 100))).isCloseTo(10, within(0.5e-3));
        }

        @Test
        void areInversesOfEachOther() {
            for (double crimp : List.of(0.0, 5.0, 12.5, 40.0)) {
                assertThat(Crimp.crimpFromTakeUp(Crimp.takeUpFromCrimp(crimp))).isCloseTo(crimp, within(0.5e-3));
            }
        }

        @Test
        void takeUpIsAlwaysTheSmallerOfTheTwo() {
            for (double crimp : List.of(1.0, 8.0, 25.0)) {
                assertThat(Crimp.takeUpFromCrimp(crimp)).isLessThan(crimp);
            }
        }

        @Test
        void refuseATakeUpOfAHundredPerCentWhichHasNoCrimpReading() {
            assertThatThrownBy(() -> Crimp.crimpFromTakeUp(100)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class YarnForClothAndClothFromYarn {

        @Test
        void addAndRemoveTheCrimp() {
            assertThat(Crimp.yarnForCloth(100, 8)).isEqualTo(108);
            assertThat(Crimp.clothFromYarn(108, 8)).isEqualTo(100);
        }

        @Test
        void leaveTheLengthAloneAtNoCrimp() {
            assertThat(Crimp.yarnForCloth(50, 0)).isEqualTo(50);
        }
    }

    @Nested
    class WarpLength {

        @Test
        void addsCrimpShrinkageAndLoomWasteInThatOrder() {
            Crimp.WarpLength plan = Crimp.warpLength(10, 10, 1, 0);
            assertThat(plan.finished()).isEqualTo(10);
            assertThat(plan.woven()).isEqualTo(11);
            assertThat(plan.total()).isEqualTo(12);
        }

        @Test
        void allowsForFinishedShrinkageBeforeTheCrimp() {
            Crimp.WarpLength plan = Crimp.warpLength(9, 0, 0, 10);
            assertThat(plan.finished()).isEqualTo(10);
            assertThat(plan.woven()).isEqualTo(10);
        }

        @Test
        void needsNothingButAClothLength() {
            assertThat(Crimp.warpLength(5)).isEqualTo(new Crimp.WarpLength(5, 5, 5, 0));
        }

        @Test
        void refusesAShrinkageOfAHundredPerCent() {
            assertThatThrownBy(() -> Crimp.warpLength(5, 0, 0, 100)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class SplitCrimp {

        @Test
        void splitsEvenlyOnASquareSett() {
            assertThat(Crimp.splitCrimp(10, 24, 24)).isEqualTo(new WarpWeft(5, 5));
        }

        @Test
        void givesTheCrimpToTheSystemWithFewerThreadsHoldingItStraight() {
            WarpWeft split = Crimp.splitCrimp(12, 60, 20);
            assertThat(split.weft()).isGreaterThan(split.warp());
            assertThat(split.warp() + split.weft()).isCloseTo(12, within(0.5e-6));
        }
    }

    @Nested
    class WarpYarnMetres {

        @Test
        void multipliesTheEndsByTheCrimpedLength() {
            assertThat(Crimp.warpYarnMetres(500, 10, 8)).isEqualTo(5400);
        }

        @Test
        void ignoresCrimpWhenThereIsNone() {
            assertThat(Crimp.warpYarnMetres(100, 5)).isEqualTo(500);
        }
    }

    @Nested
    class WeftYarnMetres {

        @Test
        void multipliesPicksByTheCrimpedWidth() {
            assertThat(Crimp.weftYarnMetres(800, 10, 1.5)).isEqualTo(12000);
        }

        @Test
        void addsTheWeftCrimpToTheWidth() {
            double straight = Crimp.weftYarnMetres(100, 1, 1);
            double crimped = Crimp.weftYarnMetres(100, 1, 1, 10);
            assertThat(crimped).isCloseTo(straight * 1.1, within(0.5e-6));
        }
    }
}
