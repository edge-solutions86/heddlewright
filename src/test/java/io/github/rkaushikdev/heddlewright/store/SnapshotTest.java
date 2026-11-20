package io.github.rkaushikdev.heddlewright.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.ConflictException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SnapshotTest {

    private static Repository filled() {
        Repository repository = Repository.createRepository();
        Repository.put(repository, "looms", "L1", Json.obj("make", "dobby"));
        Repository.put(repository, "looms", "L2", Json.obj("make", "jacquard"));
        Repository.put(repository, "drafts", "D1", Json.obj("name", "twill"));
        return repository;
    }

    private static Repository other(String... references) {
        Repository repository = Repository.createRepository();
        for (String reference : references) {
            Repository.put(repository, "looms", reference, Json.obj("make", "other"));
        }
        return repository;
    }

    private static List<String> loomIds(Repository repository) {
        return Repository.list(repository, "looms").stream()
                .map(loom -> loom.get("id").asText())
                .toList();
    }

    private static JsonObject stateOf(JsonObject snapshot) {
        return (JsonObject) snapshot.get("state");
    }

    @Nested
    class TakeSnapshot {

        @Test
        void recordsTheStateTheSequenceAndTheHead() {
            JsonObject snapshot = Snapshot.takeSnapshot(filled());
            assertThat(snapshot.get("version")).isEqualTo(Json.of(Snapshot.SNAPSHOT_VERSION));
            assertThat(snapshot.get("sequence")).isEqualTo(Json.of(3));
            assertThat(stateOf(snapshot).get("looms").get("L1")).isEqualTo(Json.obj("make", "dobby"));
        }

        @Test
        void isEmptyForAFreshRepository() {
            JsonObject snapshot = Snapshot.takeSnapshot(Repository.createRepository());
            assertThat(snapshot.get("sequence")).isEqualTo(Json.of(0));
            assertThat(snapshot.get("state")).isEqualTo(Json.obj());
        }

        @Test
        void carriesADigestOverEverythingElseInIt() {
            assertThat(Snapshot.takeSnapshot(filled()).get("digest").asText()).matches("[0-9a-f]{8}");
        }
    }

    @Nested
    class AssertSnapshotSound {

        @Test
        void passesOneStraightOutOfTakeSnapshot() {
            JsonObject snapshot = Snapshot.takeSnapshot(filled());
            assertThat(Snapshot.assertSnapshotSound(snapshot)).isSameAs(snapshot);
        }

        @Test
        void catchesASnapshotEditedAfterItWasTaken() {
            JsonObject snapshot = Snapshot.takeSnapshot(filled());
            JsonObject looms = ((JsonObject) stateOf(snapshot).get("looms")).with("L1", Json.obj("make", "smuggled"));
            JsonObject edited = snapshot.with("state", stateOf(snapshot).with("looms", looms));
            assertThatThrownBy(() -> Snapshot.assertSnapshotSound(edited)).isInstanceOf(ConflictException.class);
        }

        @Test
        void refusesOneWrittenByADifferentVersion() {
            JsonObject snapshot = Snapshot.takeSnapshot(filled()).with("version", 99);
            assertThatThrownBy(() -> Snapshot.assertSnapshotSound(snapshot))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class SnapshotMatches {

        @Test
        void matchesTheJournalItWasTakenFrom() {
            Repository repository = filled();
            assertThat(Snapshot.snapshotMatches(Snapshot.takeSnapshot(repository), repository.journal()))
                    .isTrue();
        }

        @Test
        void stillMatchesOnceMoreHasBeenWritten() {
            Repository repository = filled();
            JsonObject snapshot = Snapshot.takeSnapshot(repository);
            Repository.put(repository, "looms", "L3", Json.obj("make", "tappet"));
            assertThat(Snapshot.snapshotMatches(snapshot, repository.journal())).isTrue();
        }

        @Test
        void doesNotMatchADifferentHistory() {
            JsonObject snapshot = Snapshot.takeSnapshot(filled());
            assertThat(Snapshot.snapshotMatches(
                            snapshot, other("L9", "L8", "L7").journal()))
                    .isFalse();
        }

        @Test
        void matchesAnEmptySnapshotAgainstAnything() {
            assertThat(Snapshot.snapshotMatches(
                            Snapshot.takeSnapshot(Repository.createRepository()), Journal.createJournal()))
                    .isTrue();
        }
    }

    @Nested
    class OpenFromSnapshot {

        @Test
        void comesBackWithTheSameStateAsReplayingTheWholeJournal() {
            Repository repository = filled();
            JsonObject snapshot = Snapshot.takeSnapshot(repository);
            Repository.put(repository, "looms", "L3", Json.obj("make", "tappet"));
            Repository.remove(repository, "drafts", "D1");
            Repository reopened = Snapshot.openFromSnapshot(snapshot, repository.journal());
            assertThat(Repository.counts(reopened)).isEqualTo(Repository.counts(repository));
            assertThat(loomIds(reopened)).containsExactly("L1", "L2", "L3");
        }

        @Test
        void appliesARemovalWrittenAfterTheSnapshot() {
            Repository repository = filled();
            JsonObject snapshot = Snapshot.takeSnapshot(repository);
            Repository.remove(repository, "looms", "L1");
            assertThat(loomIds(Snapshot.openFromSnapshot(snapshot, repository.journal())))
                    .containsExactly("L2");
        }

        @Test
        void refusesASnapshotFromADifferentJournal() {
            JsonObject snapshot = Snapshot.takeSnapshot(filled());
            Journal journal = other("L9").journal();
            assertThatThrownBy(() -> Snapshot.openFromSnapshot(snapshot, journal))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void refusesASnapshotThatHasBeenEdited() {
            Repository repository = filled();
            JsonObject snapshot = Snapshot.takeSnapshot(repository).with("sequence", 1);
            assertThatThrownBy(() -> Snapshot.openFromSnapshot(snapshot, repository.journal()))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class ApplyEntry {

        @Test
        void writesAPutIntoABareStateMap() {
            Repository repository = filled();
            Map<String, Map<String, Json>> state = Snapshot.applyEntry(
                    new LinkedHashMap<>(), Journal.entries(repository.journal()).get(0));
            assertThat(state.get("looms").get("L1")).isEqualTo(Json.obj("make", "dobby"));
        }

        @Test
        void leavesARemovalForACollectionNothingWroteAlone() {
            Repository repository = filled();
            Repository.remove(repository, "looms", "L1");
            List<Journal.Entry> entries = Journal.entries(repository.journal());
            Map<String, Map<String, Json>> state =
                    Snapshot.applyEntry(new LinkedHashMap<>(), entries.get(entries.size() - 1));
            assertThat(state).isEmpty();
        }
    }

    @Nested
    class EntriesSaved {

        @Test
        void countsTheEntriesASnapshotSavesReplaying() {
            Repository repository = filled();
            JsonObject snapshot = Snapshot.takeSnapshot(repository);
            Repository.put(repository, "looms", "L3", Json.obj("make", "tappet"));
            assertThat(Snapshot.entriesSaved(snapshot, repository.journal())).isEqualTo(3);
            assertThat(Journal.length(repository.journal())).isEqualTo(4);
        }
    }
}
