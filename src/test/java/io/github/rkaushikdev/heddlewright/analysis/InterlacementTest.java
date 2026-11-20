package io.github.rkaushikdev.heddlewright.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.heddlewright.analysis.Interlacement.Firmness;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.weave.Plain;
import io.github.rkaushikdev.heddlewright.weave.Satin;
import io.github.rkaushikdev.heddlewright.weave.Twill;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InterlacementTest {

    @Nested
    class IntersectionsPerEnd {

        @Test
        void isTwoPerEndForPlainWeaveOneUpAndOneDown() {
            assertThat(Interlacement.intersectionsPerEnd(Plain.plainWeave())).containsExactly(2, 2);
        }

        @Test
        void isTwoPerEndForASatinWhichBindsOnceAndFloatsOnce() {
            assertThat(Interlacement.intersectionsPerEnd(Satin.satinWeave(8, 3)))
                    .containsExactly(2, 2, 2, 2, 2, 2, 2, 2);
        }

        @Test
        void countsRoundTheRepeatSoAWrappingFloatIsOneRun() {
            assertThat(Interlacement.intersectionsPerEnd(Matrix.parse("X.\nX.\n..\nX."))
                            .get(0))
                    .isEqualTo(2);
        }

        @Test
        void countsAConstantEndAsASingleUnbrokenRun() {
            assertThat(Interlacement.intersectionsPerEnd(Matrix.parse("X.\nX.")))
                    .containsExactly(1, 1);
        }
    }

    @Nested
    class IntersectionsPerPick {

        @Test
        void readsTheSameCountAcrossThePicksOfATwill() {
            assertThat(Interlacement.intersectionsPerPick(Twill.twillWeave("2/2")))
                    .containsExactly(2, 2, 2, 2);
        }
    }

    @Nested
    class InterlacementFactor {

        @Test
        void isOneForPlainWeaveTheFirmestInterlacementThereIs() {
            assertThat(Interlacement.interlacementFactor(Plain.plainWeave())).isEqualTo(1);
        }

        @Test
        void fallsAsTheFloatsLengthen() {
            double plain = Interlacement.interlacementFactor(Plain.plainWeave());
            double twill = Interlacement.interlacementFactor(Twill.twillWeave("2/2"));
            double satin = Interlacement.interlacementFactor(Satin.satinWeave(8, 3));
            assertThat(twill).isLessThan(plain);
            assertThat(satin).isLessThan(twill);
        }

        @Test
        void readsATwoByTwoBasketAtAHalf() {
            assertThat(Interlacement.interlacementFactor(Plain.basketWeave(2))).isEqualTo(0.5);
        }

        @Test
        void isAQuarterForTheEightThreadSatin() {
            assertThat(Interlacement.interlacementFactor(Satin.satinWeave(8, 3)))
                    .isEqualTo(0.25);
        }
    }

    @Nested
    class AverageFloatFromIntersections {

        @Test
        void isOneEachWayForPlainWeave() {
            assertThat(Interlacement.averageFloatFromIntersections(Plain.plainWeave()))
                    .isEqualTo(new WarpWeft(1, 1));
        }

        @Test
        void isTwoEachWayForA22Twill() {
            assertThat(Interlacement.averageFloatFromIntersections(Twill.twillWeave("2/2")))
                    .isEqualTo(new WarpWeft(2, 2));
        }

        @Test
        void isFourEachWayForAnEightThreadSatin() {
            assertThat(Interlacement.averageFloatFromIntersections(Satin.satinWeave(8, 3)))
                    .isEqualTo(new WarpWeft(4, 4));
        }
    }

    @Nested
    class AshenhurstDivisor {

        @Test
        void isOneForPlainWeaveWhichNeedsNoAllowanceAtAll() {
            assertThat(Interlacement.ashenhurstDivisor(Plain.plainWeave())).isEqualTo(new WarpWeft(1, 1));
        }

        @Test
        void risesWithTheFloatLengthSoTheClothMayBeSetCloser() {
            assertThat(Interlacement.ashenhurstDivisor(Twill.twillWeave("2/2"))).isEqualTo(new WarpWeft(2, 2));
            assertThat(Interlacement.ashenhurstDivisor(Satin.satinWeave(5, 2))).isEqualTo(new WarpWeft(2.5, 2.5));
        }

        @Test
        void readsAWarpRibUnevenlyBecauseOnlyOneDirectionFloats() {
            WarpWeft divisor = Interlacement.ashenhurstDivisor(Matrix.parse("X.\nX.\n.X\n.X"));
            assertThat(divisor.warp()).isEqualTo(2);
            assertThat(divisor.weft()).isEqualTo(1);
        }
    }

    @Nested
    class LooseEnds {

        @Test
        void findsAnEndThatNeverCrosses() {
            assertThat(Interlacement.looseEnds(Matrix.parse("X..\n.X."))).containsExactly(3);
        }

        @Test
        void catchesAnEndThatIsAlwaysUpAsWellAsOneAlwaysDown() {
            assertThat(Interlacement.looseEnds(Matrix.parse("XX.\n.X."))).containsExactly(2, 3);
        }

        @Test
        void isEmptyForASoundCloth() {
            assertThat(Interlacement.looseEnds(Twill.twillWeave("2/2"))).isEmpty();
        }
    }

    @Nested
    class FirmnessClass {

        @Test
        void callsPlainWeaveFirmAndASatinLoose() {
            assertThat(Interlacement.firmnessClass(Plain.plainWeave())).isEqualTo(Firmness.FIRM);
            assertThat(Interlacement.firmnessClass(Twill.twillWeave("2/2"))).isEqualTo(Firmness.MEDIUM);
            assertThat(Interlacement.firmnessClass(Satin.satinWeave(8, 3))).isEqualTo(Firmness.OPEN);
            assertThat(Interlacement.firmnessClass(Satin.satinWeave(16, 7))).isEqualTo(Firmness.LOOSE);
            assertThat(Firmness.MEDIUM).hasToString("medium");
        }
    }

    @Nested
    class SettRatio {

        @Test
        void saysHowMuchCloserASatinMayBeSetThanPlainWeave() {
            assertThat(Interlacement.settRatio(Satin.satinWeave(5, 2), Plain.plainWeave()))
                    .isEqualTo(new WarpWeft(2.5, 2.5));
        }

        @Test
        void isOneForAClothAgainstItself() {
            assertThat(Interlacement.settRatio(Twill.twillWeave("2/2"), Twill.twillWeave("2/2")))
                    .isEqualTo(new WarpWeft(1, 1));
        }
    }
}
