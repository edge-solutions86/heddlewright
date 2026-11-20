package io.github.rkaushikdev.heddlewright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GroupTest {

    private static final List<JsonObject> STOPS = List.of(
            Json.obj("cause", "warp", "minutes", 3, "count", 2),
            Json.obj("cause", "weft", "minutes", 1, "count", 5),
            Json.obj("cause", "warp", "minutes", 4, "count", 1));

    private static Group.Tally<JsonObject> byCause() {
        return Group.<JsonObject>by(stop -> stop.get("cause")).label("cause");
    }

    @Nested
    class GroupBy {

        @Test
        void groupsInFirstSeenOrder() {
            Map<Json, List<JsonObject>> groups = Group.groupBy(STOPS, stop -> stop.get("cause"));
            assertThat(groups.keySet()).containsExactly(Json.of("warp"), Json.of("weft"));
            assertThat(groups.get(Json.of("warp"))).hasSize(2);
        }

        @Test
        void passesTheIndexToTheKeyFunction() {
            Map<Integer, List<JsonObject>> groups = Group.groupBy(STOPS, (stop, index) -> index % 2);
            assertThat(groups.get(0)).hasSize(2);
        }

        @Test
        void isEmptyForAnEmptyList() {
            assertThat(Group.groupBy(List.of(), item -> "x")).isEmpty();
        }
    }

    @Nested
    class TallyRows {

        @Test
        void countsTheMembersAndTotalsTheFields() {
            List<JsonObject> rows = Group.tally(STOPS, byCause().field("minutes", stop -> stop.get("minutes")));
            assertThat(rows)
                    .containsExactly(
                            Json.obj("cause", "warp", "count", 2, "minutes", 7),
                            Json.obj("cause", "weft", "count", 1, "minutes", 1));
        }

        @Test
        void overwritesCountWhenAFieldOfThatNameIsGiven() {
            List<JsonObject> rows = Group.tally(STOPS, byCause().field("count", stop -> stop.get("count")));
            JsonObject warp = rows.stream()
                    .filter(row -> row.get("cause").equals(Json.of("warp")))
                    .findFirst()
                    .orElseThrow();
            assertThat(warp.get("count")).isEqualTo(Json.of(3));
        }

        @Test
        void leavesTheRowsInFirstSeenOrderUnlessASortIsAskedFor() {
            Group.Tally<JsonObject> spec = byCause().field("minutes", stop -> stop.get("minutes"));
            List<JsonObject> plain = Group.tally(STOPS, spec);
            List<JsonObject> sorted = Group.tally(STOPS, spec.sortBy("minutes"));
            assertThat(plain.get(0).get("cause")).isEqualTo(Json.of("warp"));
            assertThat(sorted.get(0).get("cause")).isEqualTo(Json.of("warp"));
            assertThat(sorted.get(1).get("cause")).isEqualTo(Json.of("weft"));
        }

        @Test
        void roundsTheTotalsToThePlacesItIsGiven() {
            List<JsonObject> items = List.of(Json.obj("k", "a", "v", 0.1), Json.obj("k", "a", "v", 0.2));
            List<JsonObject> rows = Group.tally(
                    items,
                    Group.<JsonObject>by(item -> item.get("k"))
                            .field("v", item -> item.get("v"))
                            .places(2));
            assertThat(rows.get(0).get("v")).isEqualTo(Json.of(0.3));
        }

        @Test
        void refusesAFieldsObjectThatIsNotOne() throws NoSuchMethodException {
            // fields: 'minutes' cannot be written in Java: a field is a name and a reader, by type.
            assertThat(Group.Tally.class.getMethod("field", String.class, Function.class))
                    .isNotNull();
            assertThatThrownBy(() -> Group.tally(STOPS, byCause().label(" ")))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class TallyOver {

        private final List<String> keys = List.of("warp", "weft", "mechanical");

        @Test
        void keepsAKeyThatScoredNothingInTheOrderTheListGave() {
            List<JsonObject> rows =
                    Group.tallyOver(keys, STOPS, byCause().field("minutes", stop -> stop.get("minutes")));
            assertThat(rows.stream().map(row -> row.get("cause").asText())).containsExactlyElementsOf(keys);
            assertThat(rows.get(2)).isEqualTo(Json.obj("cause", "mechanical", "count", 0, "minutes", 0));
        }

        @Test
        void givesEveryRowTheSameFieldsWhetherOrNotItScored() {
            List<JsonObject> rows =
                    Group.tallyOver(keys, STOPS, byCause().field("minutes", stop -> stop.get("minutes")));
            for (JsonObject row : rows) {
                List<String> names = new ArrayList<>(row.keys());
                names.sort(null);
                assertThat(names).containsExactly("cause", "count", "minutes");
            }
        }

        @Test
        void isAllZerosForAnEmptyList() {
            List<JsonObject> rows = Group.tallyOver(keys, List.of(), byCause());
            assertThat(rows).allMatch(row -> row.get("count").asDouble() == 0);
        }

        @Test
        void needsAtLeastOneKey() {
            assertThatThrownBy(() -> Group.tallyOver(List.of(), STOPS, byCause()))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class SortRows {

        private final List<JsonObject> rows =
                List.of(Json.obj("k", "b", "n", 2), Json.obj("k", "a", "n", 2), Json.obj("k", "c", "n", 5));

        @Test
        void sortsDescendingAndBreaksATieOnTheLabel() {
            assertThat(Group.sortRows(rows, "k", "n").stream()
                            .map(row -> row.get("k").asText()))
                    .containsExactly("c", "a", "b");
        }

        @Test
        void leavesTheRowsAloneWhenNoFieldIsNamed() {
            assertThat(Group.sortRows(rows, "k").stream()
                            .map(row -> row.get("k").asText()))
                    .containsExactly("b", "a", "c");
        }

        @Test
        void doesNotDisturbTheInput() {
            Group.sortRows(rows, "k", "n");
            assertThat(rows.get(0).get("k")).isEqualTo(Json.of("b"));
        }
    }

    @Nested
    class TopOf {

        @Test
        void findsTheRowScoringHighest() {
            List<JsonObject> rows = List.of(Json.obj("k", "a", "n", 1), Json.obj("k", "b", "n", 9));
            assertThat(Group.topOf(rows, "n").orElseThrow().get("k")).isEqualTo(Json.of("b"));
        }

        @Test
        void isNullWhenThereAreNoRows() {
            assertThat(Group.topOf(List.of(), "n")).isEmpty();
        }
    }
}
