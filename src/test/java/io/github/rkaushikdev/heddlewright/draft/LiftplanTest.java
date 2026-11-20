package io.github.rkaushikdev.heddlewright.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LiftplanTest {

    private static final List<List<Integer>> TWILL = Tieup.twillTieup(4, 2);

    @Nested
    class LiftplanFromTreadling {

        @Test
        void liftsTheShaftsThePressedTreadleIsTiedTo() {
            Matrix plan = Liftplan.liftplanFromTreadling(TWILL, Treadling.straightTreadling(4), 4, 4);
            assertThat(plan.format()).isEqualTo("XX..\n.XX.\n..XX\nX..X");
        }

        @Test
        void unionsTheShaftsWhenAPickPressesTwoTreadles() {
            Matrix plan = Liftplan.liftplanFromTreadling(Tieup.directTieup(4), List.of(List.of(1, 3)), 4, 4);
            assertThat(plan.format()).isEqualTo("X.X.");
        }

        @Test
        void leavesABlankRowForAPickThatPressesNothing() {
            Matrix plan = Liftplan.liftplanFromTreadling(Tieup.directTieup(4), List.of(List.of(1), List.of()), 4, 4);
            assertThat(plan.format()).isEqualTo("X...\n....");
        }

        @Test
        void refusesATieupThatDoesNotCoverTheTreadles() {
            assertThatThrownBy(() ->
                            Liftplan.liftplanFromTreadling(List.of(List.of(1), List.of(2)), List.of(List.of(1)), 4, 4))
                    .isInstanceOf(CapacityException.class);
        }

        @Test
        void refusesATreadleTheLoomDoesNotHave() {
            assertThatThrownBy(() -> Liftplan.liftplanFromTreadling(Tieup.directTieup(4), List.of(List.of(5)), 4, 4))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class TreadlingFromLiftplan {

        @Test
        void givesEachDistinctLiftItsOwnTreadleInFirstSeenOrder() {
            Liftplan.TreadleDraft result = Liftplan.treadlingFromLiftplan(Matrix.parse("XX..\n..XX\nXX.."));
            assertThat(result.tieup()).isEqualTo(List.of(List.of(1, 2), List.of(3, 4)));
            assertThat(result.treadling()).isEqualTo(List.of(List.of(1), List.of(2), List.of(1)));
            assertThat(result.treadles()).isEqualTo(2);
        }

        @Test
        void roundTripsATreadleDraftBackToTheSameLiftplan() {
            Matrix plan = Liftplan.liftplanFromTreadling(TWILL, Treadling.straightTreadling(4, 8), 4, 4);
            Liftplan.TreadleDraft split = Liftplan.treadlingFromLiftplan(plan);
            Matrix rebuilt = Liftplan.liftplanFromTreadling(split.tieup(), split.treadling(), 4, split.treadles());
            assertThat(rebuilt).isEqualTo(plan);
        }

        @Test
        void refusesToSqueezeMoreLiftsThanTheLoomHasTreadles() {
            Matrix plan = Matrix.parse("X...\n.X..\n..X.\n...X");
            assertThatThrownBy(() -> Liftplan.treadlingFromLiftplan(plan, 3)).isInstanceOf(CapacityException.class);
            assertThat(Liftplan.treadlingFromLiftplan(plan, 4).treadles()).isEqualTo(4);
        }

        @Test
        void keepsABlankPickAsItsOwnTreadleTiedToNothing() {
            Liftplan.TreadleDraft result = Liftplan.treadlingFromLiftplan(Matrix.parse("X...\n...."));
            assertThat(result.tieup()).isEqualTo(List.of(List.of(1), List.of()));
        }
    }

    @Nested
    class LiftplanFromShaftLists {

        @Test
        void setsACellPerLiftedShaft() {
            assertThat(Liftplan.liftplanFromShaftLists(List.of(List.of(1, 3), List.of(2)), 4)
                            .format())
                    .isEqualTo("X.X.\n.X..");
        }

        @Test
        void takesABareNumberForASingleShaft() {
            assertThat(Liftplan.liftplanFromShaftLists(List.of(1, 2), 2).format())
                    .isEqualTo("X.\n.X");
        }

        @Test
        void roundTripsWithShaftListsFromLiftplan() {
            List<List<Integer>> lists = List.of(List.of(1, 3), List.of(2), List.of());
            assertThat(Liftplan.shaftListsFromLiftplan(Liftplan.liftplanFromShaftLists(lists, 4)))
                    .isEqualTo(lists);
        }

        @Test
        void rejectsAShaftOffTheLoom() {
            assertThatThrownBy(() -> Liftplan.liftplanFromShaftLists(List.of(List.of(5)), 4))
                    .hasMessageContaining("shaftLists[0][0]");
        }
    }

    @Nested
    class LiftLoad {

        @Test
        void countsTheShaftsUpOnEachPick() {
            assertThat(Liftplan.liftLoad(Matrix.parse("XX..\nX...\n...."))).containsExactly(2, 1, 0);
        }
    }

    @Nested
    class ShedlessPicks {

        @Test
        void findsThePicksThatLiftNothingAndThePicksThatLiftEverything() {
            assertThat(Liftplan.shedlessPicks(Matrix.parse("....\nXXXX\nXX..")))
                    .containsExactly(
                            new Liftplan.ShedlessPick(1, "nothing lifted"),
                            new Liftplan.ShedlessPick(2, "everything lifted"));
        }

        @Test
        void isEmptyForAPlanThatOpensAShedOnEveryPick() {
            assertThat(Liftplan.shedlessPicks(Matrix.parse("X.\n.X"))).isEmpty();
        }
    }

    @Nested
    class DistinctLifts {

        @Test
        void countsTheDifferentRows() {
            assertThat(Liftplan.distinctLifts(Matrix.parse("XX..\n..XX\nXX.."))).isEqualTo(2);
            assertThat(Liftplan.distinctLifts(Matrix.parse("X.\n.X"))).isEqualTo(2);
        }

        @Test
        void matchesTheTreadleCountTheSplitterReports() {
            Matrix plan = Matrix.parse("XX..\n..XX\nXX..\n.X.X");
            assertThat(Liftplan.distinctLifts(plan))
                    .isEqualTo(Liftplan.treadlingFromLiftplan(plan).treadles());
        }
    }

    @Nested
    class InvertLiftplan {

        @Test
        void swapsEveryCell() {
            assertThat(Liftplan.invertLiftplan(Matrix.parse("XX..\n.X.X")).format())
                    .isEqualTo("..XX\nX.X.");
        }

        @Test
        void isItsOwnInverse() {
            Matrix plan = Matrix.parse("XX..\n.X.X");
            assertThat(Liftplan.invertLiftplan(Liftplan.invertLiftplan(plan))).isEqualTo(plan);
        }
    }
}
