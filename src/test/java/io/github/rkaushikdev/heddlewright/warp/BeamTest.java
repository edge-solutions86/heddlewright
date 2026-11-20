package io.github.rkaushikdev.heddlewright.warp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.warp.Beam.Warp;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BeamTest {

    private static final Beam BEAM = new Beam(20, 60, 180);
    private static final int ENDS = 3000;
    private static final double TEX = 30;

    private static Offset<Double> digits(int count) {
        return within(Math.pow(10, -count) / 2);
    }

    private static double roundThree(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    @Nested
    class WindingVolumeCm3 {

        @Test
        void isTheRingBetweenTheBarrelAndTheFlangeTimesTheWidth() {
            double wanted = Math.PI * (30 * 30 - 10 * 10) * 180;
            assertThat(BEAM.windingVolumeCm3()).isCloseTo(wanted, digits(2));
        }

        @Test
        void growsWithTheFlange() {
            assertThat(new Beam(20, 80, 180).windingVolumeCm3()).isGreaterThan(BEAM.windingVolumeCm3());
        }

        @Test
        void refusesAFlangeNoWiderThanTheBarrel() {
            assertThatThrownBy(() -> new Beam(20, 20, 180).windingVolumeCm3())
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class BeamCapacityKg {

        @Test
        void isTheVolumeAtThePackingDensity() {
            assertThat(BEAM.capacityKg()).isEqualTo(roundThree((BEAM.windingVolumeCm3() * 0.5) / 1000));
        }

        @Test
        void scalesWithTheDensityAShedMeasures() {
            // Compared loosely on purpose: both readings are rounded to three places, so doubling one
            // is not the same number as rounding the doubled one.
            assertThat(BEAM.capacityKg(1)).isCloseTo(BEAM.capacityKg() * 2, digits(2));
        }
    }

    @Nested
    class BeamCapacityMetres {

        @Test
        void turnsTheWeightIntoAWarpLength() {
            double metres = BEAM.capacityMetres(ENDS, TEX);
            assertThat(metres).isGreaterThan(0);
            double grams = (ENDS * TEX * metres) / 1000;
            assertThat(grams / 1000).isCloseTo(BEAM.capacityKg(), digits(0));
        }

        @Test
        void holdsLessOfACoarserYarn() {
            assertThat(BEAM.capacityMetres(ENDS, 60)).isLessThan(BEAM.capacityMetres(ENDS, TEX));
        }

        @Test
        void holdsLessOfAWiderWarp() {
            assertThat(BEAM.capacityMetres(6000, TEX)).isLessThan(BEAM.capacityMetres(ENDS, TEX));
        }
    }

    @Nested
    class BuildUpCmAndWoundDiameterCm {

        @Test
        void isNothingOnAnEmptyBeamAndGrowsWithTheWarp() {
            double shorter = BEAM.buildUpCm(Warp.of(ENDS, TEX, 100));
            double longer = BEAM.buildUpCm(Warp.of(ENDS, TEX, 1000));
            assertThat(shorter).isGreaterThan(0);
            assertThat(longer).isGreaterThan(shorter);
        }

        @Test
        void addsTheBuildUpToTheBarrel() {
            Warp warp = Warp.of(ENDS, TEX, 500);
            assertThat(BEAM.woundDiameterCm(warp)).isCloseTo(20 + BEAM.buildUpCm(warp), digits(4));
        }

        @Test
        void reachesTheFlangeAtAboutTheCapacityTheWeightCalculationGives() {
            double metres = BEAM.capacityMetres(ENDS, TEX);
            assertThat(BEAM.woundDiameterCm(Warp.of(ENDS, TEX, metres))).isCloseTo(60, digits(1));
        }
    }

    @Nested
    class AssertFitsBeam {

        @Test
        void passesAWarpThatFits() {
            assertThat(BEAM.assertFitsBeam(Warp.of(ENDS, TEX, 1000))).isLessThan(60);
        }

        @Test
        void refusesOneThatWouldWindPastTheFlangesAndSaysByHowMuch() {
            CapacityException err = catchThrowableOfType(
                    CapacityException.class, () -> BEAM.assertFitsBeam(Warp.of(ENDS, TEX, 100000)));
            assertThat(err).isNotNull();
            assertThat(err.detail().get("over").asDouble()).isGreaterThan(0);
        }
    }

    @Nested
    class FillShareAndIsWorthBeaming {

        @Test
        void readsAnEmptyBeamNearNothingAndAFullOneNearOne() {
            assertThat(BEAM.fillShare(Warp.of(ENDS, TEX, 10))).isLessThan(0.05);
            double full = BEAM.capacityMetres(ENDS, TEX);
            assertThat(BEAM.fillShare(Warp.of(ENDS, TEX, full))).isCloseTo(1, digits(1));
        }

        @Test
        void callsAThinBeamNotWorthTheChangeOver() {
            assertThat(BEAM.isWorthBeaming(Warp.of(ENDS, TEX, 100))).isFalse();
            double full = BEAM.capacityMetres(ENDS, TEX);
            assertThat(BEAM.isWorthBeaming(Warp.of(ENDS, TEX, full))).isTrue();
        }

        @Test
        void takesADifferentMinimum() {
            Warp warp = Warp.of(ENDS, TEX, 2000);
            assertThat(BEAM.isWorthBeaming(warp, 0)).isTrue();
            assertThat(BEAM.isWorthBeaming(warp, 1)).isFalse();
        }
    }
}
