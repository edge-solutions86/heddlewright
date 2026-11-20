package io.github.rkaushikdev.heddlewright.loom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LetoffTest {

    @Nested
    class TensionFromWeight {

        @Test
        void risesWithTheWeightAndTheLever() {
            double base = Letoff.tensionFromWeight(10, 40);
            // Loosely, because each reading is rounded to three places before it is doubled or
            // trebled here.
            assertThat(Letoff.tensionFromWeight(20, 40)).isCloseTo(base * 2, within(0.005));
            assertThat(Letoff.tensionFromWeight(10, 3, 40)).isCloseTo(base * 3, within(0.005));
        }

        @Test
        void risesAsTheBeamEmptiesBecauseTheRadiusShrinks() {
            double full = Letoff.tensionFromWeight(10, 60);
            double empty = Letoff.tensionFromWeight(10, 20);
            assertThat(empty).isGreaterThan(full);
            assertThat(empty / full).isCloseTo(3, within(0.0005));
        }

        @Test
        void refusesAWeightOrABeamOfNothing() {
            assertThatThrownBy(() -> Letoff.tensionFromWeight(0, 40)).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Letoff.tensionFromWeight(10, 0)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class TensionDrift {

        @Test
        void reportsHowFarTheTensionMovesOverABeam() {
            Letoff.Drift drift = Letoff.tensionDrift(10, 60, 20);
            assertThat(drift.ratio()).isCloseTo(3, within(0.0005));
            assertThat(drift.risePercent()).isCloseTo(200, within(0.05));
        }

        @Test
        void isOneWhenTheBeamDoesNotChange() {
            Letoff.Drift drift = Letoff.tensionDrift(10, 60, 59.999);
            assertThat(drift.ratio()).isCloseTo(1, within(0.0005));
        }

        @Test
        void refusesAnEmptyBeamThickerThanAFullOne() {
            assertThatThrownBy(() -> Letoff.tensionDrift(10, 20, 60)).hasMessageContaining("thinner than a full one");
        }
    }

    @Nested
    class WeightForTension {

        @Test
        void invertsTheTensionCalculation() {
            double weight = Letoff.weightForTension(245, 40);
            assertThat(Letoff.tensionFromWeight(weight, 40)).isCloseTo(245, within(0.05));
        }

        @Test
        void needsLessWeightOnAThickerBeam() {
            double thin = Letoff.weightForTension(245, 20);
            double thick = Letoff.weightForTension(245, 60);
            assertThat(thick).isGreaterThan(thin);
        }
    }

    @Nested
    class TensionPerEndAndWithinSafeTension {

        @Test
        void dividesTheTensionAcrossTheWarp() {
            assertThat(Letoff.tensionPerEnd(300, 3000)).isEqualTo(0.1);
        }

        @Test
        void passesAWarpHeldWellBelowItsBreakingLoad() {
            Letoff.SafeTension reading = Letoff.withinSafeTension(300, 3000, 4);
            assertThat(reading.limit()).isEqualTo(0.6);
            assertThat(reading.safe()).isTrue();
            assertThat(reading.headroom()).isCloseTo(0.5, within(0.000005));
        }

        @Test
        void failsOneHeldNearItsBreakingLoad() {
            Letoff.SafeTension reading = Letoff.withinSafeTension(3000, 3000, 4);
            assertThat(reading.safe()).isFalse();
            assertThat(reading.headroom()).isLessThan(0);
        }

        @Test
        void takesADifferentWorkingShare() {
            assertThat(Letoff.withinSafeTension(300, 3000, 4, 0.05).safe()).isTrue();
            assertThat(Letoff.withinSafeTension(300, 3000, 4, 0.01).safe()).isFalse();
        }
    }

    @Nested
    class BeamTurnsPerPick {

        @Test
        void turnsLessPerPickOnAFatterBeam() {
            double thin = Letoff.beamTurnsPerPick(20, 20);
            double fat = Letoff.beamTurnsPerPick(20, 60);
            assertThat(fat).isLessThan(thin);
        }

        @Test
        void paysOutMorePicksPerTurnOnAFatterBeam() {
            assertThat(Letoff.picksPerBeamTurn(20, 60)).isGreaterThan(Letoff.picksPerBeamTurn(20, 20));
        }

        @Test
        void isTheReciprocalOfTheTurnsPerPick() {
            assertThat(Letoff.picksPerBeamTurn(20, 40) * Letoff.beamTurnsPerPick(20, 40))
                    .isCloseTo(1, within(0.00005));
        }
    }

    @Nested
    class AssertLetOffType {

        @Test
        void namesTheTwoKinds() {
            assertThat(Letoff.LET_OFF_TYPES).extracting(Object::toString).containsExactly("negative", "positive");
            assertThat(Letoff.assertLetOffType("positive")).isEqualTo(Letoff.Type.POSITIVE);
            assertThatThrownBy(() -> Letoff.assertLetOffType("semi")).isInstanceOf(InvalidArgumentException.class);
        }
    }
}
