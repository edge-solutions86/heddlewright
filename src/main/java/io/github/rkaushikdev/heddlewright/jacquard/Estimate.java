package io.github.rkaushikdev.heddlewright.jacquard;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Costing a card cutting job.
 *
 * <p>Cutting a deck is priced on three things and they pull in different directions: the cards
 * themselves, the holes punched in them, and the lacing. A large sparse design has many cards and few
 * holes; a small dense one is the other way round, and a shop that quotes on card count alone loses
 * money on the second kind.
 */
public final class Estimate {

    /** A card shop's rates. */
    public record Rates(
            double cardPence,
            double holesPerHour,
            double lacingMetresPerHour,
            double labourPencePerHour,
            double cordPencePerMetre,
            double wastage) {}

    /** The shop's rates when nobody says otherwise. */
    public static final Rates DEFAULT_RATES = new Rates(4.5, 9000, 220, 1850, 3, 0.03);

    /** The cards a piece needs: the pattern, the spares for wastage, and both together. */
    public record Cards(int pattern, int spare, int total) {}

    /** A quotation for a deck, every money figure in pence. */
    public record Quote(
            int cards,
            int holes,
            double cordMetres,
            double punchHours,
            double laceHours,
            double cardPence,
            double cordPence,
            double punchPence,
            double lacePence,
            double labourPence,
            double totalPence) {}

    /** A deck's bulk plus the rail it takes up. */
    public record Logistics(int cards, double thicknessMm, double kilograms, double metresOfRail) {}

    /** Two quotations side by side, which is cheaper, and by how much. */
    public record Comparison(Quote left, Quote right, String cheaper, double differencePence) {}

    private Estimate() {}

    /** The shop defaults. */
    public static Rates resolveRates() {
        return resolveRates(JsonObject.EMPTY);
    }

    /**
     * Merge caller rates over the defaults, checking each one it is given. The caller's rates are a
     * plain record naming only what changes, or a whole {@link Rates}.
     */
    public static Rates resolveRates(Object rates) {
        JsonObject given = Check.plainObject(rates instanceof Rates whole ? Json.of(whole) : rates, "rates");
        JsonObject merged = ((JsonObject) Json.of(DEFAULT_RATES)).merge(given);
        return new Rates(
                Check.positiveNumber(merged.get("cardPence"), "cardPence"),
                Check.positiveNumber(merged.get("holesPerHour"), "holesPerHour"),
                Check.positiveNumber(merged.get("lacingMetresPerHour"), "lacingMetresPerHour"),
                Check.positiveNumber(merged.get("labourPencePerHour"), "labourPencePerHour"),
                Check.positiveNumber(merged.get("cordPencePerMetre"), "cordPencePerMetre"),
                Check.number(merged.get("wastage"), "wastage"));
    }

    /** How many cards one repeat needs, and how many spare to cut. */
    public static Cards cardsRequired(int picksPerRepeat) {
        return cardsRequired(picksPerRepeat, 1, JsonObject.EMPTY);
    }

    /** How many cards a piece needs at the shop's wastage. */
    public static Cards cardsRequired(int picksPerRepeat, int repeats) {
        return cardsRequired(picksPerRepeat, repeats, JsonObject.EMPTY);
    }

    /** How many cards a piece needs, and how many spare to cut for wastage. */
    public static Cards cardsRequired(int picksPerRepeat, int repeats, Object rates) {
        Check.positiveInteger(picksPerRepeat, "picksPerRepeat");
        Check.positiveInteger(repeats, "repeats");
        Rates merged = resolveRates(rates);
        int pattern = picksPerRepeat * repeats;
        int spare = (int) Math.ceil(pattern * merged.wastage());
        return new Cards(pattern, spare, pattern + spare);
    }

    /** The hours a punch takes at the shop's rate. */
    public static double cuttingHours(int holes) {
        return cuttingHours(holes, JsonObject.EMPTY);
    }

    /** The hours a punch takes to cut a given number of holes. */
    public static double cuttingHours(int holes, Object rates) {
        Check.positiveInteger(holes, "holes");
        return Numeric.roundTo(holes / resolveRates(rates).holesPerHour(), 3);
    }

    /** The hours the lacing takes at the shop's rate. */
    public static double lacingHours(double cordMetres) {
        return lacingHours(cordMetres, JsonObject.EMPTY);
    }

    /** The hours the lacing takes, from the cord it uses. */
    public static double lacingHours(double cordMetres, Object rates) {
        Check.positiveNumber(cordMetres, "cordMetres");
        return Numeric.roundTo(cordMetres / resolveRates(rates).lacingMetresPerHour(), 3);
    }

    /** A quotation at the shop's rates, guessing the cord from the card count. */
    public static Quote quoteDeck(List<Card> deck) {
        return quoteDeck(deck, OptionalDouble.empty(), JsonObject.EMPTY);
    }

    /** A quotation at the shop's rates for a known length of cord. */
    public static Quote quoteDeck(List<Card> deck, double cordMetres) {
        return quoteDeck(deck, OptionalDouble.of(cordMetres), JsonObject.EMPTY);
    }

    /**
     * A whole quotation for a deck: card stock, punching, lacing, and the labour on top, all in pence
     * so nothing is lost rounding to a currency. Without a cord length it allows 32 mm a card.
     */
    public static Quote quoteDeck(List<Card> deck, OptionalDouble cordMetres, Object rates) {
        Rates merged = resolveRates(rates);
        int cards = Deck.deckLength(deck);
        int holes = Deck.totalHoles(deck);
        double cord = cordMetres.isPresent() ? cordMetres.getAsDouble() : cards * 0.032;
        double punchHours = cuttingHours(holes, merged);
        double laceHours = lacingHours(cord, merged);
        double cardCost = cards * merged.cardPence();
        double cordCost = cord * merged.cordPencePerMetre();
        double punchCost = punchHours * merged.labourPencePerHour();
        double laceCost = laceHours * merged.labourPencePerHour();
        return new Quote(
                cards,
                holes,
                Numeric.roundTo(cord, 3),
                punchHours,
                laceHours,
                Numeric.roundTo(cardCost, 2),
                Numeric.roundTo(cordCost, 2),
                Numeric.roundTo(punchCost, 2),
                Numeric.roundTo(laceCost, 2),
                Numeric.roundTo(punchCost + laceCost, 2),
                Numeric.roundTo(cardCost + cordCost + punchCost + laceCost, 2));
    }

    /** What the finished deck weighs on the usual stock, and how much rail it takes up. */
    public static Logistics deckLogistics(List<Card> deck) {
        return logistics(Deck.deckBulk(deck));
    }

    /** What the finished deck weighs and how much rail it takes up. */
    public static Logistics deckLogistics(List<Card> deck, double cardThicknessMm, double cardGrams) {
        return logistics(Deck.deckBulk(deck, cardThicknessMm, cardGrams));
    }

    private static Logistics logistics(Deck.Bulk bulk) {
        return new Logistics(
                bulk.cards(), bulk.thicknessMm(), bulk.kilograms(), Numeric.roundTo(bulk.thicknessMm() / 1000, 3));
    }

    /**
     * The share of the quotation that is punching rather than stock, which is the number that says
     * whether a design is dense or merely long.
     */
    public static double punchingShare(Quote quote) {
        if (quote.totalPence() == 0) {
            return 0;
        }
        return Numeric.roundTo(quote.punchPence() / quote.totalPence(), 4);
    }

    /** Compare two decks at the shop's rates. */
    public static Comparison compareDecks(List<Card> a, List<Card> b) {
        return compareDecks(a, b, OptionalDouble.empty(), JsonObject.EMPTY);
    }

    /** Compare two decks on the same rates, for choosing between two designs. */
    public static Comparison compareDecks(List<Card> a, List<Card> b, OptionalDouble cordMetres, Object rates) {
        Quote left = quoteDeck(a, cordMetres, rates);
        Quote right = quoteDeck(b, cordMetres, rates);
        return new Comparison(
                left,
                right,
                left.totalPence() <= right.totalPence() ? "left" : "right",
                Numeric.roundTo(Math.abs(left.totalPence() - right.totalPence()), 2));
    }
}
