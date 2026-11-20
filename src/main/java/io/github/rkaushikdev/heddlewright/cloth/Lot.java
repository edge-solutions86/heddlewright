package io.github.rkaushikdev.heddlewright.cloth;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.ConflictException;
import io.github.rkaushikdev.heddlewright.util.Group;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Shade lots.
 *
 * <p>Two pieces dyed in different vessels are never quite the same colour, and a garment cut across
 * the join shows it. So pieces are gathered into lots by how far apart their shade readings are, and
 * a lot is only usable if every piece in it sits inside the tolerance of every other, not merely of
 * the first one. That is the part a simple grouping gets wrong.
 */
public final class Lot {

    /** A shade reading against a piece, on the three colour axes. */
    public record Reading(String reference, double lightness, double red, double yellow) {}

    /** A point in colour space with no piece attached, such as the middle of a lot. */
    public record Shade(double lightness, double red, double yellow) {}

    /** Two pieces further apart than the tolerance. */
    public record Pair(String left, String right, double distance) {}

    /** A shade lot: its number, its pieces, its centre and its widest gap. */
    public record ShadeLot(int lot, List<String> pieces, Shade centre, double spread) {
        /** Copies the pieces. */
        public ShadeLot {
            pieces = List.copyOf(pieces);
        }
    }

    /** The pieces dyed in one vessel. */
    public record DyeLot(String lot, List<String> pieces) {
        /** Copies the pieces. */
        public DyeLot {
            pieces = List.copyOf(pieces);
        }
    }

    private Lot() {}

    /** Book a shade reading against a piece, on the three colour axes. */
    public static Reading readShade(Object entry) {
        JsonObject record = Check.plainObject(entry, "reading");
        return new Reading(
                Check.nonEmptyString(record.get("reference"), "reference"),
                Check.number(record.get("lightness"), "lightness"),
                Check.number(record.get("red"), "red"),
                Check.number(record.get("yellow"), "yellow"));
    }

    /** How far apart two readings are, as a straight distance in colour space. */
    public static double shadeDistance(Reading left, Reading right) {
        double dl = left.lightness() - right.lightness();
        double da = left.red() - right.red();
        double db = left.yellow() - right.yellow();
        return Numeric.roundTo(Math.sqrt(dl * dl + da * da + db * db), 4);
    }

    /** Every pair in a lot that sits further apart than the tolerance, widest first. */
    public static List<Pair> outliers(List<Reading> readings, double tolerance) {
        Check.array(readings, "readings", 1);
        Check.positiveNumber(tolerance, "tolerance");
        List<Pair> found = new ArrayList<>();
        for (int i = 0; i < readings.size(); i += 1) {
            for (int j = i + 1; j < readings.size(); j += 1) {
                double distance = shadeDistance(readings.get(i), readings.get(j));
                if (distance > tolerance) {
                    found.add(new Pair(
                            readings.get(i).reference(), readings.get(j).reference(), distance));
                }
            }
        }
        found.sort(Comparator.comparingDouble(Pair::distance).reversed());
        return List.copyOf(found);
    }

    /** A lot holds together when no two pieces in it are further apart than the tolerance. */
    public static boolean lotHolds(List<Reading> readings, double tolerance) {
        return outliers(readings, tolerance).isEmpty();
    }

    /**
     * Gather readings into lots. A piece joins a lot only when it sits inside the tolerance of every
     * piece already in it, so a lot cannot drift across the whole colour space one small step at a
     * time.
     */
    public static List<ShadeLot> buildLots(List<Reading> readings, double tolerance) {
        Check.array(readings, "readings", 1);
        Check.positiveNumber(tolerance, "tolerance");
        List<List<Reading>> lots = new ArrayList<>();
        for (Reading reading : readings) {
            List<Reading> home = lots.stream()
                    .filter(lot -> lot.stream().allMatch(member -> shadeDistance(member, reading) <= tolerance))
                    .findFirst()
                    .orElse(null);
            if (home == null) {
                lots.add(new ArrayList<>(List.of(reading)));
                continue;
            }
            home.add(reading);
        }
        List<ShadeLot> out = new ArrayList<>(lots.size());
        for (int index = 0; index < lots.size(); index += 1) {
            List<Reading> members = lots.get(index);
            out.add(new ShadeLot(
                    index + 1,
                    members.stream().map(Reading::reference).toList(),
                    lotCentre(members),
                    lotSpread(members)));
        }
        return List.copyOf(out);
    }

    /** The middle of a lot, which is what a shade card records. */
    public static Shade lotCentre(List<Reading> readings) {
        Check.array(readings, "readings", 1);
        return new Shade(
                Numeric.roundTo(Numeric.meanBy(readings, Reading::lightness, "readings"), 4),
                Numeric.roundTo(Numeric.meanBy(readings, Reading::red, "readings"), 4),
                Numeric.roundTo(Numeric.meanBy(readings, Reading::yellow, "readings"), 4));
    }

    /** The widest gap inside a lot, which is what a customer would see. */
    public static double lotSpread(List<Reading> readings) {
        Check.array(readings, "readings", 1);
        double widest = 0;
        for (int i = 0; i < readings.size(); i += 1) {
            for (int j = i + 1; j < readings.size(); j += 1) {
                widest = Math.max(widest, shadeDistance(readings.get(i), readings.get(j)));
            }
        }
        return Numeric.roundTo(widest, 4);
    }

    /** Refuse a lot that would not hold together. */
    public static List<Reading> assertLotHolds(List<Reading> readings, double tolerance) {
        List<Pair> found = outliers(readings, tolerance);
        if (!found.isEmpty()) {
            throw new ConflictException(
                    "those pieces are not one shade lot", Json.obj("pairs", found.size(), "widest", found.get(0)));
        }
        return readings;
    }

    /** Which lot a piece was put in, by reference. */
    public static int lotOf(List<ShadeLot> lots, String reference) {
        Check.nonEmptyString(reference, "reference");
        return lots.stream()
                .filter(lot -> lot.pieces().contains(reference))
                .findFirst()
                .orElseThrow(() ->
                        new InvalidArgumentException("no lot holds " + reference, Json.obj("reference", reference)))
                .lot();
    }

    /** How many lots a delivery would need at a given tolerance. */
    public static int lotCount(List<Reading> readings, double tolerance) {
        return buildLots(readings, tolerance).size();
    }

    /**
     * Pieces grouped by the lot they were dyed in, rather than by shade, in the order the lots first
     * appear. A piece with no lot recorded goes under "unrecorded"; a lot or a reference that is not
     * text is read as its text.
     */
    public static List<DyeLot> byDyeLot(List<?> pieces) {
        List<JsonObject> records = new ArrayList<>();
        for (Object piece : Check.array(pieces, "items")) {
            records.add(Check.plainObject(piece, "piece"));
        }
        Map<String, List<JsonObject>> groups = Group.groupBy(records, (JsonObject piece) -> {
            Json lot = piece.get("lot");
            return lot.isNullish() ? "unrecorded" : Text.string(lot);
        });
        List<DyeLot> out = new ArrayList<>(groups.size());
        groups.forEach((lot, members) -> out.add(new DyeLot(
                lot,
                members.stream()
                        .map(piece -> Text.string(piece.get("reference")))
                        .toList())));
        return List.copyOf(out);
    }
}
