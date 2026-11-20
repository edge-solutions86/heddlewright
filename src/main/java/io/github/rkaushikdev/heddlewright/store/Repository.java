package io.github.rkaushikdev.heddlewright.store;

import io.github.rkaushikdev.heddlewright.store.Journal.Entry;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Collation;
import io.github.rkaushikdev.heddlewright.util.ConflictException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A repository over the journal.
 *
 * <p>Records are never edited. Putting a record appends a {@code put} event, removing one appends a
 * {@code remove}, and the current state is the projection of the whole journal. That means the
 * state can always be rebuilt from the journal alone, and the projection is a pure function, so
 * replaying it twice cannot drift.
 */
public final class Repository {

    /** The event a write appends. */
    public static final String PUT = "record.put";

    /** The event a removal appends. */
    public static final String REMOVE = "record.removed";

    /** One record the kept state and the journal disagree about. */
    public record Difference(String collection, String id) {}

    /** Whether the kept state agrees with a fresh projection of the journal, and where it does not. */
    public record Reconciliation(boolean agrees, List<Difference> differences) {
        /** Copies the differences. */
        public Reconciliation {
            differences = List.copyOf(differences);
        }
    }

    private final Journal journal;
    private final Map<String, Map<String, Json>> state;

    Repository(Journal journal, Map<String, Map<String, Json>> state) {
        this.journal = journal;
        this.state = state;
    }

    /** The journal the repository writes to. */
    public Journal journal() {
        return journal;
    }

    /** The kept state, collection by collection, which only this package may change. */
    Map<String, Map<String, Json>> state() {
        return state;
    }

    /** Open a repository over a new, empty journal. */
    public static Repository createRepository() {
        return createRepository(Journal.createJournal());
    }

    /** Open a repository over a journal, projecting whatever is already there. */
    public static Repository createRepository(Journal journal) {
        return new Repository(journal, project(journal));
    }

    /**
     * Rebuild the current records from the journal. Later entries win, and a removal takes the record
     * out entirely rather than leaving a tombstone in the view.
     */
    public static Map<String, Map<String, Json>> project(Journal journal) {
        Map<String, Map<String, Json>> state = new LinkedHashMap<>();
        for (Entry entry : Journal.entries(journal)) {
            apply(state, entry);
        }
        return state;
    }

    /**
     * Apply one entry to a state, a put writing its record and a removal taking one out. A
     * collection or id that is not text is filed under its text.
     */
    static void apply(Map<String, Map<String, Json>> state, Entry entry) {
        Json payload = entry.payload();
        String collection = Text.string(payload.get("collection"));
        String id = Text.string(payload.get("id"));
        if (entry.type().equals(PUT)) {
            state.computeIfAbsent(collection, name -> new LinkedHashMap<>())
                    .put(id, Json.canonicalClone(payload.get("record")));
            return;
        }
        if (entry.type().equals(REMOVE)) {
            Map<String, Json> records = state.get(collection);
            if (records != null) {
                records.remove(id);
            }
        }
    }

    /** Write a record with no time on its entry. */
    public static Json put(Repository repository, String collection, String id, Object record) {
        return put(repository, collection, id, record, Json.NULL);
    }

    /** Write a record, appending the event that makes it so, and return the stored copy. */
    public static Json put(Repository repository, String collection, String id, Object record, Object at) {
        Check.nonEmptyString(collection, "collection");
        Check.nonEmptyString(id, "id");
        JsonObject written = Check.plainObject(record, "record");
        Journal.append(repository.journal, PUT, Json.obj("collection", collection, "id", id, "record", written), at);
        Json stored = Json.canonicalClone(written);
        repository
                .state
                .computeIfAbsent(collection, name -> new LinkedHashMap<>())
                .put(id, stored);
        return stored;
    }

    /** Write a record only if nothing is there, which is how a create is done. */
    public static Json insert(Repository repository, String collection, String id, Object record) {
        return insert(repository, collection, id, record, Json.NULL);
    }

    /** Write a record only if nothing is there, with a time on its entry. */
    public static Json insert(Repository repository, String collection, String id, Object record, Object at) {
        if (has(repository, collection, id)) {
            throw new ConflictException(
                    collection + "/" + id + " already exists", Json.obj("collection", collection, "id", id));
        }
        return put(repository, collection, id, record, at);
    }

    /** Read a record, or say plainly that it is not there. */
    public static Json get(Repository repository, String collection, String id) {
        return find(repository, collection, id)
                .orElseThrow(() -> new NotFoundException(
                        "no " + collection + " called \"" + id + "\"", Json.obj("collection", collection, "id", id)));
    }

    /** Read a record, or nothing. */
    public static Optional<Json> find(Repository repository, String collection, String id) {
        Map<String, Json> records = repository.state.get(collection);
        return records == null ? Optional.empty() : Optional.ofNullable(records.get(id));
    }

    /** Whether a record is there. */
    public static boolean has(Repository repository, String collection, String id) {
        Map<String, Json> records = repository.state.get(collection);
        return records != null && records.containsKey(id);
    }

    /** Every record in a collection, in id order, each with its id laid in first. */
    public static List<JsonObject> list(Repository repository, String collection) {
        Map<String, Json> records = repository.state.get(collection);
        if (records == null) {
            return List.of();
        }
        List<Map.Entry<String, Json>> sorted = new ArrayList<>(records.entrySet());
        sorted.sort(Map.Entry.comparingByKey(Collation.ORDER));
        List<JsonObject> out = new ArrayList<>(sorted.size());
        for (Map.Entry<String, Json> entry : sorted) {
            JsonObject withId = Json.obj("id", entry.getKey());
            out.add(entry.getValue() instanceof JsonObject record ? withId.merge(record) : withId);
        }
        return List.copyOf(out);
    }

    /** Every record matching a test. */
    public static List<JsonObject> where(Repository repository, String collection, Predicate<JsonObject> test) {
        return list(repository, collection).stream().filter(test).toList();
    }

    /** Remove a record, with no time on its entry. */
    public static boolean remove(Repository repository, String collection, String id) {
        return remove(repository, collection, id, Json.NULL);
    }

    /** Remove a record, appending the event that removes it. */
    public static boolean remove(Repository repository, String collection, String id, Object at) {
        if (!has(repository, collection, id)) {
            throw new NotFoundException(
                    "no " + collection + " called \"" + id + "\"", Json.obj("collection", collection, "id", id));
        }
        Journal.append(repository.journal, REMOVE, Json.obj("collection", collection, "id", id), at);
        repository.state.get(collection).remove(id);
        return true;
    }

    /** The collections holding anything at all, in name order. */
    public static List<String> collections(Repository repository) {
        return repository.state.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /** How many records each collection holds. */
    public static JsonObject counts(Repository repository) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String name : collections(repository)) {
            out.put(name, repository.state.get(name).size());
        }
        return (JsonObject) Json.of(out);
    }

    /**
     * Rebuild the state from the journal and compare it with the state being kept alongside. They
     * should always agree, and a difference means something has written to the state without going
     * through the journal.
     */
    public static Reconciliation reconcileWithJournal(Repository repository) {
        Map<String, Map<String, Json>> rebuilt = project(repository.journal);
        List<Difference> differences = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>(rebuilt.keySet());
        names.addAll(repository.state.keySet());
        for (String name : names) {
            Map<String, Json> left = rebuilt.getOrDefault(name, Map.of());
            Map<String, Json> right = repository.state.getOrDefault(name, Map.of());
            Set<String> ids = new LinkedHashSet<>(left.keySet());
            ids.addAll(right.keySet());
            for (String id : ids) {
                if (!Json.stringify(left.getOrDefault(id, Json.MISSING))
                        .equals(Json.stringify(right.getOrDefault(id, Json.MISSING)))) {
                    differences.add(new Difference(name, id));
                }
            }
        }
        return new Reconciliation(differences.isEmpty(), differences);
    }
}
