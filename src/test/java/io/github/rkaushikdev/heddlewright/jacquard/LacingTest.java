package io.github.rkaushikdev.heddlewright.jacquard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LacingTest {

    private static final Machine MACHINE = Machine.defineMachine("bench", 2, 4);
    private static final List<Card> DECK =
            Deck.deckFromHookLists(MACHINE, List.of(List.of(1), List.of(2), List.of(3), List.of(4)));

    private static Card extra() {
        return Card.punchCard(MACHINE, List.of(8));
    }

    @Nested
    class LacingCordMetres {

        @Test
        void measuresBothSidesAndAddsATailForTheKnots() {
            assertThat(Lacing.lacingCordMetres(100)).isEqualTo(4);
        }

        @Test
        void growsWithTheChain() {
            assertThat(Lacing.lacingCordMetres(200)).isGreaterThan(Lacing.lacingCordMetres(100));
        }

        @Test
        void takesASingleSidedLacing() {
            assertThat(Lacing.lacingCordMetres(100, 8, 1, 400)).isEqualTo(2);
        }

        @Test
        void takesADifferentCardPitch() {
            assertThat(Lacing.lacingCordMetres(100, 4, 2, 0)).isEqualTo(1.6);
        }
    }

    @Nested
    class LacingKnots {

        @Test
        void isTwoKnotsASideWhateverTheLength() {
            assertThat(Lacing.lacingKnots(50)).isEqualTo(4);
            assertThat(Lacing.lacingKnots(5000)).isEqualTo(4);
            assertThat(Lacing.lacingKnots(50, 1)).isEqualTo(2);
        }
    }

    @Nested
    class LacingPlan {

        @Test
        void joinsEachCardToTheNextAndClosesTheLoop() {
            assertThat(Lacing.lacingPlan(3))
                    .containsExactly(new Lacing.Pass(1, 2), new Lacing.Pass(2, 3), new Lacing.Pass(3, 1));
        }

        @Test
        void hasOnePassPerCard() {
            assertThat(Lacing.lacingPlan(120)).hasSize(120);
        }

        @Test
        void lacesASingleCardBackToItself() {
            assertThat(Lacing.lacingPlan(1)).containsExactly(new Lacing.Pass(1, 1));
        }
    }

    @Nested
    class SpliceAfter {

        @Test
        void putsNewCardsInAndRenumbersWhatFollows() {
            List<Card> spliced = Lacing.spliceAfter(DECK, 2, List.of(extra()));
            assertThat(Deck.deckHooks(spliced, MACHINE))
                    .isEqualTo(List.of(List.of(1), List.of(2), List.of(8), List.of(3), List.of(4)));
            assertThat(spliced).extracting(Card::number).containsExactly(1, 2, 3, 4, 5);
        }

        @Test
        void splicesAtTheHeadWhenToldToGoAfterNothing() {
            assertThat(Deck.deckHooks(Lacing.spliceAfter(DECK, 0, List.of(extra())), MACHINE)
                            .get(0))
                    .containsExactly(8);
        }

        @Test
        void splicesAtTheTail() {
            List<List<Integer>> hooks = Deck.deckHooks(Lacing.spliceAfter(DECK, 4, List.of(extra())), MACHINE);
            assertThat(hooks.get(hooks.size() - 1)).containsExactly(8);
        }

        @Test
        void refusesAPositionPastTheChain() {
            assertThatThrownBy(() -> Lacing.spliceAfter(DECK, 5, List.of(Card.punchCard(MACHINE, List.of(1)))))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class CutOut {

        @Test
        void removesTheNamedCardsAndRenumbers() {
            List<Card> cut = Lacing.cutOut(DECK, List.of(2, 3));
            assertThat(Deck.deckHooks(cut, MACHINE)).isEqualTo(List.of(List.of(1), List.of(4)));
            assertThat(cut).extracting(Card::number).containsExactly(1, 2);
        }

        @Test
        void ignoresACardNamedTwice() {
            assertThat(Lacing.cutOut(DECK, List.of(2, 2))).hasSize(3);
        }

        @Test
        void refusesToCutTheWholeChainAway() {
            assertThatThrownBy(() -> Lacing.cutOut(DECK, List.of(1, 2, 3, 4))).hasMessageContaining("whole chain");
        }

        @Test
        void refusesACardTheChainHasNotGot() {
            assertThatThrownBy(() -> Lacing.cutOut(DECK, List.of(9))).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class ReplaceCard {

        @Test
        void swapsOneCardAndLeavesTheRestAlone() {
            List<Card> swapped = Lacing.replaceCard(DECK, 2, extra());
            assertThat(Deck.deckHooks(swapped, MACHINE))
                    .isEqualTo(List.of(List.of(1), List.of(8), List.of(3), List.of(4)));
        }

        @Test
        void keepsTheChainTheSameLength() {
            assertThat(Lacing.replaceCard(DECK, 1, extra())).hasSameSizeAs(DECK);
        }

        @Test
        void renumbersTheReplacementIntoItsPlace() {
            List<Card> swapped = Lacing.replaceCard(DECK, 3, Card.punchCard(MACHINE, List.of(8), 99));
            assertThat(swapped.get(2).number()).isEqualTo(3);
        }
    }

    @Nested
    class CardNumbered {

        @Test
        void findsACardByItsNumber() {
            assertThat(Card.renderCard(Lacing.cardNumbered(DECK, 2))).isEqualTo(Card.renderCard(DECK.get(1)));
        }

        @Test
        void saysPlainlyWhenThereIsNoSuchCard() {
            assertThatThrownBy(() -> Lacing.cardNumbered(DECK, 9)).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class JoinPositionsAndSectionCount {

        @Test
        void marksAJoinAtTheEndOfEverySectionAndAtTheEndOfTheChain() {
            assertThat(Lacing.joinPositions(1000, 250)).containsExactly(250, 500, 750, 1000);
        }

        @Test
        void leavesAShortLastSectionJoinedWhereTheChainEnds() {
            assertThat(Lacing.joinPositions(900, 400)).containsExactly(400, 800, 900);
            assertThat(Lacing.sectionCount(900, 400)).isEqualTo(3);
        }

        @Test
        void hasOneJoinOnAChainShorterThanASection() {
            assertThat(Lacing.joinPositions(100, 250)).containsExactly(100);
            assertThat(Lacing.sectionCount(100, 250)).isEqualTo(1);
        }
    }
}
