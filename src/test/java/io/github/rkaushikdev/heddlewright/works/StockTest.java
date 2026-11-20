package io.github.rkaushikdev.heddlewright.works;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.ConflictException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import io.github.rkaushikdev.heddlewright.works.Stock.Line;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StockTest {

    private static final Line LINE = Stock.createLine("C30", "30 tex cotton", 30, 100);

    @Nested
    class CreateLine {

        @Test
        void opensALineWithNothingPromised() {
            assertThat(Stock.reserved(LINE)).isEqualTo(0);
            assertThat(Stock.available(LINE)).isEqualTo(100);
            // A record has no setters and its promises are an unmodifiable list.
            assertThatThrownBy(() -> LINE.reservations().add(new Stock.Reservation("SO-1", 1)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void takesALineWithNothingOnTheShelf() {
            assertThat(Stock.available(Stock.createLine("C40", 25, 0))).isEqualTo(0);
        }

        @Test
        void refusesANegativeStockOrACountOfNothing() {
            assertThatThrownBy(() -> Stock.createLine("C40", 25, -1)).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Stock.createLine("C40", 0, 1)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class Reserve {

        @Test
        void promisesStockAndTakesItOutOfWhatIsAvailable() {
            Line promised = Stock.reserve(LINE, "SO-1", 40);
            assertThat(Stock.reserved(promised)).isEqualTo(40);
            assertThat(Stock.available(promised)).isEqualTo(60);
            assertThat(promised.kilograms()).isEqualTo(100);
        }

        @Test
        void willNotPromiseTheSameYarnTwice() {
            Line promised = Stock.reserve(LINE, "SO-1", 80);
            assertThatThrownBy(() -> Stock.reserve(promised, "SO-2", 40)).isInstanceOf(ConflictException.class);
        }

        @Test
        void willNotPromiseAnOrderTwiceOnTheSameLine() {
            Line promised = Stock.reserve(LINE, "SO-1", 10);
            assertThatThrownBy(() -> Stock.reserve(promised, "SO-1", 10)).hasMessageContaining("already has stock");
        }

        @Test
        void leavesTheLineItWasGivenAlone() {
            Stock.reserve(LINE, "SO-1", 40);
            assertThat(Stock.reserved(LINE)).isEqualTo(0);
        }
    }

    @Nested
    class Release {

        @Test
        void givesAPromiseBack() {
            Line promised = Stock.reserve(LINE, "SO-1", 40);
            assertThat(Stock.available(Stock.release(promised, "SO-1"))).isEqualTo(100);
        }

        @Test
        void refusesToReleaseWhatWasNeverPromised() {
            assertThatThrownBy(() -> Stock.release(LINE, "SO-9")).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Issue {

        private final Line promised = Stock.reserve(LINE, "SO-1", 40);

        @Test
        void takesStockOffTheShelfAndOffThePromiseTogether() {
            Line issued = Stock.issue(promised, "SO-1", 40);
            assertThat(issued.kilograms()).isEqualTo(60);
            assertThat(Stock.reserved(issued)).isEqualTo(0);
            assertThat(Stock.available(issued)).isEqualTo(60);
        }

        @Test
        void leavesTheRestOfAPartIssuePromised() {
            Line issued = Stock.issue(promised, "SO-1", 15);
            assertThat(issued.kilograms()).isEqualTo(85);
            assertThat(Stock.reserved(issued)).isEqualTo(25);
        }

        @Test
        void refusesMoreThanTheOrderWasPromised() {
            assertThatThrownBy(() -> Stock.issue(promised, "SO-1", 60)).hasMessageContaining("more than the order");
        }

        @Test
        void refusesAnIssueAgainstNoPromise() {
            assertThatThrownBy(() -> Stock.issue(LINE, "SO-9", 1)).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Receive {

        @Test
        void putsStockInWithoutTouchingThePromises() {
            Line delivered = Stock.receive(Stock.reserve(LINE, "SO-1", 40), 50);
            assertThat(delivered.kilograms()).isEqualTo(150);
            assertThat(Stock.available(delivered)).isEqualTo(110);
        }
    }

    @Nested
    class LineByCodeAndFullyPromised {

        @Test
        void findsALineAndRefusesOneThatIsNotThere() {
            assertThat(Stock.lineByCode(List.of(LINE), "C30")).isSameAs(LINE);
            assertThatThrownBy(() -> Stock.lineByCode(List.of(LINE), "C99")).isInstanceOf(NotFoundException.class);
        }

        @Test
        void namesTheLinesWithNothingLeftToPromise() {
            Line promised = Stock.reserve(LINE, "SO-1", 100);
            assertThat(Stock.fullyPromised(List.of(LINE, promised))).containsExactly("C30");
        }
    }

    @Nested
    class ByOrder {

        @Test
        void addsUpWhatEachOrderHasPromisedAcrossTheStore() {
            Line first = Stock.reserve(LINE, "SO-1", 40);
            Line other = Stock.reserve(Stock.createLine("C40", 25, 50), "SO-1", 20);
            assertThat(Stock.byOrder(List.of(first, other)))
                    .containsExactly(Json.obj("order", "SO-1", "count", 2, "kilograms", 60));
        }

        @Test
        void isEmptyForAStoreWithNothingPromised() {
            assertThat(Stock.byOrder(List.of(LINE))).isEmpty();
        }
    }

    @Nested
    class AssertStoreSound {

        @Test
        void passesAStoreWhosePromisesFit() {
            Line promised = Stock.reserve(LINE, "SO-1", 40);
            assertThat(Stock.assertStoreSound(List.of(promised))).containsExactly(promised);
        }

        @Test
        void catchesALineThatHasPromisedMoreThanItHolds() {
            Line shrunk = Stock.reserve(LINE, "SO-1", 80).withKilograms(50);
            assertThatThrownBy(() -> Stock.assertStoreSound(List.of(shrunk)))
                    .hasMessageContaining("promised more than they hold");
        }
    }
}
