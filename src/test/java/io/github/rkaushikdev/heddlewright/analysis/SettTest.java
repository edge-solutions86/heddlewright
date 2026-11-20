package io.github.rkaushikdev.heddlewright.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.weave.Plain;
import io.github.rkaushikdev.heddlewright.weave.Satin;
import io.github.rkaushikdev.heddlewright.weave.Twill;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SettTest {

    private static double roundToTwo(double value) {
        return Math.round(value * 100) / 100.0;
    }

    @Nested
    class MaximumSett {

        @Test
        void isTwentyEightRootNeTheDiametersThatFitInAnInch() {
            assertThat(Sett.maximumSett(16, "ne")).isEqualTo(112);
            assertThat(Sett.maximumSett(4, "ne")).isEqualTo(56);
        }
    }

    @Nested
    class SettFactor {

        @Test
        void isAHalfForPlainWeaveWhichCrossesOnEveryThread() {
            assertThat(Sett.settFactor(Plain.plainWeave())).isEqualTo(new WarpWeft(0.5, 0.5));
        }

        @Test
        void risesThroughTwillToSatinAsTheCrossingsThinOut() {
            double plain = Sett.settFactor(Plain.plainWeave()).warp();
            double twill = Sett.settFactor(Twill.twillWeave("2/2")).warp();
            double satin = Sett.settFactor(Satin.satinWeave(8, 3)).warp();
            assertThat(twill).isGreaterThan(plain);
            assertThat(satin).isGreaterThan(twill);
        }

        @Test
        void readsTwoThirdsForA22TwillAndFourFifthsForAnEightThreadSatin() {
            assertThat(Sett.settFactor(Twill.twillWeave("2/2")).warp()).isCloseTo(4.0 / 6, within(0.5e-3));
            assertThat(Sett.settFactor(Satin.satinWeave(8, 3)).warp()).isCloseTo(8.0 / 10, within(0.5e-3));
        }
    }

    @Nested
    class AshenhurstSett {

        @Test
        void setsPlainWeaveAtHalfTheDiameters() {
            Sett.Ashenhurst sett = Sett.ashenhurstSett(Plain.plainWeave(), 16);
            assertThat(sett.diameters()).isEqualTo(112);
            assertThat(sett.warp()).isEqualTo(56);
            assertThat(sett.weft()).isEqualTo(56);
        }

        @Test
        void setsASatinCloserThanATwillInTheSameYarn() {
            double twill = Sett.ashenhurstSett(Twill.twillWeave("2/2"), 30).warp();
            double satin = Sett.ashenhurstSett(Satin.satinWeave(8, 3), 30).warp();
            assertThat(satin).isGreaterThan(twill);
        }

        @Test
        void isTheSettFactorTimesTheDiameters() {
            Matrix cloth = Twill.twillWeave("3/1");
            Sett.Ashenhurst sett = Sett.ashenhurstSett(cloth, 24);
            assertThat(sett.warp()).isCloseTo(Sett.settFactor(cloth).warp() * sett.diameters(), within(0.05));
        }

        @Test
        void takesADifferentDiameterConstant() {
            Sett.Ashenhurst wide = Sett.ashenhurstSett(Plain.plainWeave(), 16, "ne", 25);
            assertThat(wide.diameters()).isEqualTo(100);
            assertThat(wide.warp()).isEqualTo(50);
        }
    }

    @Nested
    class WorkingSett {

        @Test
        void takesTwoThirdsOfTheTheoreticalFigureByDefault() {
            assertThat(Sett.workingSett(60)).isEqualTo(40.2);
        }

        @Test
        void takesWhateverShareTheDesignerAsksFor() {
            assertThat(Sett.workingSett(60, 0.5)).isEqualTo(30);
            assertThat(Sett.workingSett(60, 1)).isEqualTo(60);
        }

        @Test
        void refusesAShareOfZero() {
            assertThatThrownBy(() -> Sett.workingSett(60, 0)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class CoverFactor {

        @Test
        void readsEachDirectionAsThreadsOverRootCount() {
            Sett.Cover cover = Sett.coverFactor(60, 60, 36, 36);
            assertThat(cover.warp()).isEqualTo(10);
            assertThat(cover.weft()).isEqualTo(10);
        }

        @Test
        void takesOffTheOverlapWhenItAddsTheTwo() {
            assertThat(Sett.coverFactor(60, 60, 36, 36).cloth()).isEqualTo(roundToTwo(10 + 10 - (10.0 * 10) / 28));
        }

        @Test
        void risesWithACloserSett() {
            assertThat(Sett.coverFactor(80, 80, 36, 36).cloth())
                    .isGreaterThan(Sett.coverFactor(40, 40, 36, 36).cloth());
        }

        @Test
        void refusesACountOfZero() {
            assertThatThrownBy(() -> Sett.coverFactor(60, 60, 0, 36)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class IsJammed {

        @Test
        void isTrueOnceADirectionReachesTwentyEight() {
            assertThat(Sett.isJammed(new Sett.Cover(28, 10, 0))).isTrue();
            assertThat(Sett.isJammed(new Sett.Cover(10, 30, 0))).isTrue();
            assertThat(Sett.isJammed(new Sett.Cover(10, 12, 0))).isFalse();
        }
    }

    @Nested
    class EndsForWidthAndWidthForEnds {

        @Test
        void multipliesAndDividesByTheSett() {
            assertThat(Sett.endsForWidth(20, 24)).isEqualTo(480);
            assertThat(Sett.widthForEnds(480, 24)).isEqualTo(20);
        }

        @Test
        void roundsAnEndCountToAWholeThread() {
            assertThat(Sett.endsForWidth(20.5, 24)).isEqualTo(492);
            assertThat(Sett.endsForWidth(10.02, 24)).isEqualTo(240);
        }

        @Test
        void refusesASettOfZero() {
            assertThatThrownBy(() -> Sett.endsForWidth(20, 0)).isInstanceOf(InvalidArgumentException.class);
        }
    }
}
