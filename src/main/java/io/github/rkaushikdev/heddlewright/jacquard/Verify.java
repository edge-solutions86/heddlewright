package io.github.rkaushikdev.heddlewright.jacquard;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Collation;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checking a deck before it goes on the machine.
 *
 * <p>Cards are cut once and hang for years, so every fault worth catching is worth catching before
 * the deck is laced. Each check returns findings rather than throwing, because a deck usually has
 * several things slightly wrong with it and a card cutter wants the whole list, not the first one.
 */
public final class Verify {

    /** One thing wrong with a deck, against the card it was found on. */
    public record Finding(String code, int card, String message) {}

    /** Every check on a deck, gathered. */
    public record Verification(
            int cards, List<Finding> findings, List<Integer> idleHooks, int repeatedCards, boolean sound) {
        /** Copies the lists. */
        public Verification {
            findings = List.copyOf(findings);
            idleHooks = List.copyOf(idleHooks);
        }
    }

    /** What cutting only the repeat would save: the cards and their price. */
    public record Saving(int spareCards, double pence) {}

    /** The share of a card that may be cut away before it is weak. */
    public static final double MAX_CUT_AWAY = 0.85;

    private Verify() {}

    /** A card that lifts nothing leaves the shed shut on that pick. */
    public static List<Finding> blankCards(List<Card> deck) {
        Check.array(deck, "deck", 1);
        List<Finding> out = new ArrayList<>();
        for (int index = 0; index < deck.size(); index += 1) {
            if (Card.holeCount(deck.get(index)) == 0) {
                int card = index + 1;
                out.add(new Finding("blank_card", card, "card " + card + " lifts nothing"));
            }
        }
        return List.copyOf(out);
    }

    /** A card cut right through lifts everything, which also opens no shed. */
    public static List<Finding> fullCards(List<Card> deck) {
        Check.array(deck, "deck", 1);
        List<Finding> out = new ArrayList<>();
        for (int index = 0; index < deck.size(); index += 1) {
            if (Card.cutAwayRatio(deck.get(index)) == 1) {
                int card = index + 1;
                out.add(new Finding("full_card", card, "card " + card + " lifts every hook"));
            }
        }
        return List.copyOf(out);
    }

    /** Cards with more than {@link #MAX_CUT_AWAY} cut away. */
    public static List<Finding> weakCards(List<Card> deck) {
        return weakCards(deck, MAX_CUT_AWAY);
    }

    /**
     * A card with too much cut away tears at the lacing. The share is a shed setting rather than a
     * law, so it comes in as an option.
     */
    public static List<Finding> weakCards(List<Card> deck, double maxCutAway) {
        Check.array(deck, "deck", 1);
        List<Finding> out = new ArrayList<>();
        for (int index = 0; index < deck.size(); index += 1) {
            double ratio = Card.cutAwayRatio(deck.get(index));
            if (ratio > maxCutAway && ratio < 1) {
                int card = index + 1;
                out.add(new Finding(
                        "weak_card",
                        card,
                        "card " + card + " has " + Text.number(Numeric.roundTo(ratio * 100, 1))
                                + " per cent cut away"));
            }
        }
        return List.copyOf(out);
    }

    /** Cards cut for a different machine, which will not feed at all. */
    public static List<Finding> misfitCards(List<Card> deck, Machine machine) {
        Check.object(machine, "machine");
        List<Finding> findings = new ArrayList<>();
        for (int index = 0; index < deck.size(); index += 1) {
            try {
                Card.assertCardFitsMachine(deck.get(index), machine);
            } catch (RuntimeException err) {
                findings.add(new Finding(
                        "misfit_card",
                        index + 1,
                        "card " + (index + 1) + " does not fit " + machine.name() + ": " + err.getMessage()));
            }
        }
        return List.copyOf(findings);
    }

    /**
     * A hook that no card in the deck ever lifts. Its cords could come out of the comber board, and if
     * the design meant to use it, something is wrong with the cutting rather than with the machine.
     */
    public static List<Integer> idleHooks(List<Card> deck, Machine machine) {
        Set<Integer> lifted = new HashSet<>();
        for (Card card : deck) {
            lifted.addAll(Card.cardHooks(card, machine));
        }
        List<Integer> out = new ArrayList<>();
        for (int hook = 1; hook <= machine.hooks(); hook += 1) {
            if (!lifted.contains(hook)) {
                out.add(hook);
            }
        }
        return List.copyOf(out);
    }

    /** A deck that repeats sooner than it is long has cards nobody needed to cut. */
    public static List<Finding> wastedCards(List<Card> deck) {
        int repeat = Deck.deckRepeat(deck);
        int length = Deck.deckLength(deck);
        if (repeat == length) {
            return List.of();
        }
        return List.of(new Finding(
                "wasted_cards",
                repeat + 1,
                "the pattern repeats after " + repeat + " cards but the deck is " + length + " long"));
    }

    /** Every check, with the usual weak card limit. */
    public static Verification verifyDeck(List<Card> deck, Machine machine) {
        return verifyDeck(deck, machine, MAX_CUT_AWAY);
    }

    /** Every check, gathered, the findings in card order and then by code. */
    public static Verification verifyDeck(List<Card> deck, Machine machine, double maxCutAway) {
        List<Finding> findings = new ArrayList<>(misfitCards(deck, machine));
        findings.addAll(blankCards(deck));
        findings.addAll(fullCards(deck));
        findings.addAll(weakCards(deck, maxCutAway));
        findings.addAll(wastedCards(deck));
        findings.sort(Comparator.comparingInt(Finding::card).thenComparing(Finding::code, Collation.ORDER));
        return new Verification(
                Deck.deckLength(deck),
                findings,
                idleHooks(deck, machine),
                Deck.repeatedCards(deck).size(),
                findings.isEmpty());
    }

    /** Refuse a deck that will not run, with the usual weak card limit. */
    public static Verification assertDeckSound(List<Card> deck, Machine machine) {
        return assertDeckSound(deck, machine, MAX_CUT_AWAY);
    }

    /** Refuse a deck that will not run, naming everything wrong with it. */
    public static Verification assertDeckSound(List<Card> deck, Machine machine, double maxCutAway) {
        Verification report = verifyDeck(deck, machine, maxCutAway);
        if (!report.sound()) {
            throw new DraftException(
                    "the deck will not run as it stands",
                    Json.obj("cards", report.cards(), "findings", report.findings()));
        }
        return report;
    }

    /** The saving at 4.5 pence a card. */
    public static Saving savingFromRepeat(List<Card> deck) {
        return savingFromRepeat(deck, 4.5);
    }

    /** How much card stock a deck would save if its repeat were cut instead. */
    public static Saving savingFromRepeat(List<Card> deck, double cardPence) {
        Check.integer(Deck.deckLength(deck), "cards", Bounds.atLeast(1));
        int spare = Deck.deckLength(deck) - Deck.deckRepeat(deck);
        return new Saving(spare, Numeric.roundTo(spare * cardPence, 2));
    }
}
