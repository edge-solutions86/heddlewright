package io.github.rkaushikdev.heddlewright.analysis;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Collation;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.weave.Twill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Colour and weave effects.
 *
 * <p>The cloth you see is not the weave and it is not the colour order: it is what happens when the
 * two are laid over one another. A plain two by two twill in four dark and four light ends and picks
 * reads as houndstooth, and nothing in either the weave or the colour order on its own predicts
 * that.
 *
 * <p>The rule is simple and the results are not: where the warp is on the face, the cell takes the
 * warp colour; where it is not, it takes the weft colour.
 */
public final class ColourOrder {

    /** The visible repeat once the weave and both colour orders are taken together. */
    public record EffectRepeat(int picks, int ends) {}

    /** One stripe of a colour order: a colour and how many threads of it. */
    public record Stripe(String colour, int count) {}

    /** How many cells of the grid one colour covers. */
    public record ColourCount(String colour, int cells) {}

    private static final List<String> ALPHABET = List.of("#", ".", "o", "+", "*", "~");

    private ColourOrder() {}

    /** The colour of every cell, from a drawdown and the two colour orders. */
    public static List<List<String>> colourGrid(Matrix drawdown, List<String> warpColours, List<String> weftColours) {
        Check.array(warpColours, "warpColours", 1);
        Check.array(weftColours, "weftColours", 1);
        List<List<String>> grid = new ArrayList<>(drawdown.rows());
        for (int p = 0; p < drawdown.rows(); p += 1) {
            List<String> row = new ArrayList<>(drawdown.cols());
            for (int e = 0; e < drawdown.cols(); e += 1) {
                row.add(
                        drawdown.isSet(p, e)
                                ? warpColours.get(e % warpColours.size())
                                : weftColours.get(p % weftColours.size()));
            }
            grid.add(List.copyOf(row));
        }
        return List.copyOf(grid);
    }

    /**
     * How large the visible repeat is once the weave and both colour orders are taken together. It is
     * the least common multiple, and it is usually much bigger than any one of them.
     */
    public static EffectRepeat effectRepeat(int weavePicks, int weaveEnds, int warpOrder, int weftOrder) {
        Check.positiveInteger(weavePicks, "weavePicks");
        Check.positiveInteger(weaveEnds, "weaveEnds");
        Check.positiveInteger(warpOrder, "warpOrder");
        Check.positiveInteger(weftOrder, "weftOrder");
        return new EffectRepeat(Numeric.lcm(weavePicks, weftOrder), Numeric.lcm(weaveEnds, warpOrder));
    }

    /** Expand a colour order written as stripes into one entry per thread. */
    public static List<String> expandStripes(List<Stripe> stripes) {
        Check.array(stripes, "stripes", 1);
        List<String> out = new ArrayList<>();
        for (int index = 0; index < stripes.size(); index += 1) {
            Stripe stripe = Check.object(stripes.get(index), "stripes[" + index + "]");
            String colour = Check.nonEmptyString(stripe.colour(), "stripes[" + index + "].colour");
            int count = Check.positiveInteger(stripe.count(), "stripes[" + index + "].count");
            for (int n = 0; n < count; n += 1) {
                out.add(colour);
            }
        }
        return List.copyOf(out);
    }

    /** How many cells each colour covers, most first. */
    public static List<ColourCount> colourCounts(List<List<String>> grid) {
        Check.array(grid, "grid", 1);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (List<String> row : grid) {
            for (String colour : row) {
                counts.merge(colour, 1, Integer::sum);
            }
        }
        List<ColourCount> out = new ArrayList<>();
        counts.forEach((colour, cells) -> out.add(new ColourCount(colour, cells)));
        out.sort(Comparator.comparingInt(ColourCount::cells)
                .reversed()
                .thenComparing(ColourCount::colour, Collation.ORDER));
        return List.copyOf(out);
    }

    /** The grid printed with one character per colour, the commonest colour first in the alphabet. */
    public static String renderColourGrid(List<List<String>> grid) {
        Check.array(grid, "grid", 1);
        Map<String, String> symbols = new LinkedHashMap<>();
        int next = 0;
        for (ColourCount count : colourCounts(grid)) {
            symbols.put(count.colour(), ALPHABET.get(next % ALPHABET.size()));
            next += 1;
        }
        return renderColourGrid(grid, symbols);
    }

    /** The grid printed with the character each colour is given, and a question mark for any other. */
    public static String renderColourGrid(List<List<String>> grid, Map<String, String> symbols) {
        Check.array(grid, "grid", 1);
        return grid.stream()
                .map(row -> row.stream()
                        .map(colour -> symbols.getOrDefault(colour, "?"))
                        .collect(Collectors.joining()))
                .collect(Collectors.joining("\n"));
    }

    /** Houndstooth in dark and light. */
    public static List<List<String>> houndstooth() {
        return houndstooth("dark", "light");
    }

    /**
     * Houndstooth: a two by two twill in four dark and four light, both ways. The broken check it
     * produces is the standard demonstration that a colour effect is not the weave and not the colour
     * order.
     */
    public static List<List<String>> houndstooth(String dark, String light) {
        Matrix weave = Twill.twillWeave("2/2", Twill.Options.DEFAULT.size(8, 8));
        List<String> order = expandStripes(List.of(new Stripe(dark, 4), new Stripe(light, 4)));
        return colourGrid(weave, order, order);
    }

    /** A step pattern in dark and light. */
    public static List<List<String>> stepPattern() {
        return stepPattern("dark", "light");
    }

    /**
     * A step pattern, the other classic: the same twill in one dark and one light end and pick, which
     * reads as fine diagonal lines rather than a check.
     */
    public static List<List<String>> stepPattern(String dark, String light) {
        Matrix weave = Twill.twillWeave("2/2", Twill.Options.DEFAULT.size(8, 8));
        List<String> order = expandStripes(List.of(new Stripe(dark, 1), new Stripe(light, 1)));
        return colourGrid(weave, order, order);
    }

    /** True when the effect is balanced, which is what makes a check read square. */
    public static boolean isBalancedEffect(List<List<String>> grid) {
        List<ColourCount> counts = colourCounts(grid);
        return counts.size() == 2 && counts.get(0).cells() == counts.get(1).cells();
    }
}
