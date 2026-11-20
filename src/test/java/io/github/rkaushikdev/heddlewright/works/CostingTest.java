package io.github.rkaushikdev.heddlewright.works;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.heddlewright.Fixtures;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CostingTest {

    private static final JsonObject CLOTH = Fixtures.shirtingConstruction();
    private static final JsonObject PRICES = Json.obj("warpPricePerKg", 5, "weftPricePerKg", 4);
    private static final JsonObject LOOM = Json.obj("picksMinute", 200, "picksPerCm", 20, "ratePerHour", 18);
    private static final JsonObject JOB =
            Json.obj("construction", CLOTH, "prices", PRICES, "loom", LOOM, "widthMetres", 1.5);

    private static double roundFour(double value) {
        return Math.round(value * 10000) / 10000.0;
    }

    @Nested
    class YarnCostPerMetre {

        @Test
        void costsTheWarpAndTheWeftSeparately() {
            Costing.YarnCost cost = Costing.yarnCostPerMetre(CLOTH, PRICES, 1.5);
            assertThat(cost.warp()).isEqualTo(0.36);
            assertThat(cost.weft()).isEqualTo(0.24);
            assertThat(cost.total()).isEqualTo(0.6);
            assertThat(cost.gsm()).isEqualTo(88);
        }

        @Test
        void scalesWithTheWidth() {
            Costing.YarnCost narrow = Costing.yarnCostPerMetre(CLOTH, PRICES, 1);
            Costing.YarnCost wide = Costing.yarnCostPerMetre(CLOTH, PRICES, 2);
            assertThat(wide.total()).isCloseTo(narrow.total() * 2, within(0.00005));
        }

        @Test
        void refusesAPriceOfNothing() {
            JsonObject bad = Json.obj("warpPricePerKg", 0, "weftPricePerKg", 4);
            assertThatThrownBy(() -> Costing.yarnCostPerMetre(CLOTH, bad, 1))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class LoomCostPerMetre {

        @Test
        void isTheHourlyRateOverTheMetresAnHour() {
            assertThat(Costing.loomCostPerMetre(LOOM)).isEqualTo(roundFour(18 / (((200.0 * 60) / 20 / 100) * 0.85)));
        }

        @Test
        void costsMoreAtACloserSettBecauseTheLoomMakesLessCloth() {
            assertThat(Costing.loomCostPerMetre(LOOM.with("picksPerCm", 40)))
                    .isCloseTo(Costing.loomCostPerMetre(LOOM) * 2, within(0.0005));
        }

        @Test
        void costsMoreAtALowerEfficiency() {
            assertThat(Costing.loomCostPerMetre(LOOM.with("efficiency", 0.5)))
                    .isGreaterThan(Costing.loomCostPerMetre(LOOM));
        }

        @Test
        void refusesAnEfficiencyOfNothingWhichNeverFinishes() {
            assertThatThrownBy(() -> Costing.loomCostPerMetre(LOOM.with("efficiency", 0)))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class WithWaste {

        @Test
        void addsTheShedAllowance() {
            assertThat(Costing.withWaste(1)).isEqualTo(1.04);
            assertThat(Costing.withWaste(1, 0.1)).isEqualTo(1.1);
            assertThat(Costing.withWaste(1, 0)).isEqualTo(1);
        }
    }

    @Nested
    class CostPerMetre {

        @Test
        void addsTheYarnTheWeavingAndTheOverheads() {
            Costing.Cost cost = Costing.costPerMetre(JOB.with("overheads", 0.2));
            assertThat(cost.total()).isCloseTo(cost.yarnWithWaste() + cost.weaving() + 0.2, within(0.00005));
            assertThat(cost.overheads()).isEqualTo(0.2);
        }

        @Test
        void readsTheLoomAsMostOfTheCostAtACloseSettNotTheYarn() {
            // Twenty picks a centimetre at two hundred picks a minute is about five metres an hour, so
            // an hour of loom time buys very little cloth.
            assertThat(Costing.costPerMetre(JOB).yarnShare()).isLessThan(0.25);
        }

        @Test
        void handsTheCostBackToTheYarnOnceTheLoomTimeIsCheap() {
            JsonObject cheapLoom = JOB.with("loom", LOOM.with("ratePerHour", 1));
            assertThat(Costing.costPerMetre(cheapLoom).yarnShare()).isGreaterThan(0.7);
        }

        @Test
        void carriesTheClothWeightThroughSoItCanBeChecked() {
            assertThat(Costing.costPerMetre(JOB).gsm()).isEqualTo(88);
        }
    }

    @Nested
    class PriceForMarginAndMarginAtPrice {

        @Test
        void areInversesOfEachOther() {
            double price = Costing.priceForMargin(1, 0.25);
            assertThat(price).isCloseTo(1.3333, within(0.0005));
            assertThat(Costing.marginAtPrice(1, price)).isCloseTo(0.25, within(0.0005));
        }

        @Test
        void aMarginOfNothingPricesAtCost() {
            assertThat(Costing.priceForMargin(2, 0)).isEqualTo(2);
            assertThat(Costing.marginAtPrice(2, 2)).isEqualTo(0);
        }

        @Test
        void refusesAPriceBelowTheCost() {
            assertThatThrownBy(() -> Costing.marginAtPrice(2, 1)).hasMessageContaining("below the cost");
        }
    }

    @Nested
    class Compare {

        @Test
        void saysWhichTermAChangeMoved() {
            JsonObject dearerYarn = JOB.with("prices", Json.obj("warpPricePerKg", 10, "weftPricePerKg", 4));
            Costing.Comparison comparison = Costing.compare(JOB, dearerYarn);
            assertThat(comparison.movedBy()).isEqualTo("yarn");
            assertThat(comparison.difference()).isGreaterThan(0);
        }

        @Test
        void saysWeavingWhenTheLoomTimeMovedInstead() {
            JsonObject slower = JOB.with("loom", LOOM.with("picksMinute", 60));
            assertThat(Costing.compare(JOB, slower).movedBy()).isEqualTo("weaving");
        }

        @Test
        void reportsNoDifferenceForTheSameJobTwice() {
            assertThat(Costing.compare(JOB, JOB).difference()).isEqualTo(0);
        }
    }

    @Nested
    class RunningSpeed {

        @Test
        void isTheQuotedSpeedWhenThereIsOnlyOneWeft() {
            assertThat(Costing.runningSpeed(Json.obj("picksMinute", 200))).isEqualTo(200);
        }

        @Test
        void comesDownForAClothThatChangesBoxOnEveryPick() {
            // Pick and pick changes twice over a two pick repeat, so the box is lifting as often as
            // the loom is picking.
            assertThat(Costing.runningSpeed(Json.obj("picksMinute", 200, "weftPattern", List.of("a", "b"))))
                    .isEqualTo(181.8);
        }

        @Test
        void comesDownLessForAClothThatChangesEveryOtherPick() {
            double twoAndTwo =
                    Costing.runningSpeed(Json.obj("picksMinute", 200, "weftPattern", List.of("a", "a", "b", "b")));
            assertThat(twoAndTwo)
                    .isGreaterThan(
                            Costing.runningSpeed(Json.obj("picksMinute", 200, "weftPattern", List.of("a", "b"))));
            assertThat(twoAndTwo).isLessThan(200);
        }

        @Test
        void refusesAPatternWithMoreColoursThanTheBoxesGiven() {
            assertThatThrownBy(() -> Costing.runningSpeed(
                            Json.obj("picksMinute", 200, "weftPattern", List.of("a", "b", "c"), "boxes", 2)))
                    .isInstanceOf(CapacityException.class);
        }

        @Test
        void countsTheBoxesOffThePatternWhenItIsNotTold() {
            assertThat(Costing.runningSpeed(Json.obj("picksMinute", 200, "weftPattern", List.of("a", "b", "c"))))
                    .isLessThan(200);
        }
    }

    @Nested
    class AColourWovenClothOnTheLoom {

        private final JsonObject loom = Json.obj("picksMinute", 200, "picksPerCm", 20, "ratePerHour", 9);
        private final JsonObject shared = Json.obj("construction", CLOTH, "prices", PRICES, "widthMetres", 1.5);

        @Test
        void costsMoreLoomTimeThanTheSameSettInOneWeft() {
            double plain = Costing.loomCostPerMetre(loom);
            double woven = Costing.loomCostPerMetre(loom.with("weftPattern", List.of("a", "b")));
            assertThat(woven).isGreaterThan(plain);
            assertThat(woven / plain).isCloseTo(200 / 181.8, within(0.005));
        }

        @Test
        void carriesThroughTheWholeCostSinceTheLoomPassesWhatItIsGiven() {
            Costing.Cost plain = Costing.costPerMetre(shared.with("loom", loom));
            Costing.Cost woven = Costing.costPerMetre(shared.with("loom", loom.with("weftPattern", List.of("a", "b"))));
            assertThat(woven.weaving()).isGreaterThan(plain.weaving());
            assertThat(woven.yarn()).isEqualTo(plain.yarn());
            assertThat(woven.total()).isGreaterThan(plain.total());
        }

        @Test
        void readsAsAWeavingChangeRatherThanAYarnOne() {
            Costing.Comparison moved = Costing.compare(
                    shared.with("loom", loom), shared.with("loom", loom.with("weftPattern", List.of("a", "b"))));
            assertThat(moved.movedBy()).isEqualTo("weaving");
            assertThat(moved.yarnMoved()).isEqualTo(0);
        }
    }
}
