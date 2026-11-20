package io.github.rkaushikdev.heddlewright.store;

import io.github.rkaushikdev.heddlewright.store.Journal.Entry;
import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.ConflictException;
import io.github.rkaushikdev.heddlewright.util.Ids;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Snapshots.
 *
 * <p>Replaying a long journal to answer one question is wasteful, so the state is written out at a
 * sequence number and read back with only the entries after it replayed on top. The snapshot carries
 * the journal head it was taken at, and loading refuses a journal that does not still contain that
 * entry, because a snapshot taken from a different history is worse than no snapshot at all.
 *
 * <p>A snapshot is a JSON record: {@code version}, {@code sequence}, {@code head}, {@code state}
 * (collection to id to record) and a {@code digest}, the FNV-1a hash of the canonical JSON of the
 * rest, so it can be written to a file and checked when it is read back.
 */
public final class Snapshot {

    /** The snapshot format this code writes and reads. */
    public static final int SNAPSHOT_VERSION = 1;

    private Snapshot() {}

    /** Take a snapshot of a repository as it stands. */
    public static JsonObject takeSnapshot(Repository repository) {
        Check.object(repository, "repository");
        Map<String, Object> state = new LinkedHashMap<>();
        repository.state().forEach((collection, records) -> {
            Map<String, Object> copies = new LinkedHashMap<>();
            records.forEach((id, record) -> copies.put(id, Json.canonicalClone(record)));
            state.put(collection, copies);
        });
        JsonObject body = Json.obj(
                "version",
                SNAPSHOT_VERSION,
                "sequence",
                Journal.length(repository.journal()),
                "head",
                Journal.head(repository.journal()),
                "state",
                state);
        return body.with("digest", Ids.stableHash(Json.canonicalStringify(body)));
    }

    /** Refuse a snapshot that has been edited since it was taken. */
    public static <T> T assertSnapshotSound(T snapshot) {
        JsonObject record = Check.plainObject(snapshot, "snapshot");
        if (!record.get("version").equals(Json.of(SNAPSHOT_VERSION))) {
            throw new InvalidArgumentException(
                    "that snapshot was written by a different version",
                    Json.obj("version", record.get("version"), "expected", SNAPSHOT_VERSION));
        }
        JsonObject body = record.without("digest");
        if (!Json.of(Ids.stableHash(Json.canonicalStringify(body))).equals(record.get("digest"))) {
            throw new ConflictException(
                    "the snapshot has been altered since it was taken", Json.obj("sequence", record.get("sequence")));
        }
        return snapshot;
    }

    /** Whether a journal still contains the history a snapshot was taken from. */
    public static boolean snapshotMatches(Object snapshot, Journal journal) {
        JsonObject record = Check.plainObject(snapshot, "snapshot");
        double sequence = record.get("sequence").asDouble();
        if (Journal.length(journal) < sequence) {
            return false;
        }
        if (sequence == 0) {
            return true;
        }
        Entry taken = Journal.entries(journal).get((int) sequence - 1);
        return Json.of(taken.hash()).equals(record.get("head"));
    }

    /**
     * Open a repository from a snapshot and the entries written after it. The result has the same
     * state as replaying the whole journal.
     */
    public static Repository openFromSnapshot(Object snapshot, Journal journal) {
        JsonObject record = assertSnapshotSound(Check.plainObject(snapshot, "snapshot"));
        if (!snapshotMatches(record, journal)) {
            throw new ConflictException(
                    "that snapshot was taken from a different journal",
                    Json.obj("sequence", record.get("sequence"), "head", record.get("head")));
        }
        Map<String, Map<String, Json>> state = new LinkedHashMap<>();
        for (Map.Entry<String, Json> collection :
                Check.plainObject(record.get("state"), "snapshot.state").entries()) {
            Map<String, Json> records = new LinkedHashMap<>();
            for (Map.Entry<String, Json> entry :
                    Check.plainObject(collection.getValue(), "snapshot.state").entries()) {
                records.put(entry.getKey(), entry.getValue());
            }
            state.put(collection.getKey(), records);
        }
        // The entries after the snapshot are applied here rather than replayed through a fresh
        // journal: they carry the chain they were written with, which starts part way through, so a
        // new journal would refuse the first of them.
        for (Entry entry : Journal.since(journal, Check.integer(record.get("sequence"), "snapshot.sequence"))) {
            applyEntry(state, entry);
        }
        return new Repository(journal, state);
    }

    /** Apply one journal entry to a state map, the same way the projection does. */
    public static Map<String, Map<String, Json>> applyEntry(Map<String, Map<String, Json>> state, Entry entry) {
        Repository.apply(state, entry);
        return state;
    }

    /** How many entries a snapshot saves replaying. */
    public static int entriesSaved(Object snapshot, Journal journal) {
        JsonObject record = Check.plainObject(snapshot, "snapshot");
        int sequence = Check.integer(record.get("sequence"), "snapshot.sequence", Bounds.atLeast(0));
        return Math.max(0, Math.min(sequence, Journal.length(journal)));
    }
}
