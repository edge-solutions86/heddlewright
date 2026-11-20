package io.github.rkaushikdev.heddlewright.jacquard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VerifyTest {

    private static final Machine MACHINE = Machine.defineMachine("bench", 2, 4);
    private static final Machine OTHER = Machine.defineMachine("wide bench", 2, 6);
    private static final List<Card> GOOD =
            Deck.deckFromHookLists(MACHINE, List.of(List.of(1, 2), List.of(3, 4), List.of(5, 6), List.of(7, 8)));

    private static List<Card> goodPlus(Card... extra) {
        List<Card> deck = new ArrayList<>(GOOD);
        deck.addAll(List.of(extra));
        return deck;
    }

    private static Card everything() {
        return Card.punchCard(MACHINE, List.of(1, 2, 3, 4, 5, 6, 7, 8));
    }

    private static List<Card> hooks(Object... lists) {
        return Deck.deckFromHookLists(MACHINE, List.of(lists));
    }

    @Nested
    class BlankCards {

        @Test
        void findsACardThatLiftsNothing() {
            assertThat(Verify.blankCards(goodPlus(Card.blankCard(MACHINE))))
                    .containsExactly(new Verify.Finding("blank_card", 5, "card 5 lifts nothing"));
        }

        @Test
        void isQuietOnADeckWhereEveryCardLiftsSomething() {
            assertThat(Verify.blankCards(GOOD)).isEmpty();
        }
    }

    @Nested
    class FullCards {

        @Test
        void findsACardCutRightThrough() {
            assertThat(Verify.fullCards(goodPlus(everything())))
                    .extracting(Verify.Finding::card)
                    .containsExactly(5);
        }

        @Test
        void isQuietOtherwise() {
            assertThat(Verify.fullCards(GOOD)).isEmpty();
        }
    }

    @Nested
    class WeakCards {

        @Test
        void findsACardWithTooMuchCutAwayWithoutCountingAFullOneTwice() {
            List<Card> deck = List.of(Card.punchCard(MACHINE, List.of(1, 2, 3, 4, 5, 6, 7)));
            assertThat(Verify.weakCards(deck)).extracting(Verify.Finding::card).containsExactly(1);
            assertThat(Verify.fullCards(deck)).isEmpty();
        }

        @Test
        void takesADifferentLimit() {
            List<Card> deck = List.of(Card.punchCard(MACHINE, List.of(1, 2, 3, 4, 5)));
            assertThat(Verify.weakCards(deck)).isEmpty();
            assertThat(Verify.weakCards(deck, 0.5)).hasSize(1);
        }
    }

    @Nested
    class MisfitCards {

        @Test
        void findsACardCutForADifferentMachine() {
            List<Verify.Finding> findings = Verify.misfitCards(goodPlus(Card.blankCard(OTHER)), MACHINE);
            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).card()).isEqualTo(5);
        }

        @Test
        void isQuietOnADeckCutForTheMachineItIsGoingOn() {
            assertThat(Verify.misfitCards(GOOD, MACHINE)).isEmpty();
        }
    }

    @Nested
    class IdleHooks {

        @Test
        void namesHooksNoCardEverLifts() {
            assertThat(Verify.idleHooks(hooks(List.of(1), List.of(2)), MACHINE)).containsExactly(3, 4, 5, 6, 7, 8);
        }

        @Test
        void isEmptyWhenTheDeckReachesEveryHook() {
            assertThat(Verify.idleHooks(GOOD, MACHINE)).isEmpty();
        }
    }

    @Nested
    class WastedCards {

        @Test
        void findsADeckCutLongerThanItsOwnRepeat() {
            List<Verify.Finding> findings = Verify.wastedCards(hooks(List.of(1), List.of(2), List.of(1), List.of(2)));
            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).message()).contains("repeats after 2 cards");
        }

        @Test
        void isQuietOnADeckThatIsItsOwnRepeat() {
            assertThat(Verify.wastedCards(GOOD)).isEmpty();
        }
    }

    @Nested
    class VerifyDeck {

        @Test
        void readsASoundDeckAsSound() {
            Verify.Verification report = Verify.verifyDeck(GOOD, MACHINE);
            assertThat(report.sound()).isTrue();
            assertThat(report.cards()).isEqualTo(4);
            assertThat(report.idleHooks()).isEmpty();
        }

        @Test
        void gathersEveryFindingInCardOrder() {
            Verify.Verification report = Verify.verifyDeck(goodPlus(Card.blankCard(MACHINE), everything()), MACHINE);
            assertThat(report.sound()).isFalse();
            assertThat(report.findings()).extracting(Verify.Finding::card).containsExactly(5, 6);
        }

        @Test
        void countsTheCardsACutterPunchedTwice() {
            assertThat(Verify.verifyDeck(hooks(List.of(1), List.of(2), List.of(1)), MACHINE)
                            .repeatedCards())
                    .isEqualTo(1);
        }
    }

    @Nested
    class AssertDeckSound {

        @Test
        void returnsTheReportForASoundDeck() {
            assertThat(Verify.assertDeckSound(GOOD, MACHINE).sound()).isTrue();
        }

        @Test
        void throwsWithEveryFindingAttached() {
            DraftException err = catchThrowableOfType(
                    DraftException.class, () -> Verify.assertDeckSound(goodPlus(Card.blankCard(MACHINE)), MACHINE));
            assertThat(err).isNotNull();
            assertThat(((JsonArray) err.detail().get("findings")).size()).isEqualTo(1);
        }
    }

    @Nested
    class SavingFromRepeat {

        @Test
        void pricesTheCardsAShorterDeckWouldSave() {
            assertThat(Verify.savingFromRepeat(hooks(List.of(1), List.of(2), List.of(1), List.of(2))))
                    .isEqualTo(new Verify.Saving(2, 9));
        }

        @Test
        void savesNothingOnADeckThatIsAlreadyItsOwnRepeat() {
            assertThat(Verify.savingFromRepeat(GOOD)).isEqualTo(new Verify.Saving(0, 0));
        }
    }
}
