package io.github.rkaushikdev.heddlewright.loom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.heddlewright.draft.Drawdown;
import io.github.rkaushikdev.heddlewright.loom.Dobby.Action;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.weave.Twill;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DobbyTest {

    private static Matrix chainOf(String notation) {
        return Dobby.chainFromLiftplan(Twill.twillWeave(notation));
    }

    @Nested
    class LagFromShafts {

        @Test
        void pegsAHoleForEachLiftedShaft() {
            assertThat(Dobby.lagFromShafts(6, List.of(1, 4))).containsExactly(1, 0, 0, 1, 0, 0);
        }

        @Test
        void leavesALagBareWhenNothingLifts() {
            assertThat(Dobby.lagFromShafts(4, List.of())).containsExactly(0, 0, 0, 0);
        }

        @Test
        void refusesAShaftTheDobbyHasNoHoleFor() {
            assertThatThrownBy(() -> Dobby.lagFromShafts(4, List.of(5))).isInstanceOf(CapacityException.class);
            assertThatThrownBy(() -> Dobby.lagFromShafts(4, List.of(0))).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class ShaftsOnLag {

        @Test
        void readsThePegsBackOnAPositiveDobby() {
            assertThat(Dobby.shaftsOnLag(List.of(1, 0, 0, 1))).containsExactly(1, 4);
        }

        @Test
        void readsTheOtherShaftsOnANegativeOne() {
            assertThat(Dobby.shaftsOnLag(List.of(1, 0, 0, 1), Action.NEGATIVE)).containsExactly(2, 3);
        }

        @Test
        void namesTheTwoActions() {
            assertThat(Action.DOBBY_ACTIONS).extracting(Object::toString).containsExactly("positive", "negative");
            assertThatThrownBy(() -> Dobby.shaftsOnLag(List.of(1), Action.parse("half")))
                    .isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void roundTripsThroughLagFromShafts() {
            assertThat(Dobby.shaftsOnLag(Dobby.lagFromShafts(8, List.of(2, 5, 7))))
                    .containsExactly(2, 5, 7);
        }
    }

    @Nested
    class ChainFromLiftplan {

        @Test
        void laysTheLiftplanOutAsLags() {
            assertThat(Dobby.renderChain(chainOf("2/2"))).isEqualTo("oo..\n.oo.\n..oo\no..o");
        }

        @Test
        void padsToAWiderDobbyWithoutMovingThePegs() {
            Matrix chain = Dobby.chainFromLiftplan(Matrix.parse("X.\n.X"), 4);
            assertThat(Dobby.renderChain(chain)).isEqualTo("o...\n.o..");
        }

        @Test
        void refusesAPlanWiderThanTheDobby() {
            assertThatThrownBy(() -> Dobby.chainFromLiftplan(Matrix.parse("oooo", 'o', '.'), 2))
                    .isInstanceOf(CapacityException.class);
        }
    }

    @Nested
    class LiftplanFromChain {

        @Test
        void isTheChainItselfOnAPositiveDobby() {
            Matrix chain = chainOf("2/2");
            assertThat(Dobby.liftplanFromChain(chain)).isEqualTo(chain);
        }

        @Test
        void isTheCellByCellReverseOnANegativeOne() {
            Matrix chain = chainOf("3/1");
            Matrix negative = Dobby.liftplanFromChain(chain, Action.NEGATIVE);
            assertThat(negative.format()).isEqualTo("...X\nX...\n.X..\n..X.");
            assertThat(Drawdown.warpFaceRatio(negative)).isEqualTo(1 - Drawdown.warpFaceRatio(chain));
        }

        @Test
        void liftsThreeShaftsWhereThePositiveReadingLiftsOne() {
            Matrix chain = chainOf("1/3");
            assertThat(Dobby.shaftsOnLag(chain.row(0))).hasSize(1);
            assertThat(Dobby.shaftsOnLag(chain.row(0), Action.NEGATIVE)).hasSize(3);
        }

        @Test
        void handsBackACopySoTheChainIsNotEditedThroughIt() {
            // A Matrix cannot be written to at all, so the nearest edit is to the grid it hands out.
            Matrix chain = Dobby.chainFromLiftplan(Matrix.parse("X.\n.X"));
            int[][] grid = Dobby.liftplanFromChain(chain).toArray();
            grid[0][0] = 0;
            assertThat(chain.get(0, 0)).isEqualTo(1);
        }
    }

    @Nested
    class PegCountAndChainLength {

        @Test
        void countThePegsAndTheLags() {
            Matrix chain = chainOf("2/2");
            assertThat(Dobby.pegCount(chain)).isEqualTo(8);
            assertThat(Dobby.chainLength(chain)).isEqualTo(4);
        }

        @Test
        void aThreeOneTwillNeedsHalfAsManyPegsAgainAsATwoTwo() {
            assertThat(Dobby.pegCount(chainOf("3/1"))).isEqualTo(12);
        }
    }

    @Nested
    class ChainRepeat {

        @Test
        void findsTheShortestChainThatWeavesTheSameCloth() {
            Matrix chain = Dobby.chainFromLiftplan(Matrix.parse("X.\n.X\nX.\n.X"));
            assertThat(Dobby.chainRepeat(chain)).isEqualTo(2);
        }

        @Test
        void isTheWholeChainWhenNothingRepeats() {
            assertThat(Dobby.chainRepeat(chainOf("2/2"))).isEqualTo(4);
        }
    }

    @Nested
    class SameChain {

        @Test
        void isTrueWhereverTheBarrelWasStarted() {
            Matrix chain = chainOf("2/2");
            List<List<Integer>> lags = chain.allRows();
            List<List<Integer>> moved = new ArrayList<>(lags.subList(2, lags.size()));
            moved.addAll(lags.subList(0, 2));
            assertThat(Dobby.sameChain(chain, Matrix.of(moved))).isTrue();
        }

        @Test
        void isFalseOnceAPegMoves() {
            Matrix chain = chainOf("2/2");
            List<List<Integer>> changed = new ArrayList<>(chain.allRows());
            changed.set(0, List.of(0, 0, 1, 1));
            assertThat(Dobby.sameChain(chain, Matrix.of(changed))).isFalse();
        }
    }

    @Nested
    class RepeatedLags {

        @Test
        void groupsLagsPeggedTheSameWay() {
            Matrix chain = Dobby.chainFromLiftplan(Matrix.parse("X.\n.X\nX."));
            assertThat(Dobby.repeatedLags(chain)).isEqualTo(List.of(List.of(1, 3)));
        }

        @Test
        void isEmptyWhenEveryLagDiffers() {
            assertThat(Dobby.repeatedLags(chainOf("2/2"))).isEmpty();
        }
    }

    @Nested
    class AssertChainFitsBarrel {

        @Test
        void passesAChainTheBarrelHolds() {
            Matrix chain = chainOf("2/2");
            assertThat(Dobby.assertChainFitsBarrel(chain, 40)).isSameAs(chain);
        }

        @Test
        void saysHowManyLagsAreOver() {
            CapacityException err =
                    catchThrowableOfType(CapacityException.class, () -> Dobby.assertChainFitsBarrel(chainOf("2/2"), 3));
            assertThat(err).isNotNull();
            assertThat(err.detail().get("over")).isEqualTo(Json.of(1));
        }
    }

    @Nested
    class ShedlessLags {

        @Test
        void findsABareLagOnAPositiveDobby() {
            Matrix chain = Dobby.chainFromLiftplan(Matrix.parse("X.\n.."));
            assertThat(Dobby.shedlessLags(chain)).containsExactly(new Dobby.ShedlessLag(2, "nothing lifted"));
        }

        @Test
        void swapsTheTwoCasesOnANegativeDobby() {
            Matrix chain = Dobby.chainFromLiftplan(Matrix.parse("X.\n.."));
            assertThat(Dobby.shedlessLags(chain, Action.NEGATIVE))
                    .containsExactly(new Dobby.ShedlessLag(2, "everything lifted"));
        }

        @Test
        void isQuietOnAChainThatOpensAShedEveryPick() {
            assertThat(Dobby.shedlessLags(chainOf("2/2"))).isEmpty();
        }
    }

    @Nested
    class AssertChainWeaves {

        @Test
        void passesAChainThatOpensAShedSomewhere() {
            Matrix chain = Dobby.chainFromLiftplan(Matrix.parse("X.\n.."));
            assertThat(Dobby.assertChainWeaves(chain)).isSameAs(chain);
        }

        @Test
        void refusesAChainThatNeverOpensOne() {
            assertThatThrownBy(() -> Dobby.assertChainWeaves(Dobby.chainFromLiftplan(Matrix.parse("..\n.."))))
                    .isInstanceOf(DraftException.class);
        }
    }
}
