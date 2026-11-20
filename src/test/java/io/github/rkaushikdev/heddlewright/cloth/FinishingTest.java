package io.github.rkaushikdev.heddlewright.cloth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.heddlewright.cloth.Finishing.Cloth;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FinishingTest {

    private static final List<String> ROUTE = List.of("scour", "bleach", "dye", "sanforise");
    private static final Cloth GREIGE = Cloth.of(100, 160, 150);

    @Nested
    class ListProcesses {

        @Test
        void listsThemInNameOrderWithTheirEffects() {
            List<String> names = Finishing.listProcesses().stream()
                    .map(Finishing.Process::name)
                    .toList();
            assertThat(names).isSorted();
            assertThat(Finishing.processByName("sanforise").lengthShrink()).isEqualTo(3);
        }

        @Test
        void hasOneProcessThatWidensRatherThanShrinks() {
            assertThat(Finishing.processByName("calender").widthShrink()).isLessThan(0);
        }

        @Test
        void namesWhatItKnowsWhenItDoesNotKnowOne() {
            NotFoundException err =
                    catchThrowableOfType(NotFoundException.class, () -> Finishing.processByName("starch"));
            assertThat(err).isNotNull();
            assertThat(((JsonArray) err.detail().get("known")).items()).contains(Json.of("dye"));
        }
    }

    @Nested
    class RouteEffect {

        @Test
        void compoundsTheRatiosRatherThanAddingThePercentages() {
            Finishing.Effect effect = Finishing.routeEffect(ROUTE);
            Finishing.NaiveShrink naive = Finishing.naiveRouteShrink(ROUTE);
            assertThat(effect.lengthShrinkPercent()).isLessThan(naive.lengthShrinkPercent());
            assertThat(effect.lengthShrinkPercent()).isGreaterThan(naive.lengthShrinkPercent() - 1);
        }

        @Test
        void multipliesTheRatiosStageByStage() {
            double wanted = 0.985 * 0.99 * 0.99 * 0.97;
            assertThat(Finishing.routeEffect(ROUTE).lengthRatio()).isCloseTo(wanted, within(0.0000005));
        }

        @Test
        void isTheProcessItselfForARouteOfOne() {
            assertThat(Finishing.routeEffect(List.of("sanforise")).lengthRatio())
                    .isCloseTo(0.97, within(0.0000005));
        }

        @Test
        void letsAWideningProcessPullTheWidthBackOut() {
            assertThat(Finishing.routeEffect(List.of("calender")).widthRatio()).isGreaterThan(1);
        }
    }

    @Nested
    class FinishedPiece {

        @Test
        void shrinksTheLengthAndTheWidth() {
            Cloth finished = Finishing.finishedPiece(GREIGE, ROUTE);
            assertThat(finished.metres()).isLessThan(100);
            assertThat(finished.widthCm()).isLessThan(160);
        }

        @Test
        void raisesTheWeightPerSquareMetreAsTheClothShrinksIntoItself() {
            assertThat(Finishing.finishedPiece(GREIGE, List.of("sanforise"))
                            .gsm()
                            .getAsDouble())
                    .isGreaterThan(150);
        }

        @Test
        void leavesTheWeightOutWhenNoneWasGiven() {
            assertThat(Finishing.finishedPiece(Cloth.of(100, 160), ROUTE).gsm()).isEmpty();
        }

        @Test
        void refusesAPieceOfNoLength() {
            assertThatThrownBy(() -> Finishing.finishedPiece(Cloth.of(0, 160), ROUTE))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class GreigeNeeded {

        @Test
        void worksBackwardsFromTheFinishedPiece() {
            Cloth needed = Finishing.greigeNeeded(Cloth.of(100, 150), ROUTE);
            assertThat(needed.metres()).isGreaterThan(100);
            assertThat(needed.widthCm()).isGreaterThan(150);
        }

        @Test
        void isTheInverseOfFinishing() {
            Cloth needed = Finishing.greigeNeeded(Cloth.of(100, 150), ROUTE);
            Cloth finished = Finishing.finishedPiece(needed, ROUTE);
            assertThat(finished.metres()).isCloseTo(100, within(0.005));
            assertThat(finished.widthCm()).isCloseTo(150, within(0.005));
        }
    }

    @Nested
    class MeetsWidth {

        @Test
        void passesAGreigeWideEnoughToFinishOnContract() {
            Finishing.WidthReading reading = Finishing.meetsWidth(GREIGE, ROUTE, 150);
            assertThat(reading.meets()).isTrue();
            assertThat(reading.shortfallCm()).isLessThan(0);
        }

        @Test
        void failsOneThatFinishesTooNarrow() {
            Finishing.WidthReading reading = Finishing.meetsWidth(Cloth.of(100, 150), ROUTE, 150);
            assertThat(reading.meets()).isFalse();
            assertThat(reading.shortfallCm()).isGreaterThan(0);
        }

        @Test
        void letsASmallShortfallThroughOnTheTolerance() {
            Cloth narrow = Cloth.of(100, 156);
            assertThat(Finishing.meetsWidth(narrow, ROUTE, 150).meets()).isTrue();
            assertThat(Finishing.meetsWidth(narrow, ROUTE, 150, 0).meets()).isFalse();
        }

        @Test
        void refusesARouteThatWillNotMakeTheContract() {
            assertThatThrownBy(() -> Finishing.assertMeetsWidth(Cloth.of(100, 150), ROUTE, 150))
                    .hasMessageContaining("narrower than the contract");
            assertThat(Finishing.assertMeetsWidth(GREIGE, ROUTE, 150).meets()).isTrue();
        }
    }

    @Nested
    class AssertRouteKnown {

        @Test
        void passesARouteOfKnownProcesses() {
            assertThat(Finishing.assertRouteKnown(ROUTE)).isSameAs(ROUTE);
        }

        @Test
        void refusesOneWithAProcessNobodyHas() {
            assertThatThrownBy(() -> Finishing.assertRouteKnown(List.of("scour", "starch")))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> Finishing.assertRouteKnown(List.of()))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }
}
