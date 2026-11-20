package io.github.rkaushikdev.heddlewright.jacquard;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Cycle;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Ids;
import io.github.rkaushikdev.heddlewright.util.Indexing;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.ArrayList;
import java.util.List;

/**
 * The card deck, which the shed calls the chain.
 *
 * <p>Cards are laced end to end in weaving order and hang in a loop over the cylinder, so the deck
 * has no first card in any real sense: it has a starting point, and two decks that differ only in
 * where they were cut are the same pattern. Everything here treats the chain as a loop. A deck is a
 * {@code List<Card>}.
 */
public final class Deck {

    /** How many cards hang, how thick the chain is and what it weighs. */
    public record Bulk(int cards, double thicknessMm, double kilograms) {}

    private Deck() {}

    /** Cut a deck from a liftplan, one card per pick. */
    public static List<Card> deckFromLiftplan(Machine machine, Matrix liftplan) {
        Check.required(liftplan, "liftplan");
        if (liftplan.cols() > machine.hooks()) {
            throw new DraftException(
                    "the liftplan is wider than the machine",
                    Json.obj("hooks", machine.hooks(), "width", liftplan.cols()));
        }
        List<Card> deck = new ArrayList<>(liftplan.rows());
        for (int index = 0; index < liftplan.rows(); index += 1) {
            deck.add(Card.punchCard(machine, Indexing.setPositions(liftplan.row(index)), index + 1));
        }
        return List.copyOf(deck);
    }

    /** Cut a deck straight from a list of lifted hooks per pick; a bare number is a single hook. */
    public static List<Card> deckFromHookLists(Machine machine, List<?> hookLists) {
        List<Object> lists = Check.array(hookLists, "hookLists", 1);
        List<Card> deck = new ArrayList<>(lists.size());
        for (int index = 0; index < lists.size(); index += 1) {
            Object hooks = lists.get(index);
            List<?> named = hooks instanceof List<?> list ? list : java.util.Collections.singletonList(hooks);
            deck.add(Card.punchCard(machine, named, index + 1));
        }
        return List.copyOf(deck);
    }

    /** How many cards the deck holds. */
    public static int deckLength(List<Card> deck) {
        return Check.array(deck, "deck", 1).size();
    }

    /** The hook lists the deck weaves, in order, on a machine where a hole lifts. */
    public static List<List<Integer>> deckHooks(List<Card> deck, Machine machine) {
        return deckHooks(deck, machine, true);
    }

    /** The hook lists the deck weaves, in order. */
    public static List<List<Integer>> deckHooks(List<Card> deck, Machine machine, boolean holeMeansLift) {
        Check.array(deck, "deck", 1);
        return deck.stream()
                .map(card -> Card.cardHooks(card, machine, holeMeansLift))
                .toList();
    }

    private static List<String> rendered(List<Card> deck) {
        return deck.stream().map(Card::renderCard).toList();
    }

    /** A stable id for the pattern a deck weaves, ignoring where it was cut. */
    public static String deckId(List<Card> deck) {
        Check.array(deck, "deck", 1);
        return Ids.contentId("deck", String.join("|", Cycle.canonicalRotation(rendered(deck))));
    }

    /** How many picks before the chain comes back round to its own beginning. */
    public static int deckRepeat(List<Card> deck) {
        Check.array(deck, "deck", 1);
        return Cycle.period(rendered(deck));
    }

    /** The deck cut down to a single repeat, renumbered from one. */
    public static List<Card> minimalDeck(List<Card> deck) {
        return renumber(deck.subList(0, deckRepeat(deck)));
    }

    /** Start the chain at a different card, which weaves the same cloth. */
    public static List<Card> rotateDeck(List<Card> deck, int by) {
        Check.array(deck, "deck", 1);
        return renumber(Cycle.rotate(deck, by));
    }

    /** Two decks weave the same cloth when their patterns match round the loop. */
    public static boolean sameDeck(List<Card> a, List<Card> b) {
        return deckId(a).equals(deckId(b));
    }

    /** Cards punched identically, which a card cutter could have shared. */
    public static List<List<Integer>> repeatedCards(List<Card> deck) {
        Check.array(deck, "deck", 1);
        return Indexing.duplicateGroups(deck, (Card card) -> Card.renderCard(card));
    }

    /** Number every card in the deck from one, in place order. */
    public static List<Card> renumber(List<Card> deck) {
        Check.array(deck, "deck", 1);
        List<Card> out = new ArrayList<>(deck.size());
        for (int index = 0; index < deck.size(); index += 1) {
            out.add(deck.get(index).withNumber(index + 1));
        }
        return List.copyOf(out);
    }

    /** Refuse a deck that a machine could not read. */
    public static List<Card> assertDeckFitsMachine(List<Card> deck, Machine machine) {
        Check.array(deck, "deck", 1);
        for (int index = 0; index < deck.size(); index += 1) {
            try {
                Card.assertCardFitsMachine(deck.get(index), machine);
            } catch (RuntimeException err) {
                throw new DraftException(
                        "card " + (index + 1) + " does not fit the machine",
                        Json.obj("card", index + 1, "reason", err.getMessage()));
            }
        }
        return deck;
    }

    /** The hanging chain on the usual stock: 0.6 mm and 18 grams a card. */
    public static Bulk deckBulk(List<Card> deck) {
        return deckBulk(deck, 0.6, 18);
    }

    /** How thick the deck is when it is hanging, and what it weighs. */
    public static Bulk deckBulk(List<Card> deck, double cardThicknessMm, double cardGrams) {
        Check.positiveNumber(cardThicknessMm, "cardThicknessMm");
        Check.positiveNumber(cardGrams, "cardGrams");
        int cards = deckLength(deck);
        return new Bulk(
                cards, Numeric.roundTo(cards * cardThicknessMm, 2), Numeric.roundTo((cards * cardGrams) / 1000, 3));
    }

    /** The total holes cut across the whole deck, which sizes the punching job. */
    public static int totalHoles(List<Card> deck) {
        Check.array(deck, "deck", 1);
        return (int) Numeric.sumBy(deck, (Card card) -> Card.holeCount(card), "deck");
    }

    /** Extend a deck by repeating it until it covers the picks wanted. */
    public static List<Card> extendDeck(List<Card> deck, int picks) {
        Check.array(deck, "deck", 1);
        Check.positiveInteger(picks, "picks");
        List<Card> out = new ArrayList<>(picks);
        for (int i = 0; i < picks; i += 1) {
            out.add(deck.get(i % deck.size()));
        }
        return renumber(out);
    }
}
