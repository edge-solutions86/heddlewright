package io.github.rkaushikdev.heddlewright.jacquard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.heddlewright.jacquard.Cylinder.Direction;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CylinderTest {

    @Nested
    class FaceForCard {

        @Test
        void walksTheFourFacesRound() {
            assertThat(List.of(1, 2, 3, 4, 5, 8).stream()
                            .map(Cylinder::faceForCard)
                            .toList())
                    .containsExactly(1, 2, 3, 4, 1, 4);
        }

        @Test
        void namesTheFaceCountItAssumes() {
            assertThat(Cylinder.CYLINDER_FACES).isEqualTo(4);
        }

        @Test
        void takesACylinderWithADifferentNumberOfFaces() {
            assertThat(Cylinder.faceForCard(7, 6)).isEqualTo(1);
        }
    }

    @Nested
    class TurnAngle {

        @Test
        void turnsAQuarterPerPickOnAFourFacedCylinder() {
            assertThat(Cylinder.turnAngle(1)).isEqualTo(90);
            assertThat(Cylinder.turnAngle(2)).isEqualTo(180);
            assertThat(Cylinder.turnAngle(4)).isEqualTo(0);
            assertThat(Cylinder.turnAngle(5)).isEqualTo(90);
        }

        @Test
        void isZeroBeforeAnythingIsWoven() {
            assertThat(Cylinder.turnAngle(0)).isEqualTo(0);
        }
    }

    @Nested
    class Advance {

        @Test
        void movesTheChainOnAndWrapsAtTheJoin() {
            assertThat(Cylinder.advance(1, 3, 10)).isEqualTo(4);
            assertThat(Cylinder.advance(9, 3, 10)).isEqualTo(2);
            assertThat(Cylinder.advance(1, 10, 10)).isEqualTo(1);
        }

        @Test
        void runsTheChainBackwardsWhenToldTo() {
            assertThat(Cylinder.advance(4, 3, 10, Direction.REVERSE)).isEqualTo(1);
            assertThat(Cylinder.advance(2, 3, 10, Direction.REVERSE)).isEqualTo(9);
        }

        @Test
        void namesTheTwoDirections() {
            assertThat(Cylinder.DIRECTIONS).extracting(Object::toString).containsExactly("forward", "reverse");
            assertThatThrownBy(() -> Cylinder.advance(1, 1, 10, Direction.parse("sideways")))
                    .isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void refusesAStartingCardTheChainHasNotGot() {
            assertThatThrownBy(() -> Cylinder.advance(11, 1, 10)).hasMessageContaining("no such card");
        }

        @Test
        void staysWhereItIsForNoPicks() {
            assertThat(Cylinder.advance(5, 0, 10)).isEqualTo(5);
        }
    }

    @Nested
    class PicksBetween {

        @Test
        void countsForwardRoundTheLoop() {
            assertThat(Cylinder.picksBetween(1, 4, 10)).isEqualTo(3);
            assertThat(Cylinder.picksBetween(9, 2, 10)).isEqualTo(3);
            assertThat(Cylinder.picksBetween(4, 4, 10)).isEqualTo(0);
        }

        @Test
        void agreesWithAdvance() {
            for (int[] pair : new int[][] {{1, 7}, {8, 3}, {5, 5}}) {
                assertThat(Cylinder.advance(pair[0], Cylinder.picksBetween(pair[0], pair[1], 10), 10))
                        .isEqualTo(pair[1]);
            }
        }
    }

    @Nested
    class BackOff {

        @Test
        void undoesExactlyThePicksItIsGiven() {
            int after = Cylinder.advance(1, 6, 20);
            assertThat(Cylinder.backOff(after, 6, 20)).isEqualTo(1);
        }

        @Test
        void wrapsBackPastTheJoin() {
            assertThat(Cylinder.backOff(2, 5, 20)).isEqualTo(17);
        }
    }

    @Nested
    class ChainRevolutions {

        @Test
        void splitsThePicksIntoWholeTurnsAndWhatIsLeft() {
            assertThat(Cylinder.chainRevolutions(250, 100)).isEqualTo(new Cylinder.Revolutions(2, 50));
            assertThat(Cylinder.chainRevolutions(100, 100)).isEqualTo(new Cylinder.Revolutions(1, 0));
            assertThat(Cylinder.chainRevolutions(50, 100)).isEqualTo(new Cylinder.Revolutions(0, 50));
        }
    }

    @Nested
    class AssertChainFits {

        @Test
        void passesAChainTheRailCanCarry() {
            assertThat(Cylinder.assertChainFits(500, 800)).isEqualTo(500);
        }

        @Test
        void saysHowMuchChainIsOverTheLimit() {
            CapacityException err =
                    catchThrowableOfType(CapacityException.class, () -> Cylinder.assertChainFits(900, 800));
            assertThat(err).isNotNull();
            assertThat(err.detail().get("over")).isEqualTo(Json.of(100));
        }
    }

    @Nested
    class PatternLengthCmAndCardsForLength {

        @Test
        void turnsCardsIntoClothLengthAtAPickDensity() {
            assertThat(Cylinder.patternLengthCm(240, 24)).isEqualTo(10);
        }

        @Test
        void turnsClothLengthBackIntoCardsRoundingUpToAWholePick() {
            assertThat(Cylinder.cardsForLength(10, 24)).isEqualTo(240);
            assertThat(Cylinder.cardsForLength(10.01, 24)).isEqualTo(241);
        }

        @Test
        void refusesAPickDensityOfZero() {
            assertThatThrownBy(() -> Cylinder.patternLengthCm(240, 0)).isInstanceOf(InvalidArgumentException.class);
        }
    }
}
