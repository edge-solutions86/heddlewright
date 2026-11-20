package io.github.rkaushikdev.heddlewright.jacquard;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lacing, and the repairs that go with it.
 *
 * <p>Cards are strung together through the lacing holes at each end, cord over and cord under, and
 * the last card is laced back to the first so the chain runs round the cylinder without an end. Cards
 * wear out, patterns get altered, and a card is spliced in or cut out of a live chain far more often
 * than a whole deck is cut fresh, so those operations belong here.
 */
public final class Lacing {

    /** One pass of the cord, from a card to the one it is laced to. */
    public record Pass(int from, int to) {}

    private Lacing() {}

    /** Cord for a chain at 8 mm pitch, laced both sides, with 400 mm a side for the knots. */
    public static double lacingCordMetres(int cards) {
        return lacingCordMetres(cards, 8, 2, 400);
    }

    /** How much cord a chain needs, both sides, with a tail for the knots. */
    public static double lacingCordMetres(int cards, double pitchMm, int sides, double allowanceMm) {
        Check.positiveInteger(cards, "cards");
        Check.positiveNumber(pitchMm, "pitchMm");
        Check.positiveInteger(sides, "sides");
        Check.number(allowanceMm, "allowanceMm", Bounds.atLeast(0));
        // Each card takes two passes of cord per side: one down and one back.
        double perSide = cards * pitchMm * 2 + allowanceMm;
        return Numeric.roundTo((perSide * sides) / 1000, 3);
    }

    /** The knots a two-sided lacing needs. */
    public static int lacingKnots(int cards) {
        return lacingKnots(cards, 2);
    }

    /** The knots a lacing needs, counting the one that closes the loop. */
    public static int lacingKnots(int cards, int sides) {
        Check.positiveInteger(cards, "cards");
        Check.positiveInteger(sides, "sides");
        return sides * 2;
    }

    /**
     * Where the cord passes, as a list of card pairs. The chain is a loop, so the last pair joins the
     * last card back to the first.
     */
    public static List<Pass> lacingPlan(int cards) {
        Check.positiveInteger(cards, "cards");
        List<Pass> plan = new ArrayList<>(cards);
        for (int card = 1; card <= cards; card += 1) {
            plan.add(new Pass(card, card % cards + 1));
        }
        return List.copyOf(plan);
    }

    /** Splice cards into a live chain after the given card number. */
    public static List<Card> spliceAfter(List<Card> deck, int afterCard, List<Card> cards) {
        Check.array(deck, "deck", 1);
        Check.integer(afterCard, "afterCard", Bounds.between(0, deck.size()));
        Check.array(cards, "cards", 1);
        List<Card> out = new ArrayList<>(deck.subList(0, afterCard));
        out.addAll(cards);
        out.addAll(deck.subList(afterCard, deck.size()));
        return Deck.renumber(out);
    }

    /** Cut cards out of a chain by their 1-based numbers. */
    public static List<Card> cutOut(List<Card> deck, List<?> numbers) {
        Check.array(deck, "deck", 1);
        List<Object> named = Check.array(numbers, "numbers", 1);
        Set<Integer> drop = new HashSet<>();
        for (int i = 0; i < named.size(); i += 1) {
            drop.add(Check.integer(named.get(i), "numbers[" + i + "]", Bounds.between(1, deck.size())));
        }
        if (drop.size() == deck.size()) {
            throw new InvalidArgumentException("that would cut the whole chain away", Json.obj("cards", deck.size()));
        }
        List<Card> kept = new ArrayList<>();
        for (int index = 0; index < deck.size(); index += 1) {
            if (!drop.contains(index + 1)) {
                kept.add(deck.get(index));
            }
        }
        return Deck.renumber(kept);
    }

    /** Swap one card for another, keeping the rest of the chain as it is. */
    public static List<Card> replaceCard(List<Card> deck, int number, Card card) {
        Check.array(deck, "deck", 1);
        Check.integer(number, "number", Bounds.between(1, deck.size()));
        List<Card> out = new ArrayList<>(deck);
        out.set(number - 1, card);
        return Deck.renumber(out);
    }

    /** Find a card by number, or say plainly that the chain has no such card. */
    public static Card cardNumbered(List<Card> deck, int number) {
        Check.array(deck, "deck", 1);
        return deck.stream()
                .filter(card -> card.number() == number)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "the chain has no card " + number, Json.obj("number", number, "cards", deck.size())));
    }

    /**
     * The join is where two lengths of chain meet. A chain laced from several sections carries one
     * join per section, and they are the first thing to check when a chain jams on the cylinder.
     */
    public static List<Integer> joinPositions(int cards, int sectionLength) {
        Check.positiveInteger(cards, "cards");
        Check.positiveInteger(sectionLength, "sectionLength");
        List<Integer> joins = new ArrayList<>();
        for (int at = sectionLength; at < cards; at += sectionLength) {
            joins.add(at);
        }
        joins.add(cards);
        return List.copyOf(joins);
    }

    /** How many sections a chain of this length is laced from. */
    public static int sectionCount(int cards, int sectionLength) {
        Check.positiveInteger(cards, "cards");
        Check.positiveInteger(sectionLength, "sectionLength");
        return (int) Math.ceil((double) cards / sectionLength);
    }
}
