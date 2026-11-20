package io.github.rkaushikdev.heddlewright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.Indexing.Labelled;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IndexingTest {

    @Nested
    class PositionsWhere {

        @Test
        void countsFromOneNotFromZero() {
            assertThat(Indexing.positionsWhere(List.of(5, 0, 7), value -> value > 0))
                    .containsExactly(1, 3);
        }

        @Test
        void passesTheZeroBasedIndexToTheTestAsWell() {
            assertThat(Indexing.positionsWhere(List.of("a", "b", "c"), (value, index) -> index % 2 == 0))
                    .containsExactly(1, 3);
        }

        @Test
        void isEmptyWhenNothingMatchesAndForAnEmptyList() {
            assertThat(Indexing.positionsWhere(List.of(1, 2), value -> false)).isEmpty();
            assertThat(Indexing.positionsWhere(List.of(), value -> true)).isEmpty();
        }

        @Test
        void refusesSomethingThatIsNotAList() {
            assertThatThrownBy(() -> Indexing.positionsWhere(null, value -> true))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class PositionsOf {

        @Test
        void findsEveryPositionHoldingTheValue() {
            assertThat(Indexing.positionsOf(List.of(1, 2, 1, 3), 1)).containsExactly(1, 3);
            assertThat(Indexing.positionsOf(List.of(0, 0), 0)).containsExactly(1, 2);
        }

        @Test
        void comparesStrictly() {
            assertThat(Indexing.positionsOf(List.<Object>of(0, false, "0"), 0)).containsExactly(1);
        }
    }

    @Nested
    class SetPositions {

        @Test
        void readsABitRowAsTheListOfRaisedPositions() {
            assertThat(Indexing.setPositions(List.of(1, 0, 1, 1))).containsExactly(1, 3, 4);
            assertThat(Indexing.setPositions(List.of(0, 0))).isEmpty();
        }
    }

    @Nested
    class DuplicateGroups {

        @Test
        void groupsIdenticalEntriesAndDropsTheSingletons() {
            assertThat(Indexing.duplicateGroups(List.of("a", "b", "a", "c", "b")))
                    .isEqualTo(List.of(List.of(1, 3), List.of(2, 5)));
        }

        @Test
        void keepsFirstSeenOrderBetweenGroups() {
            assertThat(Indexing.duplicateGroups(List.of("z", "a", "z", "a")))
                    .isEqualTo(List.of(List.of(1, 3), List.of(2, 4)));
        }

        @Test
        void comparesByStructureNotByReference() {
            assertThat(Indexing.duplicateGroups(List.of(List.of(1, 2), List.of(1, 2))))
                    .isEqualTo(List.of(List.of(1, 2)));
        }

        @Test
        void takesAKeyFunctionForACheaperComparison() {
            List<List<Integer>> rows = List.of(List.of(1, 0), List.of(1, 0), List.of(0, 1));
            List<List<Integer>> groups = Indexing.duplicateGroups(
                    rows,
                    (List<Integer> row) -> row.stream().map(String::valueOf).collect(Collectors.joining()));
            assertThat(groups).isEqualTo(List.of(List.of(1, 2)));
        }

        @Test
        void isEmptyWhenEveryEntryDiffers() {
            assertThat(Indexing.duplicateGroups(List.of(1, 2, 3))).isEmpty();
        }
    }

    @Nested
    class LabelDistinct {

        @Test
        void numbersEachDistinctEntryFromOneInFirstSeenOrder() {
            Labelled<String> result = Indexing.labelDistinct(List.of("b", "a", "b"));
            assertThat(result.labels()).containsExactly(1, 2, 1);
            assertThat(result.representatives()).containsExactly("b", "a");
            assertThat(result.count()).isEqualTo(2);
        }

        @Test
        void givesEveryPositionALabel() {
            Labelled<Integer> result = Indexing.labelDistinct(List.of(1, 1, 1));
            assertThat(result.labels()).containsExactly(1, 1, 1);
            assertThat(result.count()).isEqualTo(1);
        }

        @Test
        void takesAKeyFunction() {
            List<List<Integer>> rows = List.of(List.of(1, 0), List.of(0, 1), List.of(1, 0));
            Labelled<List<Integer>> result = Indexing.labelDistinct(
                    rows, row -> row.stream().map(String::valueOf).collect(Collectors.joining()));
            assertThat(result.labels()).containsExactly(1, 2, 1);
            assertThat(result.representatives()).isEqualTo(List.of(List.of(1, 0), List.of(0, 1)));
        }

        @Test
        void handlesAnEmptyList() {
            assertThat(Indexing.labelDistinct(List.of())).isEqualTo(new Labelled<>(List.of(), List.of(), 0));
        }
    }
}
