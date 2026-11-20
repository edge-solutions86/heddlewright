package io.github.rkaushikdev.heddlewright.weave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.draft.Drawdown;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Runs;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HoneycombTest {

    @Nested
    class HoneycombRepeat {

        @Test
        void isTwiceTheOrderLessTwo() {
            assertThat(Honeycomb.honeycombRepeat(5)).isEqualTo(8);
            assertThat(Honeycomb.honeycombRepeat(3)).isEqualTo(4);
            assertThat(Honeycomb.honeycombRepeat(9)).isEqualTo(16);
        }

        @Test
        void refusesAnOrderTooSmallToMakeACell() {
            assertThatThrownBy(() -> Honeycomb.honeycombRepeat(2)).isInstanceOf(DraftException.class);
            assertThatThrownBy(() -> Honeycomb.honeycombRepeat(0)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class RidgeDistance {

        @Test
        void countsTheShortWayRoundTheRepeat() {
            assertThat(Honeycomb.ridgeDistance(0, 8)).isEqualTo(0);
            assertThat(Honeycomb.ridgeDistance(3, 8)).isEqualTo(3);
            assertThat(Honeycomb.ridgeDistance(4, 8)).isEqualTo(4);
            assertThat(Honeycomb.ridgeDistance(5, 8)).isEqualTo(3);
            assertThat(Honeycomb.ridgeDistance(7, 8)).isEqualTo(1);
        }

        @Test
        void wrapsAnIndexPastTheRepeat() {
            assertThat(Honeycomb.ridgeDistance(9, 8)).isEqualTo(1);
        }

        @Test
        void isSymmetricAboutTheFarSide() {
            for (int i = 0; i < 8; i += 1) {
                assertThat(Honeycomb.ridgeDistance(i, 8)).isEqualTo(Honeycomb.ridgeDistance(8 - i, 8));
            }
        }
    }

    @Nested
    class HoneycombWeave {

        @Test
        void buildsTheNestedCellOfAFiveShaftWaffle() {
            assertThat(Honeycomb.honeycombWeave(5).format())
                    .isEqualTo(String.join(
                            "\n",
                            "XXXX.XXX",
                            "XXX...XX",
                            "XX.....X",
                            "X.......",
                            "........",
                            "X.......",
                            "XX.....X",
                            "XXX...XX"));
        }

        @Test
        void isSquareAndSymmetricBetweenWarpAndWeft() {
            for (int order : List.of(3, 5, 7)) {
                Matrix repeat = Honeycomb.honeycombWeave(order);
                assertThat(Honeycomb.isSquareCell(repeat)).isTrue();
                assertThat(repeat.transpose()).isEqualTo(repeat);
            }
        }

        @Test
        void leavesOnePickWithNoWarpAtAllTheFloorOfTheCell() {
            long bare = Honeycomb.honeycombWeave(5).allRows().stream()
                    .filter(row -> row.stream().allMatch(cell -> cell == 0))
                    .count();
            assertThat(bare).isEqualTo(1);
        }

        @Test
        void ridesALongRidgeFloatDownTheFirstEnd() {
            List<Integer> firstEnd = Honeycomb.honeycombWeave(5).column(0);
            assertThat(Runs.longestRun(firstEnd, 1)).isEqualTo(Honeycomb.ridgeFloat(5));
            assertThat(Honeycomb.ridgeFloat(5)).isEqualTo(7);
        }

        @Test
        void shrinksToASmallCellOnThreeShafts() {
            assertThat(Honeycomb.honeycombWeave(3).format()).isEqualTo("XX.X\nX...\n....\nX...");
        }
    }

    @Nested
    class HoneycombDraft {

        @Test
        void weavesExactlyTheHoneycombTheGeometricRuleDescribes() {
            for (int order : List.of(3, 5, 7)) {
                assertThat(Drawdown.drawdownFromDraft(Honeycomb.honeycombDraft(order)))
                        .isEqualTo(Honeycomb.honeycombWeave(order));
            }
        }

        @Test
        void usesAStaircaseTieupWhoseLastTreadleLiftsNothing() {
            JsonObject draft = Honeycomb.honeycombDraft(5);
            assertThat(draft.get("tieup"))
                    .isEqualTo(Json.of(
                            List.of(List.of(1, 2, 3, 4), List.of(1, 2, 3), List.of(1, 2), List.of(1), List.of())));
            assertThat(draft.get("shafts")).isEqualTo(Json.of(5));
            assertThat(draft.get("treadles")).isEqualTo(Json.of(5));
        }

        @Test
        void threadsAndTreadlesOnTheSamePointDraw() {
            JsonObject draft = Honeycomb.honeycombDraft(5);
            assertThat(draft.get("threading")).isEqualTo(Json.arr(1, 2, 3, 4, 5, 4, 3, 2));
            List<Json> firsts = ((JsonArray) draft.get("treadling"))
                    .stream().map(pick -> pick.at(0)).toList();
            assertThat(firsts).isEqualTo(((JsonArray) draft.get("threading")).items());
        }
    }

    @Nested
    class CellDepth {

        @Test
        void isOneLessThanTheOrder() {
            assertThat(Honeycomb.cellDepth(5)).isEqualTo(4);
            assertThat(Honeycomb.cellDepth(9)).isEqualTo(8);
        }
    }

    @Nested
    class StaggeredHoneycomb {

        @Test
        void laysASecondCellAlongsideOffsetByHalfARepeat() {
            Matrix staggered = Honeycomb.staggeredHoneycomb(5);
            assertThat(staggered.dimensions()).isEqualTo(new Matrix.Dimensions(8, 16));
            Matrix base = Honeycomb.honeycombWeave(5);
            for (int p = 0; p < 8; p += 1) {
                assertThat(staggered.row(p).subList(0, 8)).isEqualTo(base.row(p));
                assertThat(staggered.row(p).subList(8, 16)).isEqualTo(base.row((p + 4) % 8));
            }
        }
    }
}
