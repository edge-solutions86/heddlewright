package io.github.rkaushikdev.heddlewright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CycleTest {

    @Nested
    class Rotate {

        @Test
        void movesTheFrontToTheBackOnAPositiveCount() {
            assertThat(Cycle.rotate(List.of(1, 2, 3, 4), 1)).containsExactly(2, 3, 4, 1);
            assertThat(Cycle.rotate(List.of(1, 2, 3, 4), 3)).containsExactly(4, 1, 2, 3);
        }

        @Test
        void rotatesTheOtherWayOnANegativeCount() {
            assertThat(Cycle.rotate(List.of(1, 2, 3, 4), -1)).containsExactly(4, 1, 2, 3);
        }

        @Test
        void wrapsCountsLargerThanTheLength() {
            assertThat(Cycle.rotate(List.of(1, 2, 3), 4)).containsExactly(2, 3, 1);
            assertThat(Cycle.rotate(List.of(1, 2, 3), 0)).containsExactly(1, 2, 3);
            assertThat(Cycle.rotate(List.of(), 5)).isEmpty();
        }

        @Test
        void leavesTheInputAlone() {
            List<Integer> seq = new ArrayList<>(List.of(1, 2, 3));
            Cycle.rotate(seq, 2);
            assertThat(seq).containsExactly(1, 2, 3);
        }
    }

    @Nested
    class Rotations {

        @Test
        void listsOneEntryPerPositionForASequenceWithNoInternalRepeat() {
            assertThat(Cycle.rotations(List.of(1, 2, 3)))
                    .isEqualTo(List.of(List.of(1, 2, 3), List.of(2, 3, 1), List.of(3, 1, 2)));
        }

        @Test
        void dropsTheDuplicatesARepeatingSequenceProduces() {
            assertThat(Cycle.rotations(List.of(1, 0, 1, 0)))
                    .isEqualTo(List.of(List.of(1, 0, 1, 0), List.of(0, 1, 0, 1)));
            assertThat(Cycle.rotations(List.of(1, 1, 1))).isEqualTo(List.of(List.of(1, 1, 1)));
        }
    }

    @Nested
    class Period {

        @Test
        void findsTheShortestBlockThatRebuildsTheSequence() {
            assertThat(Cycle.period(List.of(1, 0, 1, 0, 1, 0))).isEqualTo(2);
            assertThat(Cycle.period(List.of(1, 2, 3, 1, 2, 3))).isEqualTo(3);
            assertThat(Cycle.period(List.of(1, 1, 1, 1))).isEqualTo(1);
        }

        @Test
        void isTheWholeLengthWhenNothingShorterWorks() {
            assertThat(Cycle.period(List.of(1, 2, 3, 4))).isEqualTo(4);
            assertThat(Cycle.period(List.of(1, 0, 1, 0, 1))).isEqualTo(5);
        }

        @Test
        void isZeroForAnEmptySequence() {
            assertThat(Cycle.period(List.of())).isEqualTo(0);
        }

        @Test
        void minimalRepeatCutsTheSequenceToOnePeriod() {
            assertThat(Cycle.minimalRepeat(List.of(1, 0, 1, 0, 1, 0))).containsExactly(1, 0);
            assertThat(Cycle.minimalRepeat(List.of(2, 4, 3))).containsExactly(2, 4, 3);
        }
    }

    @Nested
    class IsRotationOf {

        @Test
        void seesTheSameChainCutAtADifferentLag() {
            assertThat(Cycle.isRotationOf(List.of(1, 2, 3, 4), List.of(3, 4, 1, 2)))
                    .isTrue();
            assertThat(Cycle.isRotationOf(List.of(1, 2, 3, 4), List.of(1, 2, 3, 4)))
                    .isTrue();
        }

        @Test
        void isFalseForAReversalOrADifferentLength() {
            assertThat(Cycle.isRotationOf(List.of(1, 2, 3), List.of(3, 2, 1))).isFalse();
            assertThat(Cycle.isRotationOf(List.of(1, 2, 3), List.of(1, 2))).isFalse();
        }

        @Test
        void treatsTwoEmptySequencesAsRotationsOfEachOther() {
            assertThat(Cycle.isRotationOf(List.of(), List.of())).isTrue();
        }
    }

    @Nested
    class RotationOffset {

        @Test
        void reportsHowFarTheSecondChainHasBeenAdvanced() {
            assertThat(Cycle.rotationOffset(List.of(1, 2, 3, 4), List.of(3, 4, 1, 2)))
                    .isEqualTo(2);
            assertThat(Cycle.rotationOffset(List.of(1, 2, 3), List.of(1, 2, 3))).isEqualTo(0);
        }

        @Test
        void isMinus1WhenNoRotationMatches() {
            assertThat(Cycle.rotationOffset(List.of(1, 2, 3), List.of(3, 2, 1))).isEqualTo(-1);
            assertThat(Cycle.rotationOffset(List.of(1, 2), List.of(1, 2, 3))).isEqualTo(-1);
        }

        @Test
        void reportsTheSmallestOffsetForARepeatingChain() {
            assertThat(Cycle.rotationOffset(List.of(1, 0, 1, 0), List.of(0, 1, 0, 1)))
                    .isEqualTo(1);
        }
    }

    @Nested
    class CanonicalRotation {

        @Test
        void givesEveryRotationOfTheSameChainOneKey() {
            List<Integer> base = Cycle.canonicalRotation(List.of(3, 1, 2));
            assertThat(Cycle.canonicalRotation(List.of(1, 2, 3))).isEqualTo(base);
            assertThat(Cycle.canonicalRotation(List.of(2, 3, 1))).isEqualTo(base);
        }

        @Test
        void separatesChainsThatAreNotRotations() {
            assertThat(Cycle.canonicalRotation(List.of(1, 2, 3)))
                    .isNotEqualTo(Cycle.canonicalRotation(List.of(1, 3, 2)));
        }

        @Test
        void handlesTheEmptySequence() {
            assertThat(Cycle.canonicalRotation(List.of())).isEmpty();
        }
    }

    @Nested
    class StepSequence {

        @Test
        void walksTheRingByTheMoveNumberWhichIsASatinPickOrder() {
            assertThat(Cycle.stepSequence(5, 2)).containsExactly(0, 2, 4, 1, 3);
            assertThat(Cycle.stepSequence(5, 3)).containsExactly(0, 3, 1, 4, 2);
            assertThat(Cycle.stepSequence(8, 3)).containsExactly(0, 3, 6, 1, 4, 7, 2, 5);
        }

        @Test
        void takesACountShorterOrLongerThanTheRing() {
            assertThat(Cycle.stepSequence(5, 2, 3)).containsExactly(0, 2, 4);
            assertThat(Cycle.stepSequence(4, 1, 6)).containsExactly(0, 1, 2, 3, 0, 1);
        }

        @Test
        void handlesANegativeStepByWrapping() {
            assertThat(Cycle.stepSequence(5, -1)).containsExactly(0, 4, 3, 2, 1);
        }
    }

    @Nested
    class StepCycleLengthAndStepCoversRing {

        @Test
        void aCoprimeStepVisitsEveryPosition() {
            assertThat(Cycle.stepCycleLength(5, 2)).isEqualTo(5);
            assertThat(Cycle.stepCoversRing(5, 2)).isTrue();
            assertThat(Cycle.stepCoversRing(8, 3)).isTrue();
        }

        @Test
        void aStepSharingAFactorClosesEarly() {
            assertThat(Cycle.stepCycleLength(8, 2)).isEqualTo(4);
            assertThat(Cycle.stepCoversRing(8, 2)).isFalse();
            assertThat(Cycle.stepCycleLength(6, 3)).isEqualTo(2);
        }

        @Test
        void aStepOfZeroNeverMoves() {
            assertThat(Cycle.stepCycleLength(7, 0)).isEqualTo(1);
            assertThat(Cycle.stepCoversRing(7, 0)).isFalse();
        }

        @Test
        void needsAPositiveRingSize() {
            assertThatThrownBy(() -> Cycle.stepCycleLength(0, 1)).isInstanceOf(InvalidArgumentException.class);
        }
    }
}
