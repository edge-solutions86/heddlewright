package io.github.rkaushikdev.heddlewright.store;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.ConflictException;
import io.github.rkaushikdev.heddlewright.util.Ids;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The append-only journal.
 *
 * <p>Nothing in this package is edited in place. A draft is revised by appending the revision, a
 * piece is regraded by appending the regrade, and the current state is whatever falls out of
 * replaying the entries in order. Each entry carries the hash of the one before it, so a journal
 * that has been meddled with can be spotted rather than merely suspected.
 *
 * <p>An entry's hash is the FNV-1a hash of the canonical JSON of its sequence, type, payload, time
 * and previous hash, keys sorted, so a journal file written at any time reads back with the same
 * chain.
 */
public final class Journal {

    /** The hash the first entry follows. */
    public static final String GENESIS = "00000000";

    /**
     * One entry in the journal.
     *
     * @param sequence its place, from one
     * @param type what happened
     * @param payload the data it carries
     * @param at when it was written, or null
     * @param previous the hash of the entry before it
     * @param hash its own hash
     */
    public record Entry(int sequence, String type, Json payload, Json at, String previous, String hash) {}

    /**
     * Whether a journal's chain holds. A sound reading carries the entry count and the head; an
     * unsound one names the first entry that does not follow and why.
     */
    public record Verification(
            boolean sound, OptionalInt entries, Optional<String> head, OptionalInt sequence, Optional<String> reason)
            implements Json.Writable {

        static Verification sound(int entries, String head) {
            return new Verification(
                    true, OptionalInt.of(entries), Optional.of(head), OptionalInt.empty(), Optional.empty());
        }

        static Verification unsound(int sequence, String reason) {
            return new Verification(
                    false, OptionalInt.empty(), Optional.empty(), OptionalInt.of(sequence), Optional.of(reason));
        }

        @Override
        public Json toJson() {
            return sound
                    ? Json.obj("sound", true, "entries", entries.getAsInt(), "head", head.get())
                    : Json.obj("sound", false, "sequence", sequence.getAsInt(), "reason", reason.get());
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    private Journal() {}

    /** The live list of entries, which only this package may reach. */
    List<Entry> entryList() {
        return entries;
    }

    /** Open an empty journal. */
    public static Journal createJournal() {
        return new Journal();
    }

    /** Reopen a journal from entries already written, checking the chain as it goes. */
    public static Journal createJournal(List<?> written) {
        List<Object> list = Check.array(written, "entries");
        Journal journal = new Journal();
        for (Object entry : list) {
            appendRaw(journal, entry);
        }
        return journal;
    }

    /** Append an event with an empty payload and no time. */
    public static Entry append(Journal journal, String type) {
        return append(journal, type, JsonObject.EMPTY, Json.NULL);
    }

    /** Append an event with no time. */
    public static Entry append(Journal journal, String type, Object payload) {
        return append(journal, type, payload, Json.NULL);
    }

    /**
     * Append an event and return the entry that was written. The journal keeps its own canonical
     * copy of the payload, so a caller can go on editing what it passed in.
     */
    public static Entry append(Journal journal, String type, Object payload, Object at) {
        Check.nonEmptyString(type, "type");
        Check.plainObject(payload, "payload");
        int sequence = journal.entries.size() + 1;
        String previous =
                sequence == 1 ? GENESIS : journal.entries.get(sequence - 2).hash();
        Json copy = Json.canonicalClone(payload);
        Json when = at == null || Json.of(at).isNullish() ? Json.NULL : Json.of(at);
        String hash = entryHash(
                Json.obj("sequence", sequence, "type", type, "payload", copy, "at", when, "previous", previous));
        Entry entry = new Entry(sequence, type, copy, when, previous, hash);
        journal.entries.add(entry);
        return entry;
    }

    private static void appendRaw(Journal journal, Object raw) {
        JsonObject entry = Check.plainObject(raw, "entry");
        int expected = journal.entries.size() + 1;
        if (!entry.get("sequence").equals(Json.of(expected))) {
            throw new ConflictException(
                    "the journal entries are out of order",
                    Json.obj("expected", expected, "got", entry.get("sequence")));
        }
        String previous =
                expected == 1 ? GENESIS : journal.entries.get(expected - 2).hash();
        if (!entry.get("previous").equals(Json.of(previous))) {
            throw new ConflictException(
                    "an entry does not follow the one before it",
                    Json.obj("sequence", expected, "expected", previous, "got", entry.get("previous")));
        }
        if (!entry.get("hash").equals(Json.of(entryHash(entry)))) {
            throw new ConflictException(
                    "an entry has been altered since it was written", Json.obj("sequence", expected));
        }
        Json at = entry.get("at");
        journal.entries.add(new Entry(
                expected,
                Check.nonEmptyString(entry.get("type"), "entry.type"),
                entry.get("payload"),
                at.isMissing() ? Json.NULL : at,
                previous,
                entry.get("hash").asText()));
    }

    /** The hash an entry should carry, from everything but the hash itself. */
    public static String entryHash(Object entry) {
        JsonObject record = Check.plainObject(entry, "entry");
        return Ids.stableHash(Json.canonicalStringify(Json.obj(
                "sequence", record.get("sequence"),
                "type", record.get("type"),
                "payload", record.get("payload"),
                "at", record.get("at"),
                "previous", record.get("previous"))));
    }

    /** Every entry, oldest first. */
    public static List<Entry> entries(Journal journal) {
        return List.copyOf(journal.entries);
    }

    /** Entries after a sequence number, for a reader catching up. */
    public static List<Entry> since(Journal journal, int sequence) {
        Check.integer(sequence, "sequence", Bounds.atLeast(0));
        return journal.entries.stream()
                .filter(entry -> entry.sequence() > sequence)
                .toList();
    }

    /** Entries of one type, oldest first. */
    public static List<Entry> ofType(Journal journal, String type) {
        Check.nonEmptyString(type, "type");
        return journal.entries.stream()
                .filter(entry -> entry.type().equals(type))
                .toList();
    }

    /** One entry by sequence number. */
    public static Entry at(Journal journal, int sequence) {
        Check.integer(sequence, "sequence", Bounds.atLeast(1));
        if (sequence > journal.entries.size()) {
            throw new NotFoundException(
                    "the journal has no entry " + sequence,
                    Json.obj("sequence", sequence, "length", journal.entries.size()));
        }
        return journal.entries.get(sequence - 1);
    }

    /** How many entries the journal holds. */
    public static int length(Journal journal) {
        return journal.entries.size();
    }

    /** The hash of the last entry, which identifies the whole journal so far. */
    public static String head(Journal journal) {
        return journal.entries.isEmpty()
                ? GENESIS
                : journal.entries.get(journal.entries.size() - 1).hash();
    }

    /**
     * Walk the chain and report the first entry that does not follow. A journal that reads clean here
     * has not been edited after the fact.
     */
    public static Verification verify(Journal journal) {
        String previous = GENESIS;
        for (int index = 0; index < journal.entries.size(); index += 1) {
            Entry entry = journal.entries.get(index);
            if (entry.sequence() != index + 1) {
                return Verification.unsound(index + 1, "out of order");
            }
            if (!entry.previous().equals(previous)) {
                return Verification.unsound(entry.sequence(), "broken chain");
            }
            if (!entry.hash().equals(entryHash(entry))) {
                return Verification.unsound(entry.sequence(), "altered entry");
            }
            previous = entry.hash();
        }
        return Verification.sound(journal.entries.size(), previous);
    }

    /** Refuse a journal that does not verify. */
    public static Journal assertSound(Journal journal) {
        Verification result = verify(journal);
        if (!result.sound()) {
            throw new ConflictException(
                    "the journal is not sound at entry " + result.sequence().getAsInt(), (JsonObject) result.toJson());
        }
        return journal;
    }

    /** The journal as canonical JSON text, for writing to a file. */
    public static String toJson(Journal journal) {
        return Json.canonicalStringify(journal.entries);
    }

    /** Read a journal back from that form, checking the chain as it goes. */
    public static Journal fromJson(String text) {
        Json parsed = Json.parse(text);
        if (!(parsed instanceof JsonArray array)) {
            throw new InvalidArgumentException(
                    "a journal file holds an array of entries", Json.obj("got", Check.typeOf(parsed)));
        }
        return createJournal(array.items());
    }
}
