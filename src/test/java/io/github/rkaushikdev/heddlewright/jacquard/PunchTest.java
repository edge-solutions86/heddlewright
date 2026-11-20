package io.github.rkaushikdev.heddlewright.jacquard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PunchTest {

    private static final Machine MACHINE = Machine.defineMachine("bench", 2, 4);
    private static final List<Integer> TIE = Harness.straightTie(4, 8);
    private static final Matrix DESIGN = Matrix.parse("XX..XX..\n.XX..XX.");

    @Nested
    class TieConflicts {

        @Test
        void isQuietWhenEveryEndOnAHookAgrees() {
            assertThat(Punch.tieConflicts(DESIGN, TIE, 8)).isEmpty();
        }

        @Test
        void namesThePickTheHookAndBothEndsWhenTheyDisagree() {
            List<Punch.Conflict> conflicts = Punch.tieConflicts(Matrix.parse("X..X.X.."), TIE, 8);
            assertThat(conflicts).isNotEmpty();
            assertThat(conflicts.get(0).pick()).isEqualTo(1);
            assertThat(conflicts.get(0).hook()).isEqualTo(1);
            assertThat(conflicts.get(0).ends()).containsExactly(1, 5);
        }

        @Test
        void reportsOneConflictPerDisagreeingPairNotOnePerEnd() {
            assertThat(Punch.tieConflicts(Matrix.parse("X...X..."), Harness.straightTie(4, 8), 8))
                    .isEmpty();
        }

        @Test
        void refusesATieThatDoesNotCoverTheDesign() {
            assertThatThrownBy(() -> Punch.tieConflicts(DESIGN, Harness.straightTie(4, 6), 8))
                    .hasMessageContaining("does not cover");
        }

        @Test
        void refusesATieNamingAHookTheMachineHasNotGot() {
            assertThatThrownBy(() -> Punch.tieConflicts(DESIGN, Harness.straightTie(4, 8), 3))
                    .isInstanceOf(DraftException.class);
        }
    }

    @Nested
    class HookPlanFromDesign {

        @Test
        void collapsesTheDesignOntoTheHooks() {
            assertThat(Punch.hookPlanFromDesign(DESIGN, TIE, 8).format()).isEqualTo("XX......\n.XX.....");
        }

        @Test
        void leavesTheHooksPastTheRepeatUnlifted() {
            Matrix plan = Punch.hookPlanFromDesign(DESIGN, TIE, 8);
            assertThat(plan.row(0).subList(4, 8)).containsExactly(0, 0, 0, 0);
        }

        @Test
        void refusesADesignTheTieCannotWeaveAndSaysHowManyClashes() {
            DraftException err = catchThrowableOfType(
                    DraftException.class, () -> Punch.hookPlanFromDesign(Matrix.parse("X..X.X.."), TIE, 8));
            assertThat(err).isNotNull();
            assertThat(err.detail().get("conflicts").asDouble()).isGreaterThan(0);
            assertThat(err.detail().get("first").get("pick")).isEqualTo(Json.of(1));
        }
    }

    @Nested
    class DesignFromHookPlan {

        @Test
        void weavesTheDesignBackOutThroughTheSameTie() {
            Matrix plan = Punch.hookPlanFromDesign(DESIGN, TIE, 8);
            assertThat(Punch.designFromHookPlan(plan, TIE)).isEqualTo(DESIGN);
        }

        @Test
        void roundTripsAPointTieWhichMirrorsTheFigureAcrossTheCloth() {
            List<Integer> tie = Harness.pointTie(4, 12);
            Matrix plan = Matrix.parse("XX..\n.XX.\n..XX");
            Matrix design = Punch.designFromHookPlan(plan, tie);
            assertThat(design.format('o', '.')).isEqualTo("oo...ooo...o\n.oo.oo.oo.oo\n..ooo...ooo.");
            assertThat(Punch.tieConflicts(design, tie, 4)).isEmpty();
            assertThat(Punch.hookPlanFromDesign(design, tie, 4)).isEqualTo(plan);
        }

        @Test
        void refusesATieReachingPastThePlan() {
            assertThatThrownBy(() -> Punch.designFromHookPlan(Matrix.parse("X.\n.X"), List.of(1, 3)))
                    .isInstanceOf(DraftException.class);
        }
    }

    @Nested
    class PunchJob {

        @Test
        void reportsTheShapeOfTheJobAndCutsTheCards() {
            Punch.Job job = Punch.punchJob(MACHINE, DESIGN, TIE);
            assertThat(job.machine()).isEqualTo("bench");
            assertThat(job.hooks()).isEqualTo(8);
            assertThat(job.picks()).isEqualTo(2);
            assertThat(job.ends()).isEqualTo(8);
            assertThat(job.hooksUsed()).isEqualTo(3);
            assertThat(job.deck()).hasSize(2);
        }

        @Test
        void cutsCardsThatLiftExactlyTheHooksThePlanNames() {
            Punch.Job job = Punch.punchJob(MACHINE, DESIGN, TIE);
            assertThat(Deck.deckHooks(job.deck(), MACHINE)).isEqualTo(List.of(List.of(1, 2), List.of(2, 3)));
        }
    }

    @Nested
    class UsedHooks {

        @Test
        void listsTheHooksAPlanEverLiftsSorted() {
            assertThat(Punch.usedHooks(Matrix.parse("X..X\n.X.."))).containsExactly(1, 2, 4);
        }

        @Test
        void isEmptyForAPlanThatLiftsNothing() {
            assertThat(Punch.usedHooks(Matrix.parse("..\n.."))).isEmpty();
        }
    }

    @Nested
    class RepeatWidthOfTie {

        @Test
        void findsThePassWidthOfAStraightTie() {
            assertThat(Punch.repeatWidthOfTie(Harness.straightTie(4, 12))).isEqualTo(4);
            assertThat(Punch.repeatWidthOfTie(Harness.straightTie(6, 12))).isEqualTo(6);
        }

        @Test
        void findsTheWiderPassOfAPointTie() {
            assertThat(Punch.repeatWidthOfTie(Harness.pointTie(4, 12))).isEqualTo(6);
        }

        @Test
        void isTheWholeTieWhenNothingRepeats() {
            assertThat(Punch.repeatWidthOfTie(List.of(1, 2, 3, 2, 1))).isEqualTo(5);
        }
    }

    @Nested
    class DesignRepeat {

        @Test
        void cutsTheDesignToOnePassOfTheTie() {
            assertThat(Punch.designRepeat(DESIGN, TIE).format()).isEqualTo("XX..\n.XX.");
        }

        @Test
        void leavesADesignAlreadyOneRepeatWideAlone() {
            Matrix narrow = Matrix.parse("XX..\n.XX.");
            assertThat(Punch.designRepeat(narrow, Harness.straightTie(4, 4)).format())
                    .isEqualTo(narrow.format());
        }
    }
}
