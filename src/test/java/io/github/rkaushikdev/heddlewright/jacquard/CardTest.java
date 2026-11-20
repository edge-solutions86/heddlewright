package io.github.rkaushikdev.heddlewright.jacquard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CardTest {

    private static final Machine SMALL = Machine.defineMachine("bench", 2, 4);

    @Nested
    class BlankCard {

        @Test
        void isTheSizeOfTheNeedleBoardWithNothingPunched() {
            Card card = Card.blankCard(SMALL);
            assertThat(card.rows()).isEqualTo(2);
            assertThat(card.columns()).isEqualTo(4);
            assertThat(Card.holeCount(card)).isEqualTo(0);
            assertThat(Card.isBlank(card)).isTrue();
        }

        @Test
        void carriesItsNumberAndTheMachineItWasCutFor() {
            Card card = Card.blankCard(SMALL, 7);
            assertThat(card.number()).isEqualTo(7);
            assertThat(card.machine()).isEqualTo("bench");
        }

        @Test
        void namesHowManyPegAndLacingHolesACardCarries() {
            assertThat(Card.PEG_HOLES).isEqualTo(2);
            assertThat(Card.LACING_HOLES).isEqualTo(2);
        }
    }

    @Nested
    class PunchCard {

        @Test
        void punchesAHoleAtEachNamedHookReadingAlongTheRows() {
            assertThat(Card.renderCard(Card.punchCard(SMALL, List.of(1, 4, 5)))).isEqualTo("O..O\nO...");
        }

        @Test
        void punchesNothingForAnEmptyHookList() {
            assertThat(Card.isBlank(Card.punchCard(SMALL, List.of()))).isTrue();
        }

        @Test
        void ignoresAHookNamedTwice() {
            assertThat(Card.holeCount(Card.punchCard(SMALL, List.of(3, 3)))).isEqualTo(1);
        }

        @Test
        void refusesAHookPastTheMachine() {
            assertThatThrownBy(() -> Card.punchCard(SMALL, List.of(9))).isInstanceOf(CapacityException.class);
            assertThatThrownBy(() -> Card.punchCard(SMALL, List.of(0))).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class CardHooks {

        @Test
        void readsThePunchedHooksBackInMachineOrder() {
            assertThat(Card.cardHooks(Card.punchCard(SMALL, List.of(5, 1, 4)), SMALL))
                    .containsExactly(1, 4, 5);
        }

        @Test
        void roundTripsAnyHookSet() {
            for (List<Integer> hooks :
                    List.of(List.of(1), List.of(2, 6), List.of(1, 2, 3, 4, 5, 6, 7, 8), List.<Integer>of())) {
                assertThat(Card.cardHooks(Card.punchCard(SMALL, hooks), SMALL))
                        .isEqualTo(hooks.stream().sorted().toList());
            }
        }

        @Test
        void readsTheOtherWayForAMachineWhereSolidCardLifts() {
            Card card = Card.punchCard(SMALL, List.of(1));
            assertThat(Card.cardHooks(card, SMALL, false)).containsExactly(2, 3, 4, 5, 6, 7, 8);
        }

        @Test
        void refusesACardCutForADifferentMachine() {
            assertThatThrownBy(() -> Card.cardHooks(Card.blankCard(SMALL), Machine.machineByName("400 hook")))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class CutAwayRatio {

        @Test
        void isTheShareOfTheCardThatHasBeenRemoved() {
            assertThat(Card.cutAwayRatio(Card.punchCard(SMALL, List.of(1, 2)))).isEqualTo(0.25);
            assertThat(Card.cutAwayRatio(Card.blankCard(SMALL))).isEqualTo(0);
        }

        @Test
        void isOneOnACardPunchedRightThrough() {
            Card everything = Card.punchCard(SMALL, List.of(1, 2, 3, 4, 5, 6, 7, 8));
            assertThat(Card.cutAwayRatio(everything)).isEqualTo(1);
            assertThat(Card.isFullyPunched(everything)).isTrue();
        }
    }

    @Nested
    class AssertCardFitsMachine {

        @Test
        void passesACardCutForTheMachine() {
            Card card = Card.blankCard(SMALL);
            assertThat(Card.assertCardFitsMachine(card, SMALL)).isSameAs(card);
        }

        @Test
        void catchesACardWhoseStatedSizeDisagreesWithItsOwnGrid() {
            Card card = Card.blankCard(SMALL);
            Card altered = new Card(card.number(), card.rows(), 5, card.machine(), card.holes());
            assertThatThrownBy(() -> Card.assertCardFitsMachine(altered, SMALL))
                    .hasMessageContaining("does not fit that machine");
        }

        @Test
        void catchesAGridThatHasBeenResizedBehindTheCardRecord() {
            Card card = Card.blankCard(SMALL);
            Card altered = new Card(card.number(), card.rows(), card.columns(), card.machine(), Matrix.parse("...."));
            assertThatThrownBy(() -> Card.assertCardFitsMachine(altered, SMALL))
                    .hasMessageContaining("does not match its own stated size");
        }
    }

    @Nested
    class CardSizeMm {

        @Test
        void worksTheCardOutFromTheNeedlePitchPlusItsMargins() {
            Card.Size size = Card.cardSizeMm(Machine.machineByName("400 hook"));
            assertThat(size.width()).isEqualTo(50 * 8 + 24);
            assertThat(size.height()).isEqualTo(8 * 8 + 24);
            assertThat(size.pitchMm()).isEqualTo(8);
        }

        @Test
        void takesADifferentMargin() {
            assertThat(Card.cardSizeMm(SMALL, 0).width()).isEqualTo(32);
        }
    }

    @Nested
    class SameCard {

        @Test
        void isTrueForTwoCardsPunchedTheSameWay() {
            assertThat(Card.sameCard(Card.punchCard(SMALL, List.of(1, 5)), Card.punchCard(SMALL, List.of(5, 1))))
                    .isTrue();
        }

        @Test
        void ignoresTheCardNumberWhichIsBookkeepingRatherThanPattern() {
            assertThat(Card.sameCard(Card.punchCard(SMALL, List.of(1), 1), Card.punchCard(SMALL, List.of(1), 9)))
                    .isTrue();
        }

        @Test
        void isFalseOnceAHoleMoves() {
            assertThat(Card.sameCard(Card.punchCard(SMALL, List.of(1)), Card.punchCard(SMALL, List.of(2))))
                    .isFalse();
        }
    }
}
