package io.github.rkaushikdev.heddlewright.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ThreadingTest {

    @Nested
    class AssertThreading {

        @Test
        void acceptsShaftsInsideTheCount() {
            assertThat(Threading.assertThreading(List.of(1, 2, 3, 4), 4)).containsExactly(1, 2, 3, 4);
        }

        @Test
        void rejectsAShaftAboveTheLoomAndOneBelowOne() {
            assertThatThrownBy(() -> Threading.assertThreading(List.of(1, 5), 4))
                    .hasMessageContaining("threading[1]");
            assertThatThrownBy(() -> Threading.assertThreading(List.of(0, 1), 4))
                    .isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void rejectsAnEmptyThreading() {
            assertThatThrownBy(() -> Threading.assertThreading(List.of(), 4))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class StraightDraw {

        @Test
        void countsUpTheShaftsAndStartsAgain() {
            assertThat(Threading.straightDraw(4)).containsExactly(1, 2, 3, 4);
            assertThat(Threading.straightDraw(4, 10)).containsExactly(1, 2, 3, 4, 1, 2, 3, 4, 1, 2);
        }

        @Test
        void startsOnADifferentShaftWhenAsked() {
            assertThat(Threading.straightDraw(4, 4, 3, 1)).containsExactly(3, 4, 1, 2);
        }

        @Test
        void runsTheOtherWayOnAReversedDirection() {
            assertThat(Threading.straightDraw(4, 4, 1, -1)).containsExactly(1, 4, 3, 2);
        }

        @Test
        void isASingleShaftOnAOneShaftLoom() {
            assertThat(Threading.straightDraw(1, 3)).containsExactly(1, 1, 1);
        }

        @Test
        void refusesAStartOffTheLoom() {
            assertThatThrownBy(() -> Threading.straightDraw(4, 4, 5, 1)).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Threading.straightDraw(4, 4, 1, 0)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class PointDraw {

        @Test
        void runsUpAndBackWithoutRepeatingTheTurn() {
            assertThat(Threading.pointDraw(4)).containsExactly(1, 2, 3, 4, 3, 2);
            assertThat(Threading.pointDraw(5)).containsExactly(1, 2, 3, 4, 5, 4, 3, 2);
        }

        @Test
        void repeatsItsCycleOverALongerThreading() {
            assertThat(Threading.pointDraw(4, 12)).containsExactly(1, 2, 3, 4, 3, 2, 1, 2, 3, 4, 3, 2);
        }

        @Test
        void cutsShortWhenFewerEndsAreAskedFor() {
            assertThat(Threading.pointDraw(4, 3)).containsExactly(1, 2, 3);
        }

        @Test
        void needsTwoShaftsToHaveAPointAtAll() {
            assertThatThrownBy(() -> Threading.pointDraw(1)).isInstanceOf(DraftException.class);
        }
    }

    @Nested
    class BrokenDraw {

        @Test
        void runsABlockThenStepsTheStartAcross() {
            assertThat(Threading.brokenDraw(4, 8, 4, 2)).containsExactly(1, 2, 3, 4, 3, 4, 1, 2);
        }

        @Test
        void aStaggerOfTheBlockSizeIsJustAStraightDraw() {
            assertThat(Threading.brokenDraw(4, 8, 4, 4)).isEqualTo(Threading.straightDraw(4, 8));
        }

        @Test
        void takesABlockShorterThanTheShaftCount() {
            assertThat(Threading.brokenDraw(6, 6, 2, 3)).containsExactly(1, 2, 4, 5, 1, 2);
        }

        @Test
        void staysInsideTheLoomWhateverTheStagger() {
            for (int shaft : Threading.brokenDraw(8, 40, 3, 5)) {
                assertThat(shaft).isBetween(1, 8);
            }
        }
    }

    @Nested
    class UndulatingDraw {

        @Test
        void holdsEachShaftForTheNextRunLengthInTheList() {
            assertThat(Threading.undulatingDraw(4, 9, List.of(1, 2))).containsExactly(1, 2, 2, 3, 4, 4, 1, 2, 2);
        }

        @Test
        void aRunListOfAllOnesIsAStraightDraw() {
            assertThat(Threading.undulatingDraw(4, 8, List.of(1))).isEqualTo(Threading.straightDraw(4, 8));
        }

        @Test
        void stopsExactlyOnTheEndCountEvenMidRun() {
            assertThat(Threading.undulatingDraw(3, 4, List.of(3))).containsExactly(1, 1, 1, 2);
        }

        @Test
        void refusesARunLengthOfZeroWhichWouldNeverAdvance() {
            assertThatThrownBy(() -> Threading.undulatingDraw(4, 4, List.of(0)))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class DividedDraw {

        @Test
        void alternatesTheTwoGroupsAdvancingEachOnItsOwnTurn() {
            assertThat(Threading.dividedDraw(List.of(1, 2), List.of(3, 4, 5), 10))
                    .containsExactly(1, 3, 2, 4, 1, 5, 2, 3, 1, 4);
        }

        @Test
        void worksWithASingleGroundShaft() {
            assertThat(Threading.dividedDraw(List.of(1), List.of(3, 4), 6)).containsExactly(1, 3, 1, 4, 1, 3);
        }

        @Test
        void needsBothGroups() {
            assertThatThrownBy(() -> Threading.dividedDraw(List.of(), List.of(1), 4))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class DrawByStyle {

        @Test
        void namesEveryStyleItCanBuild() {
            assertThat(Threading.DRAW_STYLES).containsExactly("straight", "point", "broken", "undulating", "divided");
        }

        @Test
        void routesToTheMatchingBuilderWithTheSameDefaults() {
            assertThat(Threading.drawByStyle("straight", Json.obj("shafts", 4))).isEqualTo(Threading.straightDraw(4));
            assertThat(Threading.drawByStyle("point", Json.obj("shafts", 4))).isEqualTo(Threading.pointDraw(4));
            assertThat(Threading.drawByStyle("broken", Json.obj("shafts", 4, "ends", 8)))
                    .isEqualTo(Threading.brokenDraw(4, 8));
            assertThat(Threading.drawByStyle("undulating", Json.obj("shafts", 4, "ends", 6, "runLengths", List.of(2))))
                    .isEqualTo(Threading.undulatingDraw(4, 6, List.of(2)));
            assertThat(Threading.drawByStyle(
                            "divided",
                            Json.obj(
                                    "shafts",
                                    4,
                                    "ends",
                                    6,
                                    "groundShafts",
                                    List.of(1, 2),
                                    "patternShafts",
                                    List.of(3, 4))))
                    .isEqualTo(Threading.dividedDraw(List.of(1, 2), List.of(3, 4), 6));
        }

        @Test
        void rejectsAStyleItDoesNotKnow() {
            assertThatThrownBy(() -> Threading.drawByStyle("spiral", Json.obj("shafts", 4)))
                    .hasMessageContaining("straight, point");
        }
    }

    @Nested
    class ShaftUsage {

        private final List<Integer> threading = List.of(1, 2, 1, 2, 4, 4);

        @Test
        void countsTheEndsCarriedByEachShaft() {
            assertThat(Threading.shaftLoad(threading, 4)).containsExactly(2, 2, 0, 2);
        }

        @Test
        void findsTheShaftsWithNothingOnThem() {
            assertThat(Threading.idleShafts(threading, 4)).containsExactly(3);
            assertThat(Threading.idleShafts(List.of(1, 2, 3, 4), 4)).isEmpty();
            assertThat(Threading.idleShafts(List.of(1, 1), 3)).containsExactly(2, 3);
        }

        @Test
        void listsTheEndsOnAShaftAs1BasedPositions() {
            assertThat(Threading.endsOnShaft(threading, 1)).containsExactly(1, 3);
            assertThat(Threading.endsOnShaft(threading, 4)).containsExactly(5, 6);
            assertThat(Threading.endsOnShaft(threading, 3)).isEmpty();
        }

        @Test
        void countsTheDistinctShaftsAThreadingActuallyNeeds() {
            assertThat(Threading.shaftsUsed(threading)).isEqualTo(3);
            assertThat(Threading.shaftsUsed(List.of(1, 1, 1))).isEqualTo(1);
        }

        @Test
        void aStraightDrawLoadsEveryShaftEvenlyWhenTheEndsDivide() {
            assertThat(Threading.shaftLoad(Threading.straightDraw(4, 16), 4)).containsExactly(4, 4, 4, 4);
        }
    }
}
