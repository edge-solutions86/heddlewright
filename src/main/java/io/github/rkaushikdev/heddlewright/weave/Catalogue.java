package io.github.rkaushikdev.heddlewright.weave;

import io.github.rkaushikdev.heddlewright.analysis.Floats;
import io.github.rkaushikdev.heddlewright.analysis.Interlacement;
import io.github.rkaushikdev.heddlewright.analysis.Interlacement.Firmness;
import io.github.rkaushikdev.heddlewright.draft.Draft;
import io.github.rkaushikdev.heddlewright.draft.Drawdown;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Collation;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * The catalogue.
 *
 * <p>A weave is a name and a way of building one repeat. Keeping them in one register means a
 * report can walk every weave the package knows and measure it the same way, which is how the
 * numbers in the docs stay honest: nothing is written down that was not read off the cloth.
 */
public final class Catalogue {

    /** A catalogue entry: the weave's name, its family, and how to build one repeat. */
    public record Weave(String name, String family, Supplier<Matrix> build) {}

    /** Everything a report wants to say about one weave, read off the cloth. */
    public record Measurement(
            String name,
            String family,
            int picks,
            int ends,
            double warpFace,
            int longestFloat,
            double interlacement,
            Firmness firmness) {}

    private static final Map<String, Weave> WEAVES = new LinkedHashMap<>();

    static {
        define("plain", "plain", Plain::plainWeave);
        define("basket 2/2", "plain", () -> Plain.basketWeave(2));
        define("basket 3/3", "plain", () -> Plain.basketWeave(3));
        define("warp rib", "plain", () -> Plain.warpRib(2));
        define("weft rib", "plain", () -> Plain.weftRib(2));
        define("twill 2/2", "twill", () -> Twill.twillWeave("2/2"));
        define("twill 3/1", "twill", () -> Twill.twillWeave("3/1"));
        define("twill 1/3", "twill", () -> Twill.twillWeave("1/3"));
        define("twill 2/1/1/2", "twill", () -> Twill.twillWeave("2/1/1/2"));
        define("broken twill 2/2", "twill", () -> Twill.brokenTwill("2/2", 2));
        define("herringbone 2/2", "twill", () -> Twill.pointedTwill("2/2", 4));
        define("satin 5", "satin", () -> Satin.satinWeave(5, 2));
        define("satin 8", "satin", () -> Satin.satinWeave(8, 3));
        define("sateen 5", "satin", () -> Satin.satinWeave(5, 2, Satin.Face.WEFT));
        define("honeycomb 5", "figured", () -> Honeycomb.honeycombWeave(5));
        define("honeycomb 7", "figured", () -> Honeycomb.honeycombWeave(7));
        define("mock leno 2", "figured", () -> Leno.mockLeno(2));
        define("mock leno 3", "figured", () -> Leno.mockLeno(3));
        define("crepe 8", "crepe", () -> Crepe.satinCrepe(Crepe.SatinOptions.DEFAULT.threads(8)));
        define(
                "crepe rearranged",
                "crepe",
                () -> Crepe.rearrangedCrepe(Twill.twillWeave("2/2", Twill.Options.DEFAULT.size(8, 8))));
        define(
                "overshot ABCD",
                "block",
                () -> woven(Overshot.overshotDraft(List.of("A", "B", "C", "D"), Overshot.Options.DEFAULT.units(2))));
        define("summer and winter 121", "block", () -> woven(SummerWinter.summerWinterDraft(List.of(1, 2, 1), 2)));
    }

    private Catalogue() {}

    private static synchronized void define(String name, String family, Supplier<Matrix> build) {
        WEAVES.put(name, new Weave(name, family, build));
    }

    /** Weave a draft and hand back the cloth, for the entries that are drafts. */
    private static Matrix woven(JsonObject draft) {
        return Drawdown.drawdownFromDraft(Draft.create(draft));
    }

    /** Every weave in the catalogue, in name order. */
    public static synchronized List<Weave> listWeaves() {
        List<Weave> weaves = new ArrayList<>(WEAVES.values());
        weaves.sort(Comparator.comparing(Weave::name, Collation.ORDER));
        return List.copyOf(weaves);
    }

    /** The families the catalogue is grouped into. */
    public static List<String> families() {
        return List.copyOf(
                new TreeSet<>(listWeaves().stream().map(Weave::family).toList()));
    }

    /** Every weave in one family. */
    public static List<Weave> weavesInFamily(String family) {
        Check.nonEmptyString(family, "family");
        return listWeaves().stream()
                .filter(weave -> weave.family().equals(family))
                .toList();
    }

    private static synchronized Weave entry(String name) {
        Check.nonEmptyString(name, "name");
        Weave found = WEAVES.get(name);
        if (found == null) {
            throw new NotFoundException(
                    "no weave called \"" + name + "\"", Json.obj("name", name, "known", List.copyOf(WEAVES.keySet())));
        }
        return found;
    }

    /** Look a weave up and build one repeat of it. */
    public static Matrix weaveByName(String name) {
        return entry(name).build().get();
    }

    /** Everything a report wants to say about one weave, read off the cloth. */
    public static Measurement measure(String name) {
        Weave weave = entry(name);
        Matrix repeat = weave.build().get();
        return new Measurement(
                name,
                weave.family(),
                repeat.rows(),
                repeat.cols(),
                Drawdown.warpFaceRatio(repeat),
                Floats.longestFloat(repeat).longest(),
                Interlacement.interlacementFactor(repeat),
                Interlacement.firmnessClass(repeat));
    }

    /** The whole catalogue measured, for a table. */
    public static List<Measurement> measureAll() {
        return listWeaves().stream().map(weave -> measure(weave.name())).toList();
    }

    /** The weaves that stay inside a float limit, firmest first. */
    public static List<Measurement> weavesWithin(int limit) {
        List<Measurement> rows = new ArrayList<>();
        for (Measurement row : measureAll()) {
            if (row.longestFloat() <= limit) {
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparingDouble(Measurement::interlacement)
                .reversed()
                .thenComparing(Measurement::name, Collation.ORDER));
        return List.copyOf(rows);
    }

    /** Add a weave to the catalogue at runtime, for a shed with its own book. */
    public static String addWeave(Weave entry) {
        Check.object(entry, "entry");
        Check.nonEmptyString(entry.name(), "entry.name");
        Check.nonEmptyString(entry.family(), "entry.family");
        if (entry.build() == null) {
            throw new NotFoundException("a catalogue entry needs a build function", Json.obj("name", entry.name()));
        }
        define(entry.name(), entry.family(), entry.build());
        return entry.name();
    }

    /** Take a weave back out again, which a test that added one should do. */
    public static synchronized boolean removeWeave(String name) {
        Check.nonEmptyString(name, "name");
        return WEAVES.remove(name) != null;
    }
}
