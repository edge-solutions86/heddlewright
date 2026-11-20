package io.github.rkaushikdev.heddlewright.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WeightTest {

    private static final Construction SHIRTING = Construction.of(24, 20, 20, 20);

    @Nested
    class WarpWeightGsmAndWeftWeightGsm {

        @Test
        void areATenthOfTheThreadsPerCmTimesTheTex() {
            assertThat(Weight.warpWeightGsm(SHIRTING)).isEqualTo(48);
            assertThat(Weight.weftWeightGsm(SHIRTING)).isEqualTo(40);
        }

        @Test
        void riseWithTheCrimp() {
            assertThat(Weight.warpWeightGsm(SHIRTING.warpCrimp(10))).isEqualTo(52.8);
            assertThat(Weight.weftWeightGsm(SHIRTING.weftCrimp(5))).isEqualTo(42);
        }

        @Test
        void readACountInAnotherSystemByConvertingFirst() {
            double asTex = Weight.warpWeightGsm(SHIRTING.warpCount(29.527).system("tex"));
            double asNe = Weight.warpWeightGsm(SHIRTING.warpCount(20).system("ne"));
            assertThat(asNe).isCloseTo(asTex, within(0.05));
        }

        @Test
        void refuseASettOfNothing() {
            assertThatThrownBy(() -> Weight.warpWeightGsm(SHIRTING.endsPerCm(0)))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class ClothWeightGsm {

        @Test
        void addsTheTwoSystemsAndSaysWhichCarriesTheWeight() {
            Weight.ClothWeight weight = Weight.clothWeightGsm(SHIRTING);
            assertThat(weight.total()).isEqualTo(88);
            assertThat(weight.warpShare()).isCloseTo(48.0 / 88, within(0.5e-4));
        }

        @Test
        void shiftsTheShareWhenOneSystemIsSetCloser() {
            assertThat(Weight.clothWeightGsm(SHIRTING.endsPerCm(40)).warpShare())
                    .isGreaterThan(Weight.clothWeightGsm(SHIRTING).warpShare());
        }
    }

    @Nested
    class GsmAndOunces {

        @Test
        void convertsACommonShirtingWeight() {
            assertThat(Weight.gsmToOuncesPerSquareYard(339)).isCloseTo(10, within(0.05));
        }

        @Test
        void roundTripsBothWays() {
            assertThat(Weight.ouncesPerSquareYardToGsm(Weight.gsmToOuncesPerSquareYard(250)))
                    .isCloseTo(250, within(0.05));
        }

        @Test
        void refusesAWeightOfNothing() {
            assertThatThrownBy(() -> Weight.gsmToOuncesPerSquareYard(0)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class PieceWeightKg {

        @Test
        void multipliesTheAreaByTheWeight() {
            assertThat(Weight.pieceWeightKg(200, 50, 1.5)).isEqualTo(15);
        }

        @Test
        void scalesWithEveryTerm() {
            double base = Weight.pieceWeightKg(200, 50, 1.5);
            assertThat(Weight.pieceWeightKg(400, 50, 1.5)).isEqualTo(base * 2);
            assertThat(Weight.pieceWeightKg(200, 100, 1.5)).isEqualTo(base * 2);
        }
    }

    @Nested
    class GramsPerLinearMetre {

        @Test
        void isTheRunningWeightAtTheClothWidth() {
            assertThat(Weight.gramsPerLinearMetre(200, 1.5)).isEqualTo(300);
        }
    }

    @Nested
    class YarnCostPerSquareMetre {

        @Test
        void followsTheWeightSplit() {
            Weight.YarnCost cost = Weight.yarnCostPerSquareMetre(SHIRTING, 5, 5);
            assertThat(cost.warp()).isEqualTo(0.24);
            assertThat(cost.weft()).isEqualTo(0.2);
            assertThat(cost.total()).isEqualTo(0.44);
        }

        @Test
        void risesWhenTheWarpYarnIsTheDearOneOnAWarpHeavyCloth() {
            double cheapWarp = Weight.yarnCostPerSquareMetre(SHIRTING, 4, 8).total();
            double dearWarp = Weight.yarnCostPerSquareMetre(SHIRTING, 8, 4).total();
            assertThat(dearWarp).isGreaterThan(cheapWarp);
        }
    }

    @Nested
    class PicksForWeight {

        @Test
        void worksOutThePicksATargetWeightNeeds() {
            assertThat(Weight.picksForWeight(88, SHIRTING)).isEqualTo(20);
        }

        @Test
        void agreesWithTheWeightCalculationItInverts() {
            double picks = Weight.picksForWeight(120, SHIRTING);
            assertThat(Weight.clothWeightGsm(SHIRTING.picksPerCm(picks)).total())
                    .isCloseTo(120, within(0.05));
        }

        @Test
        void isNothingWhenTheWarpAloneAlreadyWeighsMoreThanTheTarget() {
            assertThat(Weight.picksForWeight(10, SHIRTING)).isEqualTo(0);
        }
    }
}
