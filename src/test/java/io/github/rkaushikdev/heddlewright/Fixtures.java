package io.github.rkaushikdev.heddlewright;

import io.github.rkaushikdev.heddlewright.draft.Draft;
import io.github.rkaushikdev.heddlewright.draft.Threading;
import io.github.rkaushikdev.heddlewright.draft.Tieup;
import io.github.rkaushikdev.heddlewright.draft.Treadling;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;

/**
 * The cloths, looms and orders the tests share.
 *
 * <p>The four shaft twill had been written out longhand in nine test files, and they had drifted:
 * some threaded eight ends and some sixteen, so two files that read a repeat count off "the same"
 * draft were measuring different pieces. The looms had drifted the same way. They come from here.
 *
 * <p>Everything is a method and not a constant, so a test that amends a record to make its own
 * case cannot change what the next test reads. The records are immutable as well, so an amendment
 * is always a copy.
 */
public final class Fixtures {

    /** The size and name of a warp faced twill. */
    public record Twill(int shafts, int ends, int picks, int raise, String name) {

        /** The same twill on more or fewer shafts. */
        public Twill shafts(int count) {
            return new Twill(count, ends, picks, raise, name);
        }

        /** The same twill threaded over more or fewer ends. */
        public Twill ends(int count) {
            return new Twill(shafts, count, picks, raise, name);
        }

        /** The same twill trodden over more or fewer picks. */
        public Twill picks(int count) {
            return new Twill(shafts, ends, count, raise, name);
        }

        /** The same twill lifting more or fewer shafts a treadle. */
        public Twill raise(int count) {
            return new Twill(shafts, ends, picks, count, name);
        }

        /** The same twill under another name. */
        public Twill name(String text) {
            return new Twill(shafts, ends, picks, raise, text);
        }

        /** The plain record. */
        public JsonObject record() {
            return Json.obj(
                    "name",
                    name,
                    "shafts",
                    shafts,
                    "treadles",
                    shafts,
                    "threading",
                    Threading.straightDraw(shafts, ends),
                    "tieup",
                    Tieup.twillTieup(shafts, raise),
                    "treadling",
                    Treadling.straightTreadling(shafts, picks));
        }

        /** The same, built into a draft. */
        public Draft draft() {
            return Draft.create(record());
        }
    }

    private Fixtures() {}

    /** The four shaft twill, eight ends by eight picks, raising two. */
    public static Twill twill() {
        return new Twill(4, 8, 8, 2, "four shaft twill");
    }

    /** The plain record for the four shaft twill. */
    public static JsonObject twillRecord() {
        return twill().record();
    }

    /** The four shaft twill built into a draft. */
    public static Draft twillDraft() {
        return twill().draft();
    }

    /**
     * A narrow eight shaft dobby, the loom most of the shop floor tests load. It is a four box loom
     * because the shop floor tests need somewhere to put a second weft, and a plain one box loom is
     * the odd case rather than the usual one.
     */
    public static JsonObject narrowLoom() {
        return Json.obj(
                "reference", "N1",
                "shedding", "dobby",
                "reedWidthCm", 120,
                "shafts", 8,
                "picksMinute", 200,
                "shuttleBoxes", 4,
                "boxedAt", "one");
    }

    /** A broad sixteen shaft dobby, for the styles the narrow one cannot take. */
    public static JsonObject wideLoom() {
        return wideLoom(200);
    }

    /** The broad dobby at a running speed, for the tests that need a slower one. */
    public static JsonObject wideLoom(double picksMinute) {
        return Json.obj(
                "reference", "W1", "shedding", "dobby", "reedWidthCm", 260, "shafts", 16, "picksMinute", picksMinute);
    }

    /** A shirting order, priced and dated, with any fields laid over it. */
    public static JsonObject poplinOrder(Object... overrides) {
        return Json.obj(
                        "reference", "SO-1",
                        "customer", "Ravensward",
                        "style", "poplin",
                        "metres", 400,
                        "widthCm", 100,
                        "pricePerMetre", 5,
                        "wantedBy", "2025-10-01")
                .with(overrides);
    }

    /** A construction for the weight, sett and costing calculations, with any fields laid over it. */
    public static JsonObject shirtingConstruction(Object... overrides) {
        return Json.obj("endsPerCm", 24, "picksPerCm", 20, "warpCount", 20, "weftCount", 20, "system", "tex")
                .with(overrides);
    }
}
