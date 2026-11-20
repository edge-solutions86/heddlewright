package io.github.rkaushikdev.heddlewright.loom;

import io.github.rkaushikdev.heddlewright.loom.BoxMotion.Boxing;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import java.util.List;
import java.util.Locale;

/**
 * One loom on the register: its reference, how it sheds and picks, how wide its reed is, what it
 * carries in shafts or hooks, how fast it runs, and how its shuttle boxes are arranged.
 *
 * <p>{@code installed} is whatever the register recorded about when the loom went in, or null.
 */
public record Loom(
        String reference,
        Shedding shedding,
        Picking picking,
        double reedWidthCm,
        int shafts,
        int hooks,
        int treadles,
        double picksMinute,
        int shuttleBoxes,
        Boxing boxedAt,
        Json installed) {

    /** How a loom opens its shed. */
    public enum Shedding {
        /** Cams under the loom. */
        TAPPET,
        /** A dobby head reading a chain. */
        DOBBY,
        /** A jacquard head, every end on its own hook. */
        JACQUARD;

        /** The three motions, simplest first. */
        public static final List<Shedding> SHEDDING = List.of(values());

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** How a loom carries the weft across. */
    public enum Picking {
        /** A thrown shuttle. */
        SHUTTLE("shuttle"),
        /** A rapier. */
        RAPIER("rapier"),
        /** A projectile gripper. */
        PROJECTILE("projectile"),
        /** A jet of air. */
        AIR_JET("air jet");

        /** The four motions, oldest first. */
        public static final List<Picking> PICKING = List.of(values());

        private final String word;

        Picking(String word) {
            this.word = word;
        }

        @Override
        public String toString() {
            return word;
        }
    }

    /** The plain record the loom is stored as, which {@link Registry#createLoom} reads straight back. */
    public JsonObject toRecord() {
        return (JsonObject) Json.of(this);
    }
}
