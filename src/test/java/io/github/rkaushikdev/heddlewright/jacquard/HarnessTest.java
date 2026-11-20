package io.github.rkaushikdev.heddlewright.jacquard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.heddlewright.jacquard.Harness.TieType;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HarnessTest {

    @Nested
    class StraightTie {

        @Test
        void runsThroughAndStartsAgain() {
            assertThat(Harness.straightTie(4, 10)).containsExactly(1, 2, 3, 4, 1, 2, 3, 4, 1, 2);
        }

        @Test
        void givesEveryHookTheSameLoadWhenTheEndsDivide() {
            assertThat(Harness.hookLoad(Harness.straightTie(4, 16), 4)).containsExactly(4, 4, 4, 4);
        }
    }

    @Nested
    class PointTie {

        @Test
        void mirrorsTheFigureWithoutRepeatingTheTurn() {
            assertThat(Harness.pointTie(4, 12)).containsExactly(1, 2, 3, 4, 3, 2, 1, 2, 3, 4, 3, 2);
        }

        @Test
        void coversTwiceTheWidthLessTwoEndsPerRepeat() {
            assertThat(Harness.repeatWidth(TieType.POINT, 4)).isEqualTo(6);
            assertThat(Harness.repeatWidth(TieType.STRAIGHT, 4)).isEqualTo(4);
        }

        @Test
        void needsAtLeastTwoHooks() {
            assertThatThrownBy(() -> Harness.pointTie(1, 4)).isInstanceOf(DraftException.class);
        }

        @Test
        void loadsTheTurningHooksHalfAsOftenAsTheRest() {
            List<Integer> load = Harness.hookLoad(Harness.pointTie(4, 12), 4);
            assertThat(load.get(0)).isEqualTo(2);
            assertThat(load.get(3)).isEqualTo(2);
            assertThat(load.get(1)).isEqualTo(4);
        }
    }

    @Nested
    class CentreTie {

        @Test
        void runsOutFromTheMiddleAndMirrorsBack() {
            assertThat(Harness.centreTie(4, 8)).containsExactly(4, 3, 2, 1, 1, 2, 3, 4);
        }

        @Test
        void isSymmetricAboutTheCentreOfTheCloth() {
            List<Integer> tie = Harness.centreTie(5, 20);
            for (int i = 0; i < 10; i += 1) {
                assertThat(tie.get(i)).isEqualTo(tie.get(19 - i));
            }
        }
    }

    @Nested
    class BorderedTie {

        @Test
        void givesTheBorderItsOwnHooksAndMirrorsItOnTheFarSide() {
            assertThat(Harness.borderedTie(4, 2, 4, 2)).containsExactly(1, 2, 3, 4, 5, 6, 2, 1);
        }

        @Test
        void neverLetsABodyEndReachABorderHook() {
            List<Integer> tie = Harness.borderedTie(3, 2, 9, 4);
            for (int hook : tie.subList(4, 13)) {
                assertThat(hook).isGreaterThan(2);
            }
        }
    }

    @Nested
    class TieByType {

        @Test
        void namesItsFourTiesAndRoutesToEach() {
            assertThat(Harness.TIE_TYPES)
                    .extracting(Object::toString)
                    .containsExactly("straight", "point", "centre", "bordered");
            assertThat(Harness.tieByType("straight", Json.obj("hooks", 4, "totalEnds", 8)))
                    .isEqualTo(Harness.straightTie(4, 8));
            assertThat(Harness.tieByType("point", Json.obj("hooks", 4, "totalEnds", 8)))
                    .isEqualTo(Harness.pointTie(4, 8));
            assertThat(Harness.tieByType("centre", Json.obj("hooks", 4, "totalEnds", 8)))
                    .isEqualTo(Harness.centreTie(4, 8));
        }

        @Test
        void refusesATieItDoesNotKnow() {
            assertThatThrownBy(() -> Harness.tieByType("spiral", Json.obj("hooks", 4, "totalEnds", 8)))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class HookLoad {

        @Test
        void countsTheCordsHangingOnEachHook() {
            assertThat(Harness.hookLoad(List.of(1, 1, 2), 3)).containsExactly(2, 1, 0);
        }

        @Test
        void refusesATieNamingAHookTheMachineHasNotGotAndSaysWhichEnd() {
            CapacityException err =
                    catchThrowableOfType(CapacityException.class, () -> Harness.hookLoad(List.of(1, 9), 4));
            assertThat(err).isNotNull();
            assertThat(err.detail()).isEqualTo(Json.obj("end", 2, "hook", 9, "hooks", 4));
        }
    }

    @Nested
    class IdleHooksAndEndsOnHook {

        @Test
        void findsHooksNothingIsTiedTo() {
            assertThat(Harness.idleHooks(List.of(1, 1, 3), 4)).containsExactly(2, 4);
            assertThat(Harness.idleHooks(Harness.straightTie(4, 8), 4)).isEmpty();
        }

        @Test
        void listsTheEndsAHookPulls() {
            assertThat(Harness.endsOnHook(Harness.straightTie(4, 10), 2)).containsExactly(2, 6, 10);
        }
    }

    @Nested
    class ComberBoardPosition {

        @Test
        void fillsTheRowsBeforeMovingAlongTheBoard() {
            assertThat(Harness.comberBoardPosition(1, 4, 3)).isEqualTo(new Harness.BoardHole(1, 1));
            assertThat(Harness.comberBoardPosition(4, 4, 3)).isEqualTo(new Harness.BoardHole(4, 1));
            assertThat(Harness.comberBoardPosition(5, 4, 3)).isEqualTo(new Harness.BoardHole(1, 2));
            assertThat(Harness.comberBoardPosition(12, 4, 3)).isEqualTo(new Harness.BoardHole(4, 3));
        }

        @Test
        void givesEveryEndADifferentHole() {
            Set<Harness.BoardHole> seen = new HashSet<>();
            for (int end = 1; end <= 12; end += 1) {
                seen.add(Harness.comberBoardPosition(end, 4, 3));
            }
            assertThat(seen).hasSize(12);
        }

        @Test
        void refusesAnEndTheBoardHasNoHoleFor() {
            assertThatThrownBy(() -> Harness.comberBoardPosition(13, 4, 3)).isInstanceOf(CapacityException.class);
        }
    }

    @Nested
    class ComberBoardSize {

        @Test
        void worksTheHolesPerRowOutFromTheWarpWidth() {
            assertThat(Harness.comberBoardSize(1600)).isEqualTo(new Harness.BoardSize(8, 200, 1600));
        }

        @Test
        void roundsARowUpRatherThanLosingAnEnd() {
            assertThat(Harness.comberBoardSize(1601).holesPerRow()).isEqualTo(201);
        }

        @Test
        void takesAShallowerBoard() {
            assertThat(Harness.comberBoardSize(1600, 4).holesPerRow()).isEqualTo(400);
        }
    }
}
