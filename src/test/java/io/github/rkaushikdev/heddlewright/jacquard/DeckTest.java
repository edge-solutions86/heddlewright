package io.github.rkaushikdev.heddlewright.jacquard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DeckTest {

    private static final Machine MACHINE = Machine.defineMachine("bench", 2, 4);
    private static final Machine OTHER = Machine.defineMachine("wide bench", 2, 6);
    private static final Matrix PLAN = Matrix.parse("X.X.X.X.\n.X.X.X.X");

    private static List<Card> hooks(Object... lists) {
        return Deck.deckFromHookLists(MACHINE, List.of(lists));
    }

    private static List<Integer> numbers(List<Card> deck) {
        return deck.stream().map(Card::number).toList();
    }

    @Nested
    class DeckFromLiftplan {

        @Test
        void cutsOneCardPerPickAndNumbersThemInWeavingOrder() {
            List<Card> deck = Deck.deckFromLiftplan(MACHINE, PLAN);
            assertThat(Deck.deckLength(deck)).isEqualTo(2);
            assertThat(deck.get(0).number()).isEqualTo(1);
            assertThat(deck.get(1).number()).isEqualTo(2);
        }

        @Test
        void punchesTheHooksThePlanLifts() {
            List<Card> deck = Deck.deckFromLiftplan(MACHINE, PLAN);
            assertThat(Deck.deckHooks(deck, MACHINE)).isEqualTo(List.of(List.of(1, 3, 5, 7), List.of(2, 4, 6, 8)));
        }

        @Test
        void refusesAPlanWiderThanTheMachine() {
            assertThatThrownBy(() -> Deck.deckFromLiftplan(MACHINE, Matrix.parse("X.X.X.X.X.")))
                    .isInstanceOf(DraftException.class);
        }
    }

    @Nested
    class DeckFromHookLists {

        @Test
        void takesTheLiftedHooksDirectly() {
            assertThat(Deck.deckHooks(hooks(List.of(1, 2), List.of(3)), MACHINE))
                    .isEqualTo(List.of(List.of(1, 2), List.of(3)));
        }

        @Test
        void takesABareNumberForASingleHook() {
            assertThat(Deck.deckHooks(hooks(1, 2), MACHINE)).isEqualTo(List.of(List.of(1), List.of(2)));
        }
    }

    @Nested
    class DeckRepeatAndMinimalDeck {

        @Test
        void findsTheShortestChainThatWeavesTheSameCloth() {
            List<Card> deck = hooks(List.of(1), List.of(2), List.of(1), List.of(2));
            assertThat(Deck.deckRepeat(deck)).isEqualTo(2);
            assertThat(Deck.deckLength(Deck.minimalDeck(deck))).isEqualTo(2);
        }

        @Test
        void isTheWholeDeckWhenNothingRepeats() {
            assertThat(Deck.deckRepeat(hooks(List.of(1), List.of(2), List.of(3))))
                    .isEqualTo(3);
        }

        @Test
        void renumbersTheCutDeckFromOne() {
            List<Card> deck = hooks(List.of(1), List.of(2), List.of(1), List.of(2));
            assertThat(numbers(Deck.minimalDeck(deck))).containsExactly(1, 2);
        }
    }

    @Nested
    class RotateDeck {

        @Test
        void startsTheChainElsewhereAndRenumbers() {
            List<Card> moved = Deck.rotateDeck(hooks(List.of(1), List.of(2), List.of(3)), 1);
            assertThat(Deck.deckHooks(moved, MACHINE)).isEqualTo(List.of(List.of(2), List.of(3), List.of(1)));
            assertThat(numbers(moved)).containsExactly(1, 2, 3);
        }

        @Test
        void doesNotChangeWhatTheChainWeaves() {
            List<Card> deck = hooks(List.of(1), List.of(2), List.of(3));
            assertThat(Deck.sameDeck(deck, Deck.rotateDeck(deck, 2))).isTrue();
        }
    }

    @Nested
    class DeckId {

        @Test
        void isTheSameWhereverTheChainWasCut() {
            List<Card> deck = hooks(List.of(1), List.of(2), List.of(3), List.of(4));
            for (int by : List.of(0, 1, 2, 3)) {
                assertThat(Deck.deckId(Deck.rotateDeck(deck, by))).isEqualTo(Deck.deckId(deck));
            }
        }

        @Test
        void separatesChainsThatWeaveDifferentCloth() {
            assertThat(Deck.deckId(hooks(List.of(1), List.of(2))))
                    .isNotEqualTo(Deck.deckId(hooks(List.of(1), List.of(3))));
        }

        @Test
        void readsAsADeckPrefixAndAHash() {
            assertThat(Deck.deckId(hooks(List.of(1)))).matches("deck-[0-9a-f]{8}");
        }

        @Test
        void isNotFooledByAReversedChain() {
            List<Card> a = hooks(List.of(1), List.of(2), List.of(3));
            List<Card> b = hooks(List.of(3), List.of(2), List.of(1));
            assertThat(Deck.sameDeck(a, b)).isFalse();
        }
    }

    @Nested
    class RepeatedCards {

        @Test
        void groupsCardsACutterPunchedTheSameWay() {
            assertThat(Deck.repeatedCards(hooks(List.of(1), List.of(2), List.of(1), List.of(3))))
                    .isEqualTo(List.of(List.of(1, 3)));
        }

        @Test
        void isEmptyWhenEveryCardDiffers() {
            assertThat(Deck.repeatedCards(hooks(List.of(1), List.of(2)))).isEmpty();
        }
    }

    @Nested
    class Renumber {

        @Test
        void numbersFromOneWithoutTouchingTheHoles() {
            List<Card> deck = hooks(List.of(1), List.of(2));
            List<Card> shuffled = Deck.renumber(List.of(deck.get(1), deck.get(0)));
            assertThat(numbers(shuffled)).containsExactly(1, 2);
            assertThat(Card.renderCard(shuffled.get(0))).isEqualTo(Card.renderCard(deck.get(1)));
        }
    }

    @Nested
    class AssertDeckFitsMachine {

        @Test
        void passesADeckCutForTheMachine() {
            List<Card> deck = hooks(List.of(1));
            assertThat(Deck.assertDeckFitsMachine(deck, MACHINE)).isSameAs(deck);
        }

        @Test
        void namesTheCardThatDoesNotFit() {
            List<Card> deck = new ArrayList<>(hooks(List.of(1), List.of(2)));
            deck.add(Card.blankCard(OTHER));
            DraftException err =
                    catchThrowableOfType(DraftException.class, () -> Deck.assertDeckFitsMachine(deck, MACHINE));
            assertThat(err).isNotNull();
            assertThat(err.detail().get("card")).isEqualTo(Json.of(3));
        }
    }

    @Nested
    class DeckBulk {

        @Test
        void measuresTheHangingChain() {
            List<Card> deck = Deck.extendDeck(hooks(List.of(1), List.of(2)), 500);
            assertThat(Deck.deckBulk(deck)).isEqualTo(new Deck.Bulk(500, 300, 9));
        }

        @Test
        void takesADifferentCardStock() {
            List<Card> deck = Deck.extendDeck(hooks(List.of(1)), 100);
            assertThat(Deck.deckBulk(deck, 1, 25)).isEqualTo(new Deck.Bulk(100, 100, 2.5));
        }
    }

    @Nested
    class TotalHoles {

        @Test
        void addsTheHolesAcrossEveryCardWhichIsThePunchingJob() {
            assertThat(Deck.totalHoles(hooks(List.of(1, 2), List.of(3)))).isEqualTo(3);
        }
    }

    @Nested
    class ExtendDeck {

        @Test
        void repeatsTheChainToThePicksWanted() {
            List<Card> deck = hooks(List.of(1), List.of(2));
            assertThat(Deck.deckHooks(Deck.extendDeck(deck, 5), MACHINE))
                    .isEqualTo(List.of(List.of(1), List.of(2), List.of(1), List.of(2), List.of(1)));
        }

        @Test
        void renumbersTheExtendedChain() {
            assertThat(numbers(Deck.extendDeck(hooks(List.of(1), List.of(2)), 3)))
                    .containsExactly(1, 2, 3);
        }

        @Test
        void refusesAPickCountOfZero() {
            assertThatThrownBy(() -> Deck.extendDeck(hooks(List.of(1)), 0))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }
}
