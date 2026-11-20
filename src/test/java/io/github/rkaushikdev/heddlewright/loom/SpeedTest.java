package io.github.rkaushikdev.heddlewright.loom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SpeedTest {

    @Nested
    class PicksPerMinute {

        @Test
        void isTheCrankSpeedOnASingleShuttleLoom() {
            assertThat(Speed.picksPerMinute(180)).isEqualTo(180);
        }

        @Test
        void doublesOnAMachineInsertingTwoPicksATurn() {
            assertThat(Speed.picksPerMinute(180, 2)).isEqualTo(360);
        }
    }

    @Nested
    class WeftInsertionRate {

        @Test
        void multipliesSpeedByTheWidthWoven() {
            assertThat(Speed.weftInsertionRate(200, 1.9)).isEqualTo(380);
        }

        @Test
        void letsAWideSlowLoomBeatANarrowFastOne() {
            assertThat(Speed.weftInsertionRate(150, 3.2)).isGreaterThan(Speed.weftInsertionRate(300, 1.5));
        }
    }

    @Nested
    class ClothPerHourMetres {

        @Test
        void fallsAsThePickDensityRises() {
            double open = Speed.clothPerHourMetres(200, 10);
            double close = Speed.clothPerHourMetres(200, 20);
            assertThat(open).isEqualTo(12);
            assertThat(close).isEqualTo(6);
        }

        @Test
        void scalesStraightDownWithEfficiency() {
            assertThat(Speed.clothPerHourMetres(200, 10, 0.85)).isEqualTo(10.2);
        }

        @Test
        void isNothingWhenTheLoomIsStopped() {
            assertThat(Speed.clothPerHourMetres(200, 10, 0)).isEqualTo(0);
        }

        @Test
        void refusesAnEfficiencyOverOne() {
            assertThatThrownBy(() -> Speed.clothPerHourMetres(200, 10, 1.2))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class PicksForCloth {

        @Test
        void countsThePicksARunNeeds() {
            assertThat(Speed.picksForCloth(50, 20)).isEqualTo(100000);
        }

        @Test
        void roundsToAWholePick() {
            assertThat(Speed.picksForCloth(0.503, 20)).isEqualTo(1006);
        }
    }

    @Nested
    class RunHours {

        @Test
        void isThePicksOverTheRunningSpeed() {
            assertThat(Speed.runHours(100, 20, 200)).isEqualTo(16.667);
        }

        @Test
        void takesLongerAtALowerEfficiency() {
            double full = Speed.runHours(100, 20, 200);
            double real = Speed.runHours(100, 20, 200, 0.8);
            assertThat(real).isGreaterThan(full);
            assertThat(real).isCloseTo(full / 0.8, within(0.005));
        }

        @Test
        void refusesAnEfficiencyOfNothingWhichNeverFinishes() {
            assertThatThrownBy(() -> Speed.runHours(100, 20, 200, 0)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class ShiftProduction {

        @Test
        void isTheHourlyFigureOverTheShift() {
            assertThat(Speed.shiftProduction(200, 20, 0.85)).isEqualTo(40.8);
        }

        @Test
        void takesAShiftOfADifferentLength() {
            double eight = Speed.shiftProduction(200, 20);
            double twelve = Speed.shiftProduction(12, 200, 20, 1);
            assertThat(twelve).isCloseTo(eight * 1.5, within(0.0005));
        }
    }

    @Nested
    class WeftPackageMinutes {

        @Test
        void lastsLongerOnANarrowClothThanAWideOne() {
            double narrow = Speed.weftPackageMinutes(20000, 1, 200);
            double wide = Speed.weftPackageMinutes(20000, 2, 200);
            assertThat(narrow).isEqualTo(100);
            assertThat(wide).isEqualTo(50);
        }

        @Test
        void runsOutSoonerOnceTheWeftCrimpIsAllowedFor() {
            double straight = Speed.weftPackageMinutes(20000, 1, 200);
            double crimped = Speed.weftPackageMinutes(20000, 1, 200, 10);
            assertThat(crimped).isLessThan(straight);
        }
    }

    @Nested
    class LoomsPerWeaver {

        @Test
        void dividesTheHourByTheAttentionEachLoomNeeds() {
            assertThat(Speed.loomsPerWeaver(5)).isEqualTo(10);
            assertThat(Speed.loomsPerWeaver(10)).isEqualTo(5);
        }

        @Test
        void roundsDownBecauseAPartLoomIsNotALoom() {
            assertThat(Speed.loomsPerWeaver(7)).isEqualTo(7);
        }

        @Test
        void takesADifferentPersonalAllowance() {
            assertThat(Speed.loomsPerWeaver(5, 1)).isEqualTo(12);
        }
    }

    @Nested
    class CompareLooms {

        @Test
        void comparesOnInsertionRateNotOnPicksAMinute() {
            Speed.Comparison comparison = Speed.compareLooms(new Speed.Machine(300, 1.5), new Speed.Machine(150, 3.2));
            assertThat(comparison.faster()).isEqualTo("right");
            assertThat(comparison.ratio()).isCloseTo(480.0 / 450, within(0.0005));
        }

        @Test
        void callsTheLeftOneFasterOnATie() {
            Speed.Machine same = new Speed.Machine(200, 2);
            Speed.Comparison comparison = Speed.compareLooms(same, same);
            assertThat(comparison.faster()).isEqualTo("left");
            assertThat(comparison.ratio()).isEqualTo(1);
        }
    }
}
