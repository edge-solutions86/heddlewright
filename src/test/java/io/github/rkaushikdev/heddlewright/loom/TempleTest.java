package io.github.rkaushikdev.heddlewright.loom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TempleTest {

    @Nested
    class PullIn {

        @Test
        void isTheShareTheClothLostAgainstTheReed() {
            assertThat(Temple.pullIn(200, 190)).isEqualTo(0.05);
        }

        @Test
        void isNothingWhenTheClothComesOutTheWidthItWentIn() {
            assertThat(Temple.pullIn(200, 200)).isEqualTo(0);
        }

        @Test
        void refusesClothWiderThanTheReed() {
            assertThatThrownBy(() -> Temple.pullIn(200, 210)).hasMessageContaining("cannot be wider");
        }
    }

    @Nested
    class ClothWidthAndReedWidth {

        @Test
        void areInversesOfEachOther() {
            assertThat(Temple.clothWidth(200, 0.05)).isEqualTo(190);
            assertThat(Temple.reedWidth(190, 0.05)).isEqualTo(200);
        }

        @Test
        void leaveTheWidthAloneAtNoPullIn() {
            assertThat(Temple.clothWidth(200, 0)).isEqualTo(200);
        }

        @Test
        void refuseAPullInNobodyWouldWeave() {
            assertThatThrownBy(() -> Temple.clothWidth(200, 0.9)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class EstimatePullIn {

        @Test
        void risesWithTheWeftCrimp() {
            double low = Temple.estimatePullIn(4, 14);
            double high = Temple.estimatePullIn(10, 14);
            assertThat(high).isGreaterThan(low);
        }

        @Test
        void fallsAsTheClothIsSetMoreClosely() {
            double open = Temple.estimatePullIn(8, 8);
            double close = Temple.estimatePullIn(8, 22);
            assertThat(close).isLessThan(open);
        }

        @Test
        void isNothingWhenTheWeftDoesNotBendAtAll() {
            assertThat(Temple.estimatePullIn(0, 14)).isEqualTo(0);
        }

        @Test
        void neverReadsPastAQuarterWhateverItIsGiven() {
            assertThat(Temple.estimatePullIn(500, 1)).isEqualTo(0.25);
        }
    }

    @Nested
    class TempleGripAndTempleFits {

        @Test
        void holdsHalfTheLossAtEachSide() {
            assertThat(Temple.templeGrip(200, 190)).isEqualTo(5);
        }

        @Test
        void fitsWhenTheTempleReaches() {
            Temple.Fit reading = Temple.templeFits(200, 190, 6);
            assertThat(reading.fits()).isTrue();
            assertThat(Temple.assertTempleFits(200, 190, 6).fits()).isTrue();
        }

        @Test
        void refusesAGripPastWhatTheTempleHolds() {
            assertThat(Temple.templeFits(200, 170, 6).fits()).isFalse();
            assertThatThrownBy(() -> Temple.assertTempleFits(200, 170, 6)).isInstanceOf(CapacityException.class);
        }
    }

    @Nested
    class PullInWarning {

        @Test
        void isQuietInsideTheWorkingLimit() {
            assertThat(Temple.pullInWarning(0.05)).isEmpty();
            assertThat(Temple.pullInWarning(0.1)).isEmpty();
        }

        @Test
        void warnsPastIt() {
            assertThat(Temple.pullInWarning(0.15).orElseThrow().code()).isEqualTo("excessive_pull_in");
        }

        @Test
        void takesADifferentLimit() {
            assertThat(Temple.pullInWarning(0.06, 0.05)).isPresent();
        }
    }

    @Nested
    class EndsUnderTempleAndRingsNeeded {

        @Test
        void countsTheSelvedgeEndsATempleCovers() {
            assertThat(Temple.endsUnderTemple(5, 24)).isEqualTo(120);
        }

        @Test
        void roundsAnEndUpRatherThanLosingIt() {
            assertThat(Temple.endsUnderTemple(0.1, 24)).isEqualTo(3);
        }

        @Test
        void needsAtLeastTwoRingsWhateverTheWidth() {
            assertThat(Temple.ringsNeeded(1)).isEqualTo(2);
            assertThat(Temple.ringsNeeded(100)).isEqualTo(20);
            assertThat(Temple.ringsNeeded(100, 10)).isEqualTo(10);
        }
    }

    @Nested
    class AssertTempleKind {

        @Test
        void namesTheKindsAShedFits() {
            assertThat(Temple.TEMPLE_KINDS)
                    .extracting(Object::toString)
                    .containsExactly("ring", "roller", "full width");
            assertThat(Temple.assertTempleKind("roller")).isEqualTo(Temple.Kind.ROLLER);
            assertThatThrownBy(() -> Temple.assertTempleKind("clamp")).isInstanceOf(InvalidArgumentException.class);
        }
    }
}
