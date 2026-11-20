package io.github.rkaushikdev.heddlewright.draft;

import io.github.rkaushikdev.heddlewright.util.Check;
import java.util.List;
import java.util.Locale;

/**
 * Which way the shed opens. A jack loom lifts what is tied; a counterbalance loom sinks it, so the
 * same cloth needs the complementary tie-up on the other kind of loom.
 */
public enum Shed {
    /** The tied shafts rise. */
    RISING,
    /** The tied shafts sink. */
    SINKING;

    /** The two shed types, in the order a draft file lists them. */
    public static final List<Shed> TYPES = List.of(values());

    /** Read a shed by its name, refusing anything else with the list of the ones it knows. */
    public static Shed parse(Object text) {
        return parse(text, "shed");
    }

    /** Read a shed by its name, naming the field in any complaint. */
    public static Shed parse(Object text, String field) {
        return Check.member(text, field, values());
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
