package io.github.rkaushikdev.heddlewright.warp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.warp.Creel.Stripe;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CreelTest {

    private static final List<Stripe> ORDER = List.of(new Stripe("indigo", 12), new Stripe("ecru", 4));

    private static double roundThree(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    @Nested
    class NormaliseColourOrder {

        @Test
        void keepsTheStripesInOrder() {
            assertThat(Creel.normaliseColourOrder(ORDER)).isEqualTo(ORDER);
        }

        @Test
        void refusesAStripeWithNoColourOrNoEnds() {
            assertThatThrownBy(() -> Creel.normaliseColourOrder(List.of(new Stripe(null, 4))))
                    .isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Creel.normaliseColourOrder(List.of(new Stripe("ecru", 0))))
                    .isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Creel.normaliseColourOrder(List.of()))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class OrderRepeat {

        @Test
        void addsTheStripesUp() {
            assertThat(Creel.orderRepeat(ORDER)).isEqualTo(16);
        }
    }

    @Nested
    class ExpandColourOrder {

        @Test
        void runsTheStripesOutAcrossTheWarp() {
            List<String> ends = Creel.expandColourOrder(ORDER, 20);
            assertThat(ends.subList(0, 12)).allMatch("indigo"::equals);
            assertThat(ends.subList(12, 16)).allMatch("ecru"::equals);
            assertThat(ends.get(16)).isEqualTo("indigo");
        }

        @Test
        void cutsTheLastStripeShortRatherThanOverrunning() {
            assertThat(Creel.expandColourOrder(ORDER, 14)).hasSize(14).last().isEqualTo("ecru");
        }
    }

    @Nested
    class EndsByColour {

        @Test
        void countsTheEndsOfEachColourMostFirst() {
            assertThat(Creel.endsByColour(ORDER, 32)).containsExactly(new Stripe("indigo", 24), new Stripe("ecru", 8));
        }

        @Test
        void addsBackToTheWarpWidth() {
            assertThat(Creel.endsByColour(ORDER, 100).stream()
                            .mapToInt(Stripe::ends)
                            .sum())
                    .isEqualTo(100);
        }
    }

    @Nested
    class CreelPlan {

        @Test
        void needsOnePackagePerEndWhenTheWarpIsWarpedInOnePass() {
            Creel.Plan plan = Creel.creelPlan(ORDER, 32);
            assertThat(plan.packages()).isEqualTo(32);
            assertThat(plan.colours()).isEqualTo(2);
        }

        @Test
        void dividesThePackagesBetweenTheSections() {
            assertThat(Creel.creelPlan(ORDER, 320, 10).packages()).isEqualTo(32);
        }

        @Test
        void roundsAColourUpRatherThanLeavingAnEndUnsupplied() {
            Creel.Plan plan = Creel.creelPlan(ORDER, 33, 2);
            int supplied = plan.positions().stream()
                    .mapToInt(entry -> entry.packages() * 2)
                    .sum();
            assertThat(supplied).isGreaterThanOrEqualTo(33);
        }
    }

    @Nested
    class AssertCreelFits {

        @Test
        void passesAPlanTheCreelHolds() {
            Creel.Plan plan = Creel.creelPlan(ORDER, 32);
            assertThat(Creel.assertCreelFits(plan, 400)).isSameAs(plan);
        }

        @Test
        void saysHowManyPackagesAreOver() {
            Creel.Plan plan = Creel.creelPlan(ORDER, 640);
            CapacityException err =
                    catchThrowableOfType(CapacityException.class, () -> Creel.assertCreelFits(plan, 400));
            assertThat(err).isNotNull();
            assertThat(err.detail().get("over")).isEqualTo(Json.of(240));
        }
    }

    @Nested
    class StripeWidths {

        @Test
        void turnsTheStripesIntoInchesAtTheSett() {
            assertThat(Creel.stripeWidths(ORDER, 24))
                    .containsExactly(
                            new Creel.StripeWidth("indigo", 12, 0.5),
                            new Creel.StripeWidth("ecru", 4, roundThree(4.0 / 24)));
        }
    }

    @Nested
    class MirrorOrder {

        @Test
        void mirrorsWithoutRepeatingTheTurningStripe() {
            List<Stripe> mirrored =
                    Creel.mirrorOrder(List.of(new Stripe("a", 4), new Stripe("b", 2), new Stripe("c", 6)));
            assertThat(mirrored.stream().map(Stripe::colour)).containsExactly("a", "b", "c", "b", "a");
        }

        @Test
        void isSymmetricAboutTheMiddle() {
            assertThat(Creel.mirrorOrder(ORDER).stream().map(Stripe::colour))
                    .containsExactly("indigo", "ecru", "indigo");
        }

        @Test
        void refusesASingleStripeWhichHasNothingToMirror() {
            assertThatThrownBy(() -> Creel.mirrorOrder(List.of(new Stripe("a", 4))))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class StripeSpans {

        @Test
        void saysWhereEachStripeSitsAcrossTheWarp() {
            assertThat(Creel.stripeSpans(ORDER, 32))
                    .containsExactly(
                            new Creel.Span("indigo", 1, 12, 12),
                            new Creel.Span("ecru", 13, 16, 4),
                            new Creel.Span("indigo", 17, 28, 12),
                            new Creel.Span("ecru", 29, 32, 4));
        }

        @Test
        void coversEveryEndExactlyOnce() {
            List<Creel.Span> spans = Creel.stripeSpans(ORDER, 50);
            assertThat(spans.stream().mapToInt(Creel.Span::ends).sum()).isEqualTo(50);
            assertThat(spans.get(spans.size() - 1).to()).isEqualTo(50);
        }
    }

    @Nested
    class YarnByColour {

        @Test
        void worksTheWeightOutFromTheCountInTex() {
            List<Creel.ColourYarn> yarn = Creel.yarnByColour(ORDER, 32, 1000, Map.of("indigo", 30, "ecru", 30));
            assertThat(yarn.get(0)).isEqualTo(new Creel.ColourYarn("indigo", 24, 24000, 0.72));
        }

        @Test
        void refusesAColourWithNoCountGiven() {
            assertThatThrownBy(() -> Creel.yarnByColour(ORDER, 32, 1000, Map.of("indigo", 30)))
                    .hasMessageContaining("texByColour.ecru");
        }
    }
}
