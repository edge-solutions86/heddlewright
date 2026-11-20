package io.github.rkaushikdev.heddlewright.jacquard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EstimateTest {

    private static final Machine MACHINE = Machine.defineMachine("bench", 4, 8);
    private static final List<Card> SPARSE =
            Deck.extendDeck(Deck.deckFromHookLists(MACHINE, List.of(List.of(1), List.of(2))), 100);
    private static final List<Card> DENSE = Deck.extendDeck(
            Deck.deckFromHookLists(
                    MACHINE,
                    List.of(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), List.of(11, 12, 13, 14, 15, 16, 17, 18, 19, 20))),
            100);

    @Nested
    class ResolveRates {

        @Test
        void fillsInTheShopDefaults() {
            assertThat(Estimate.resolveRates()).isEqualTo(Estimate.DEFAULT_RATES);
        }

        @Test
        void takesAnOverrideWithoutLosingTheRest() {
            Estimate.Rates rates = Estimate.resolveRates(Json.obj("cardPence", 6));
            assertThat(rates.cardPence()).isEqualTo(6);
            assertThat(rates.holesPerHour()).isEqualTo(Estimate.DEFAULT_RATES.holesPerHour());
        }

        @Test
        void refusesARateOfZero() {
            assertThatThrownBy(() -> Estimate.resolveRates(Json.obj("holesPerHour", 0)))
                    .isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void leavesTheDefaultsFrozen() {
            // A record has no setters, so nothing can change one after it is built.
            assertThat(Estimate.DEFAULT_RATES.getClass().isRecord()).isTrue();
        }
    }

    @Nested
    class CardsRequired {

        @Test
        void multipliesTheRepeatOutAndAddsWastage() {
            assertThat(Estimate.cardsRequired(400, 2)).isEqualTo(new Estimate.Cards(800, 24, 824));
        }

        @Test
        void roundsAFractionalSpareUpToAWholeCard() {
            assertThat(Estimate.cardsRequired(10).spare()).isEqualTo(1);
        }

        @Test
        void takesADifferentWastageAllowance() {
            assertThat(Estimate.cardsRequired(100, 1, Json.obj("wastage", 0.1)).spare())
                    .isEqualTo(10);
        }
    }

    @Nested
    class CuttingHoursAndLacingHours {

        @Test
        void divideTheWorkByTheRate() {
            assertThat(Estimate.cuttingHours(9000)).isEqualTo(1);
            assertThat(Estimate.cuttingHours(4500)).isEqualTo(0.5);
            assertThat(Estimate.lacingHours(220)).isEqualTo(1);
        }

        @Test
        void followAnOverriddenRate() {
            assertThat(Estimate.cuttingHours(9000, Json.obj("holesPerHour", 4500)))
                    .isEqualTo(2);
        }
    }

    @Nested
    class QuoteDeck {

        @Test
        void pricesStockCordAndLabourSeparatelyAndAddsThemUp() {
            Estimate.Quote quote = Estimate.quoteDeck(SPARSE);
            assertThat(quote.cards()).isEqualTo(100);
            assertThat(quote.holes()).isEqualTo(100);
            assertThat(quote.cardPence()).isEqualTo(450);
            assertThat(quote.totalPence())
                    .isEqualTo(Math.round((quote.cardPence() + quote.cordPence() + quote.labourPence()) * 100) / 100.0);
        }

        @Test
        void splitsTheLabourIntoPunchingAndLacing() {
            Estimate.Quote quote = Estimate.quoteDeck(SPARSE);
            assertThat(quote.punchPence() + quote.lacePence()).isCloseTo(quote.labourPence(), within(0.005));
        }

        @Test
        void takesACordLengthRatherThanGuessingOne() {
            Estimate.Quote guessed = Estimate.quoteDeck(SPARSE);
            Estimate.Quote given = Estimate.quoteDeck(SPARSE, 20);
            assertThat(given.cordMetres()).isEqualTo(20);
            assertThat(given.totalPence()).isNotEqualTo(guessed.totalPence());
        }

        @Test
        void chargesMoreForADenseDeckThanASparseOneOfTheSameLength() {
            assertThat(Estimate.quoteDeck(DENSE).totalPence())
                    .isGreaterThan(Estimate.quoteDeck(SPARSE).totalPence());
            assertThat(Estimate.quoteDeck(DENSE).cards())
                    .isEqualTo(Estimate.quoteDeck(SPARSE).cards());
        }
    }

    @Nested
    class PunchingShare {

        @Test
        void isABiggerShareOfADenseDeckThanASparseOne() {
            assertThat(Estimate.punchingShare(Estimate.quoteDeck(DENSE)))
                    .isGreaterThan(Estimate.punchingShare(Estimate.quoteDeck(SPARSE)));
        }

        @Test
        void isBetweenNothingAndEverything() {
            double share = Estimate.punchingShare(Estimate.quoteDeck(DENSE));
            assertThat(share).isGreaterThan(0);
            assertThat(share).isLessThan(1);
        }
    }

    @Nested
    class DeckLogistics {

        @Test
        void reportsTheRailTheChainTakesUp() {
            Estimate.Logistics logistics = Estimate.deckLogistics(SPARSE);
            assertThat(logistics.cards()).isEqualTo(100);
            assertThat(logistics.metresOfRail()).isEqualTo(0.06);
        }
    }

    @Nested
    class CompareDecks {

        @Test
        void saysWhichOfTwoDecksIsCheaperAndByHowMuch() {
            Estimate.Comparison comparison = Estimate.compareDecks(SPARSE, DENSE);
            assertThat(comparison.cheaper()).isEqualTo("left");
            assertThat(comparison.differencePence())
                    .isCloseTo(
                            comparison.right().totalPence() - comparison.left().totalPence(), within(0.005));
        }

        @Test
        void callsTheLeftOneCheaperOnATie() {
            assertThat(Estimate.compareDecks(SPARSE, SPARSE).cheaper()).isEqualTo("left");
            assertThat(Estimate.compareDecks(SPARSE, SPARSE).differencePence()).isEqualTo(0);
        }
    }
}
