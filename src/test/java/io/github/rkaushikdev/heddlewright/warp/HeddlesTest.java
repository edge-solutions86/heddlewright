package io.github.rkaushikdev.heddlewright.warp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.heddlewright.draft.Threading;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HeddlesTest {

    private static final List<Integer> THREADING = Threading.straightDraw(4, 400);

    @Nested
    class HeddlesNeeded {

        @Test
        void countsTheEndsOnEachShaft() {
            assertThat(Heddles.heddlesNeeded(THREADING, 4)).containsExactly(100, 100, 100, 100);
        }

        @Test
        void readsAnUnevenThreadingUnevenly() {
            assertThat(Heddles.heddlesNeeded(List.of(1, 1, 1, 2), 2)).containsExactly(3, 1);
        }
    }

    @Nested
    class HeddleShortfalls {

        @Test
        void isEmptyWhenEveryShaftHasEnough() {
            assertThat(Heddles.heddleShortfalls(THREADING, 4, List.of(120, 120, 120, 120)))
                    .isEmpty();
        }

        @Test
        void namesTheShaftThatIsShortAndByHowMuch() {
            assertThat(Heddles.heddleShortfalls(THREADING, 4, List.of(80, 120, 120, 120)))
                    .containsExactly(new Heddles.Shortfall(1, 100, 80, 20));
            assertThat(Heddles.heddleShortfalls(THREADING, 4, List.of(80, 120, 120, 120))
                            .get(0)
                            .toJson())
                    .isEqualTo(Json.obj("shaft", 1, "needed", 100, "available", 80, "short", 20));
        }

        @Test
        void doesNotLetSpareHeddlesOnAnotherShaftCoverIt() {
            assertThat(Heddles.heddleShortfalls(THREADING, 4, List.of(80, 400, 120, 120)))
                    .hasSize(1);
        }

        @Test
        void needsOneAvailableCountPerShaft() {
            assertThatThrownBy(() -> Heddles.heddleShortfalls(THREADING, 4, List.of(120)))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class AssertHeddlesFit {

        @Test
        void passesADraftTheLoomCarries() {
            assertThat(Heddles.assertHeddlesFit(THREADING, 4, List.of(120, 120, 120, 120)))
                    .isSameAs(THREADING);
        }

        @Test
        void refusesOneItDoesNotNamingTheWorstShaft() {
            CapacityException err = catchThrowableOfType(
                    CapacityException.class, () -> Heddles.assertHeddlesFit(THREADING, 4, List.of(80, 60, 120, 120)));
            assertThat(err).isNotNull();
            assertThat(err.detail().get("worst").get("shaft")).isEqualTo(Json.of(2));
        }
    }

    @Nested
    class SpareHeddles {

        @Test
        void reportsTheSparesPerShaftRatherThanAsATotal() {
            assertThat(Heddles.spareHeddles(THREADING, 4, List.of(150, 100, 120, 100)))
                    .containsExactly(
                            new Heddles.Spare(1, 50),
                            new Heddles.Spare(2, 0),
                            new Heddles.Spare(3, 20),
                            new Heddles.Spare(4, 0));
        }

        @Test
        void goesNegativeOnAShaftThatIsShort() {
            assertThat(Heddles.spareHeddles(THREADING, 4, List.of(80, 120, 120, 120))
                            .get(0)
                            .spare())
                    .isEqualTo(-20);
        }
    }

    @Nested
    class HeddleMoves {

        @Test
        void isEmptyWhenNothingNeedsMoving() {
            assertThat(Heddles.heddleMoves(THREADING, 4, List.of(120, 120, 120, 120)))
                    .isEmpty();
        }

        @Test
        void takesHeddlesOffTheShaftWithTheMostSpare() {
            assertThat(Heddles.heddleMoves(THREADING, 4, List.of(80, 200, 130, 100)))
                    .containsExactly(new Heddles.Move(2, 1, 20));
        }

        @Test
        void drawsOnMoreThanOneShaftWhenItHasTo() {
            assertThat(Heddles.heddleMoves(THREADING, 4, List.of(60, 125, 120, 100)))
                    .containsExactly(new Heddles.Move(2, 1, 25), new Heddles.Move(3, 1, 15));
        }

        @Test
        void refusesWhenTheLoomHasNotEnoughAnywhere() {
            assertThatThrownBy(() -> Heddles.heddleMoves(THREADING, 4, List.of(50, 100, 100, 100)))
                    .hasMessageContaining("not enough heddles anywhere");
        }
    }

    @Nested
    class DrawingInHoursAndTyingInHours {

        @Test
        void dividesTheEndsByTheRate() {
            assertThat(Heddles.drawingInHours(4000)).isEqualTo(10);
            assertThat(Heddles.tyingInHours(4000)).isEqualTo(2);
        }

        @Test
        void isFasterToTieInThanToDrawIn() {
            assertThat(Heddles.tyingInHours(4000)).isLessThan(Heddles.drawingInHours(4000));
        }
    }

    @Nested
    class CanTieInAndChangeoverHours {

        @Test
        void tiesInWhenTheThreadingHasNotChanged() {
            assertThat(Heddles.canTieIn(THREADING, new ArrayList<>(THREADING))).isTrue();
            assertThat(Heddles.changeoverHours(THREADING, new ArrayList<>(THREADING))
                            .method())
                    .isEqualTo("tie in");
        }

        @Test
        void drawsInWhenItHas() {
            List<Integer> changed = new ArrayList<>(THREADING);
            changed.set(0, 2);
            assertThat(Heddles.canTieIn(THREADING, changed)).isFalse();
            assertThat(Heddles.changeoverHours(THREADING, changed).method()).isEqualTo("draw in");
        }

        @Test
        void drawsInWhenTheWarpIsADifferentWidth() {
            assertThat(Heddles.canTieIn(THREADING, Threading.straightDraw(4, 300)))
                    .isFalse();
        }

        @Test
        void refusesAChangeWithNoWarpAtAll() {
            assertThatThrownBy(() -> Heddles.changeoverHours(THREADING, List.of()))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }
}
