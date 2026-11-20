package io.github.rkaushikdev.heddlewright.loom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The shed timing over a turn of the crank, named apart from the draft's rising and sinking shed. */
class ShedTimingTest {

    /** The default shed with only the cross angle moved. */
    private static ShedTiming.Cycle crossingAt(double crossAngle) {
        return ShedTiming.shedCycle(crossAngle, 140, 60);
    }

    @Nested
    class NormaliseAngle {

        @Test
        void wrapsIntoASingleTurn() {
            assertThat(ShedTiming.normaliseAngle(370)).isEqualTo(10);
            assertThat(ShedTiming.normaliseAngle(-10)).isEqualTo(350);
            assertThat(ShedTiming.normaliseAngle(360)).isEqualTo(0);
            assertThat(ShedTiming.normaliseAngle(0)).isEqualTo(ShedTiming.BEAT_UP);
        }

        @Test
        void refusesSomethingThatIsNotAnAngle() {
            // A timing that is not a number, such as the word "early", has no double form; NaN
            // stands in for it.
            assertThatThrownBy(() -> ShedTiming.normaliseAngle(Double.NaN))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class AngleWithin {

        @Test
        void readsAPlainArc() {
            assertThat(ShedTiming.angleWithin(90, 0, 180)).isTrue();
            assertThat(ShedTiming.angleWithin(270, 0, 180)).isFalse();
        }

        @Test
        void readsAnArcThatCrossesBeatUp() {
            assertThat(ShedTiming.angleWithin(350, 300, 60)).isTrue();
            assertThat(ShedTiming.angleWithin(10, 300, 60)).isTrue();
            assertThat(ShedTiming.angleWithin(180, 300, 60)).isFalse();
        }

        @Test
        void includesBothEnds() {
            assertThat(ShedTiming.angleWithin(0, 0, 180)).isTrue();
            assertThat(ShedTiming.angleWithin(180, 0, 180)).isTrue();
        }
    }

    @Nested
    class ShedCycle {

        @Test
        void opensAQuarterTurnAfterTheCrossAndHoldsForTheDwell() {
            assertThat(ShedTiming.shedCycle()).isEqualTo(new ShedTiming.Cycle(300, 0, 140, 140, 60));
        }

        @Test
        void followsADifferentCrossAngle() {
            assertThat(crossingAt(330).fullyOpen()).isEqualTo(30);
        }

        @Test
        void refusesACycleThatWillNotFitInsideOneTurn() {
            assertThatThrownBy(() -> ShedTiming.shedCycle(300, 300, 60)).isInstanceOf(DraftException.class);
        }
    }

    @Nested
    class IsShedOpenAt {

        private final ShedTiming.Cycle cycle = ShedTiming.shedCycle();

        @Test
        void isOpenThroughTheDwell() {
            assertThat(ShedTiming.isShedOpenAt(cycle, 0)).isTrue();
            assertThat(ShedTiming.isShedOpenAt(cycle, 70)).isTrue();
            assertThat(ShedTiming.isShedOpenAt(cycle, 140)).isTrue();
        }

        @Test
        void isShutOnTheWayRoundToTheNextCross() {
            assertThat(ShedTiming.isShedOpenAt(cycle, 200)).isFalse();
            assertThat(ShedTiming.isShedOpenAt(cycle, 300)).isFalse();
        }
    }

    @Nested
    class ShuttleWindow {

        @Test
        void landsTheShuttleAFlightAfterTheLaunch() {
            assertThat(ShedTiming.shuttleWindow()).isEqualTo(new ShedTiming.ShuttleWindow(10, 130, 120));
        }

        @Test
        void wrapsAnArrivalPastBeatUp() {
            assertThat(ShedTiming.shuttleWindow(300, 120).arrival()).isEqualTo(60);
        }

        @Test
        void refusesAFlightOfAWholeTurnOrMore() {
            assertThatThrownBy(() -> ShedTiming.shuttleWindow(10, 360)).isInstanceOf(DraftException.class);
        }
    }

    @Nested
    class ShuttleClearance {

        @Test
        void clearsOnTheDefaultTimingWithRoomAtBothEnds() {
            ShedTiming.Clearance clearance =
                    ShedTiming.shuttleClearance(ShedTiming.shedCycle(), ShedTiming.shuttleWindow());
            assertThat(clearance).isEqualTo(new ShedTiming.Clearance(true, 10, 10, 140));
        }

        @Test
        void reportsANegativeExitMarginWhenTheFlightIsTooLong() {
            ShedTiming.Clearance clearance =
                    ShedTiming.shuttleClearance(ShedTiming.shedCycle(), ShedTiming.shuttleWindow(10, 160));
            assertThat(clearance.clears()).isFalse();
            assertThat(clearance.exitMargin()).isEqualTo(-30);
        }

        @Test
        void aLaterLaunchEatsIntoTheExitMarginOneDegreeAtATime() {
            ShedTiming.Clearance early =
                    ShedTiming.shuttleClearance(ShedTiming.shedCycle(), ShedTiming.shuttleWindow(10, 120));
            ShedTiming.Clearance late =
                    ShedTiming.shuttleClearance(ShedTiming.shedCycle(), ShedTiming.shuttleWindow(15, 120));
            assertThat(late.entryMargin() - early.entryMargin()).isEqualTo(5);
            assertThat(early.exitMargin() - late.exitMargin()).isEqualTo(5);
        }

        @Test
        void aLongerDwellBuysClearanceBack() {
            ShedTiming.Cycle wide = ShedTiming.shedCycle(300, 180, 60);
            assertThat(ShedTiming.shuttleClearance(wide, ShedTiming.shuttleWindow(10, 160))
                            .clears())
                    .isTrue();
        }
    }

    @Nested
    class AssertShuttleClears {

        @Test
        void returnsTheClearanceWhenTheTimingIsSound() {
            assertThat(ShedTiming.assertShuttleClears(ShedTiming.shedCycle(), ShedTiming.shuttleWindow())
                            .clears())
                    .isTrue();
        }

        @Test
        void refusesATimingThatFliesIntoAClosingShed() {
            DraftException err = catchThrowableOfType(
                    DraftException.class,
                    () -> ShedTiming.assertShuttleClears(ShedTiming.shedCycle(), ShedTiming.shuttleWindow(10, 200)));
            assertThat(err).isNotNull();
            assertThat(err.detail().get("flightDegrees")).isEqualTo(Json.of(200));
            assertThat(err.detail().get("dwellDegrees")).isEqualTo(Json.of(140));
        }
    }

    @Nested
    class SheddingLead {

        @Test
        void callsACrossBeforeBeatUpEarly() {
            assertThat(ShedTiming.sheddingLead(crossingAt(300))).isEqualTo(new ShedTiming.Lead(60, "early"));
        }

        @Test
        void callsACrossAfterBeatUpLate() {
            assertThat(ShedTiming.sheddingLead(crossingAt(30)).timing()).isEqualTo("late");
        }

        @Test
        void callsACrossAtBeatUpLevel() {
            assertThat(ShedTiming.sheddingLead(crossingAt(0))).isEqualTo(new ShedTiming.Lead(0, "level"));
        }
    }

    @Nested
    class TimingChart {

        @Test
        void listsTheWholePickInCrankOrder() {
            List<ShedTiming.Event> chart = ShedTiming.timingChart(ShedTiming.shedCycle(), ShedTiming.shuttleWindow());
            assertThat(chart)
                    .extracting(ShedTiming.Event::event)
                    .containsExactly(
                            "beat-up",
                            "shed fully open",
                            "shuttle launched",
                            "shuttle boxed",
                            "shed starts closing",
                            "shed crosses");
        }

        @Test
        void neverGoesBackwards() {
            List<ShedTiming.Event> chart = ShedTiming.timingChart(crossingAt(330), ShedTiming.shuttleWindow(40, 120));
            for (int i = 1; i < chart.size(); i += 1) {
                assertThat(chart.get(i).at())
                        .isGreaterThanOrEqualTo(chart.get(i - 1).at());
            }
        }
    }
}
