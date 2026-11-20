package io.github.rkaushikdev.heddlewright.works;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.loom.Loom;
import io.github.rkaushikdev.heddlewright.loom.Registry;
import io.github.rkaushikdev.heddlewright.util.ConflictException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.works.Production.Outturn;
import io.github.rkaushikdev.heddlewright.works.Schedule.Plan;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductionTest {

    private static final List<Outturn> BOOK = Production.recordBook(List.of(
            Json.obj("order", "SO-1", "loom", "N1", "shift", "day", "metres", 120, "hours", 8, "stops", 16),
            Json.obj("order", "SO-1", "loom", "N1", "shift", "night", "metres", 96, "hours", 8, "stops", 30),
            Json.obj("order", "SO-2", "loom", "W1", "shift", "day", "metres", 200, "hours", 8, "stops", 10)));

    private static final Loom N1 = Registry.createLoom(
            Json.obj("reference", "N1", "shedding", "dobby", "reedWidthCm", 120, "shafts", 8, "picksMinute", 200));

    private static final Loom W1 = Registry.createLoom(
            Json.obj("reference", "W1", "shedding", "dobby", "reedWidthCm", 260, "shafts", 16, "picksMinute", 200));

    private static Schedule.Run run(String order, String style, double widthCm) {
        return Schedule.createRun(Json.obj(
                "order",
                order,
                "style",
                style,
                "metres",
                400,
                "picksPerCm",
                20,
                "requirements",
                Registry.styleRequirements(Json.obj("widthCm", widthCm, "shafts", 4))));
    }

    private static double roundThree(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    private static JsonObject row(List<JsonObject> rows, String label, String value) {
        return rows.stream()
                .filter(entry -> entry.get(label).equals(Json.of(value)))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    class RecordOutturn {

        @Test
        void fillsInTheShiftAndTheStopCount() {
            Outturn entry = Production.recordOutturn(Json.obj("order", "SO-1", "loom", "N1", "metres", 10, "hours", 1));
            assertThat(entry.shift()).isEqualTo("day");
            assertThat(entry.stops()).isEqualTo(0);
            assertThat(entry.at()).isEqualTo(Json.NULL);
        }

        @Test
        void refusesAnEntryWithNoLengthOrNoHours() {
            JsonObject bad = Json.obj("order", "SO-1", "loom", "N1", "metres", 0, "hours", 1);
            assertThatThrownBy(() -> Production.recordOutturn(bad)).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Production.recordOutturn(bad.with("metres", 1, "hours", 0)))
                    .isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void namesTheEntryThatIsWrong() {
            assertThatThrownBy(() -> Production.recordOutturn(Json.obj("loom", "N1", "metres", 1, "hours", 1), 3))
                    .hasMessageContaining("outturn[3]");
        }
    }

    @Nested
    class ProducedFor {

        @Test
        void addsUpOneOrderAcrossItsShifts() {
            Production.Made made = Production.producedFor(BOOK, "SO-1");
            assertThat(made.entries()).isEqualTo(2);
            assertThat(made.metres()).isEqualTo(216);
            assertThat(made.hours()).isEqualTo(16);
            assertThat(made.stops()).isEqualTo(46);
        }

        @Test
        void isEmptyForAnOrderNothingWasBookedAgainst() {
            assertThat(Production.producedFor(BOOK, "SO-9").metres()).isEqualTo(0);
        }
    }

    @Nested
    class OrderProgress {

        @Test
        void readsTheShareMadeAndWhatIsLeft() {
            Production.Progress progress = Production.orderProgress(BOOK, "SO-1", 400);
            assertThat(progress.remainingMetres()).isEqualTo(184);
            assertThat(progress.share()).isEqualTo(0.54);
            assertThat(progress.complete()).isFalse();
        }

        @Test
        void callsAnOrderCompleteOnceItReachesTheLength() {
            assertThat(Production.orderProgress(BOOK, "SO-1", 216).complete()).isTrue();
        }

        @Test
        void reportsAnOverrunRatherThanAShareAboveOne() {
            Production.Progress progress = Production.orderProgress(BOOK, "SO-1", 200);
            assertThat(progress.share()).isEqualTo(1);
            assertThat(progress.overrunMetres()).isEqualTo(16);
            assertThat(progress.remainingMetres()).isEqualTo(0);
        }

        @Test
        void refusesAnOrderedLengthOfNothing() {
            assertThatThrownBy(() -> Production.orderProgress(BOOK, "SO-1", 0))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class LoomRates {

        @Test
        void ranksTheLoomsOnMetresAnHour() {
            List<JsonObject> rates = Production.loomRates(BOOK);
            assertThat(rates.get(0).get("loom")).isEqualTo(Json.of("W1"));
            assertThat(rates.get(0).get("metresPerHour").asDouble()).isEqualTo(25);
            assertThat(rates.get(1).get("metresPerHour").asDouble()).isEqualTo(13.5);
        }

        @Test
        void reportsTheStopRateAlongside() {
            assertThat(row(Production.loomRates(BOOK), "loom", "N1")
                            .get("stopsPerHour")
                            .asDouble())
                    .isEqualTo(roundThree(46.0 / 16));
        }

        @Test
        void isEmptyForAnEmptyBook() {
            assertThat(Production.loomRates(List.of())).isEmpty();
        }
    }

    @Nested
    class ShiftRates {

        @Test
        void separatesTheDayCrewFromTheNightOne() {
            List<JsonObject> rates = Production.shiftRates(BOOK);
            assertThat(rates).extracting(entry -> entry.get("shift").asText()).containsExactly("day", "night");
            assertThat(rates.get(0).get("metresPerHour").asDouble()).isEqualTo(20);
            assertThat(rates.get(1).get("metresPerHour").asDouble()).isEqualTo(12);
        }
    }

    @Nested
    class AgainstPlan {

        private final Plan plan =
                Schedule.schedule(List.of(N1, W1), List.of(run("SO-1", "poplin", 100), run("SO-2", "sheeting", 250)));

        @Test
        void setsWhatWasMadeBesideWhatWasPlanned() {
            List<Production.AgainstPlan> rows = Production.againstPlan(plan, BOOK, Json.obj("SO-1", 400, "SO-2", 400));
            Production.AgainstPlan first = rows.stream()
                    .filter(entry -> entry.order().equals("SO-1"))
                    .findFirst()
                    .orElseThrow();
            assertThat(first.metres()).isEqualTo(216);
            assertThat(first.plannedHours()).isGreaterThan(0);
            assertThat(first.hoursOver()).isEqualTo(roundThree(16 - first.plannedHours()));
        }

        @Test
        void refusesAPlanEntryWithNoOrderedLengthAgainstIt() {
            assertThatThrownBy(() -> Production.againstPlan(plan, BOOK, Json.obj("SO-1", 400)))
                    .hasMessageContaining("no ordered length for SO-2");
        }
    }

    @Nested
    class AssertBookMatchesPlan {

        private final Plan plan = Schedule.schedule(List.of(N1), List.of(run("SO-1", "poplin", 100)));

        private List<Outturn> book(String order, String loom) {
            return Production.recordBook(List.of(Json.obj("order", order, "loom", loom, "metres", 10, "hours", 1)));
        }

        @Test
        void passesABookThatFollowsThePlan() {
            List<Outturn> book = book("SO-1", "N1");
            assertThat(Production.assertBookMatchesPlan(plan, book)).isSameAs(book);
        }

        @Test
        void catchesAnOrderBookedAgainstTheWrongLoom() {
            assertThatThrownBy(() -> Production.assertBookMatchesPlan(plan, book("SO-1", "W1")))
                    .hasMessageContaining("was planned for N1");
        }

        @Test
        void catchesAnOrderThatWasNeverPlanned() {
            assertThatThrownBy(() -> Production.assertBookMatchesPlan(plan, book("SO-7", "N1")))
                    .isInstanceOf(ConflictException.class);
        }
    }
}
