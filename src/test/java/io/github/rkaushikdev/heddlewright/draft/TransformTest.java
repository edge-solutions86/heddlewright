package io.github.rkaushikdev.heddlewright.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.Fixtures;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TransformTest {

    private static final JsonObject TWILL = Fixtures.twill().name("twill").record();

    private static int size(Json array) {
        return ((JsonArray) array).size();
    }

    @Nested
    class Mirroring {

        @Test
        void reversesTheThreading() {
            assertThat(Transform.mirrorThreading(TWILL).get("threading")).isEqualTo(Json.arr(4, 3, 2, 1, 4, 3, 2, 1));
        }

        @Test
        void reversesTheTreadling() {
            assertThat(Transform.mirrorTreadling(TWILL).get("treadling").at(0)).isEqualTo(Json.arr(4));
        }

        @Test
        void isItsOwnInverse() {
            assertThat(Transform.mirrorThreading(Transform.mirrorThreading(TWILL))
                            .get("threading"))
                    .isEqualTo(TWILL.get("threading"));
        }

        @Test
        void leavesTheRestOfTheDraftAlone() {
            assertThat(Transform.mirrorThreading(TWILL).get("tieup")).isEqualTo(TWILL.get("tieup"));
        }
    }

    @Nested
    class Shifting {

        @Test
        void movesTheThreadingAlongAndWraps() {
            assertThat(Transform.shiftThreading(TWILL, 1).get("threading")).isEqualTo(Json.arr(2, 3, 4, 1, 2, 3, 4, 1));
            assertThat(Transform.shiftThreading(TWILL, -1).get("threading").at(0))
                    .isEqualTo(Json.of(4));
        }

        @Test
        void movesTheTreadlingAlong() {
            assertThat(Transform.shiftTreadling(TWILL, 2).get("treadling").at(0))
                    .isEqualTo(Json.arr(3));
        }

        @Test
        void aShiftOfTheWholeLengthChangesNothing() {
            assertThat(Transform.shiftThreading(TWILL, 8).get("threading")).isEqualTo(TWILL.get("threading"));
        }
    }

    @Nested
    class TurnDraft {

        private final JsonObject turned = Transform.turnDraft(TWILL);

        @Test
        void swapsTheShaftsForTheTreadles() {
            assertThat(turned.get("shafts")).isEqualTo(TWILL.get("treadles"));
            assertThat(turned.get("treadles")).isEqualTo(TWILL.get("shafts"));
        }

        @Test
        void makesTheOldTreadlingTheNewThreading() {
            List<Json> firsts = ((JsonArray) TWILL.get("treadling"))
                    .stream().map(pick -> pick.at(0)).toList();
            assertThat(((JsonArray) turned.get("threading")).items()).isEqualTo(firsts);
        }

        @Test
        void transposesTheTieup() {
            assertThat(turned.get("tieup"))
                    .isEqualTo(Json.of(List.of(List.of(1, 4), List.of(1, 2), List.of(2, 3), List.of(3, 4))));
        }

        @Test
        void weavesTheSameClothReadTheOtherWayRound() {
            Matrix original = Drawdown.drawdownFromDraft(Draft.create(TWILL));
            Matrix other = Drawdown.drawdownFromDraft(Draft.create(turned));
            assertThat(other.format()).isEqualTo(original.transpose().format());
        }

        @Test
        void refusesADraftWithADoubledPickWhichHasNowhereToGo() {
            JsonObject doubled = TWILL.with(
                    "treadling",
                    List.of(
                            List.of(1, 2),
                            List.of(3),
                            List.of(4),
                            List.of(1),
                            List.of(2),
                            List.of(3),
                            List.of(4),
                            List.of(1)));
            assertThatThrownBy(() -> Transform.turnDraft(doubled)).isInstanceOf(DraftException.class);
        }
    }

    @Nested
    class FoldBackAndFoldDraft {

        @Test
        void foldsWithoutRepeatingTheTurningThread() {
            assertThat(Transform.foldBack(List.of(1, 2, 3, 4))).containsExactly(1, 2, 3, 4, 3, 2);
        }

        @Test
        void needsAtLeastTwoThreadsToFold() {
            assertThatThrownBy(() -> Transform.foldBack(List.of(1))).isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void foldsTheWarpTheWeftOrBoth() {
            assertThat(Transform.DIRECTIONS).containsExactly("warp", "weft");
            assertThat(size(Transform.foldDraft(TWILL, "warp").get("threading")))
                    .isEqualTo(14);
            assertThat(size(Transform.foldDraft(TWILL, "weft").get("treadling")))
                    .isEqualTo(14);
            JsonObject both = Transform.foldDraft(TWILL, "both");
            assertThat(size(both.get("threading"))).isEqualTo(14);
            assertThat(size(both.get("treadling"))).isEqualTo(14);
        }

        @Test
        void refusesADirectionItDoesNotKnow() {
            assertThatThrownBy(() -> Transform.foldDraft(TWILL, "sideways"))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class ExtendDraft {

        @Test
        void repeatsToTheSizeAskedFor() {
            JsonObject wide = Transform.extendDraft(TWILL, 20, 12);
            assertThat(size(wide.get("threading"))).isEqualTo(20);
            assertThat(size(wide.get("treadling"))).isEqualTo(12);
            assertThat(wide.get("threading").at(19))
                    .isEqualTo(TWILL.get("threading").at(19 % 8));
        }

        @Test
        void givesEachPickItsOwnArray() {
            JsonObject wide = Transform.extendDraft(TWILL, 8, 16);
            JsonArray first = (JsonArray) wide.get("treadling").at(0);
            assertThatThrownBy(() -> first.items().add(Json.of(2))).isInstanceOf(UnsupportedOperationException.class);
            assertThat(wide.get("treadling").at(8)).isEqualTo(Json.arr(1));
        }
    }

    @Nested
    class CompactShafts {

        @Test
        void renumbersASparseThreadingOntoTheShaftsItUses() {
            JsonObject sparse =
                    TWILL.with("shafts", 8, "threading", List.of(1, 4, 6, 4, 1, 6), "tieup", Tieup.twillTieup(8, 2));
            JsonObject tight = Transform.compactShafts(sparse);
            assertThat(tight.get("shafts")).isEqualTo(Json.of(3));
            assertThat(tight.get("threading")).isEqualTo(Json.arr(1, 2, 3, 2, 1, 3));
        }

        @Test
        void carriesTheTieupAcrossDroppingTheShaftsNothingWasOn() {
            JsonObject sparse =
                    TWILL.with("shafts", 8, "threading", List.of(1, 4), "tieup", List.of(List.of(1, 4), List.of(2, 3)));
            assertThat(Transform.compactShafts(sparse).get("tieup"))
                    .isEqualTo(Json.of(List.of(List.of(1, 2), List.of())));
        }

        @Test
        void leavesADraftThatAlreadyUsesEveryShaftAlone() {
            JsonObject tight = Transform.compactShafts(TWILL);
            assertThat(tight.get("shafts")).isEqualTo(Json.of(4));
            assertThat(tight.get("threading")).isEqualTo(TWILL.get("threading"));
        }
    }
}
