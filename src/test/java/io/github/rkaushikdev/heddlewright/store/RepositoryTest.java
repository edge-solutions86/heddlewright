package io.github.rkaushikdev.heddlewright.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.ConflictException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RepositoryTest {

    private static Repository filled() {
        Repository repository = Repository.createRepository();
        Repository.put(repository, "looms", "L1", Json.obj("make", "dobby", "shafts", 16));
        Repository.put(repository, "looms", "L2", Json.obj("make", "jacquard", "hooks", 400));
        Repository.put(repository, "drafts", "D1", Json.obj("name", "twill"));
        return repository;
    }

    private static List<String> ids(List<JsonObject> records) {
        return records.stream().map(record -> record.get("id").asText()).toList();
    }

    private static Journal.Entry last(Repository repository) {
        List<Journal.Entry> entries = Journal.entries(repository.journal());
        return entries.get(entries.size() - 1);
    }

    @Nested
    class PutAndGet {

        @Test
        void storesARecordAndReadsItBack() {
            assertThat(Repository.get(filled(), "looms", "L1")).isEqualTo(Json.obj("make", "dobby", "shafts", 16));
        }

        @Test
        void recordsTheWriteInTheJournal() {
            Repository repository = filled();
            assertThat(Journal.length(repository.journal())).isEqualTo(3);
            assertThat(Journal.entries(repository.journal()).get(0).type()).isEqualTo(Repository.PUT);
        }

        @Test
        void freezesTheStoredRecordAndCopiesItSoTheCallerCannotReachIn() {
            Repository repository = Repository.createRepository();
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("make", "dobby");
            Json stored = Repository.put(repository, "looms", "L1", record);
            record.put("make", "jacquard");
            assertThatThrownBy(() -> ((JsonObject) stored).asMap().put("make", Json.NULL))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(Repository.get(repository, "looms", "L1").get("make")).isEqualTo(Json.of("dobby"));
        }

        @Test
        void overwritesOnASecondPut() {
            Repository repository = filled();
            Repository.put(repository, "looms", "L1", Json.obj("make", "shuttleless"));
            assertThat(Repository.get(repository, "looms", "L1")).isEqualTo(Json.obj("make", "shuttleless"));
        }

        @Test
        void saysPlainlyWhenARecordIsNotThere() {
            assertThatThrownBy(() -> Repository.get(filled(), "looms", "L9")).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> Repository.get(filled(), "nothing", "L1")).isInstanceOf(NotFoundException.class);
        }

        @Test
        void refusesARecordThatIsNotAnObject() {
            assertThatThrownBy(() -> Repository.put(Repository.createRepository(), "looms", "L1", "dobby"))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class Insert {

        @Test
        void writesWhenNothingIsThere() {
            Json stored = Repository.insert(Repository.createRepository(), "looms", "L1", Json.obj("make", "dobby"));
            assertThat(stored.get("make")).isEqualTo(Json.of("dobby"));
        }

        @Test
        void refusesToOverwrite() {
            assertThatThrownBy(() -> Repository.insert(filled(), "looms", "L1", Json.obj("make", "other")))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class FindAndHas {

        @Test
        void answerWithoutThrowing() {
            Repository repository = filled();
            assertThat(Repository.find(repository, "looms", "L1")).isPresent();
            assertThat(Repository.find(repository, "looms", "L9")).isEmpty();
            assertThat(Repository.has(repository, "looms", "L1")).isTrue();
            assertThat(Repository.has(repository, "looms", "L9")).isFalse();
        }
    }

    @Nested
    class ListAndWhere {

        @Test
        void listsACollectionInIdOrderWithTheIdFoldedIn() {
            assertThat(ids(Repository.list(filled(), "looms"))).containsExactly("L1", "L2");
            assertThat(Repository.list(filled(), "looms").get(0).get("make")).isEqualTo(Json.of("dobby"));
        }

        @Test
        void isEmptyForACollectionNothingWasWrittenTo() {
            assertThat(Repository.list(filled(), "nothing")).isEmpty();
        }

        @Test
        void filters() {
            assertThat(Repository.where(
                            filled(), "looms", loom -> loom.get("make").equals(Json.of("jacquard"))))
                    .hasSize(1);
        }
    }

    @Nested
    class Remove {

        @Test
        void takesTheRecordOutAndJournalsTheRemoval() {
            Repository repository = filled();
            Repository.remove(repository, "looms", "L1");
            assertThat(Repository.has(repository, "looms", "L1")).isFalse();
            assertThat(last(repository).type()).isEqualTo(Repository.REMOVE);
        }

        @Test
        void refusesToRemoveWhatIsNotThere() {
            assertThatThrownBy(() -> Repository.remove(filled(), "looms", "L9")).isInstanceOf(NotFoundException.class);
        }

        @Test
        void leavesNoTombstoneInTheProjection() {
            Repository repository = filled();
            Repository.remove(repository, "looms", "L1");
            assertThat(ids(Repository.list(repository, "looms"))).containsExactly("L2");
        }
    }

    @Nested
    class Project {

        @Test
        void rebuildsTheSameStateFromTheJournalAlone() {
            Repository repository = filled();
            Repository.remove(repository, "looms", "L2");
            Repository.put(repository, "drafts", "D1", Json.obj("name", "satin"));
            Map<String, Map<String, Json>> rebuilt = Repository.project(repository.journal());
            assertThat(rebuilt.get("looms").keySet()).containsExactly("L1");
            assertThat(rebuilt.get("drafts").get("D1")).isEqualTo(Json.obj("name", "satin"));
        }

        @Test
        void isTheSameAfterARoundTripThroughTheJournalFile() {
            Repository repository = filled();
            Repository reopened = Repository.createRepository(Journal.fromJson(Journal.toJson(repository.journal())));
            assertThat(Repository.counts(reopened)).isEqualTo(Repository.counts(repository));
        }

        @Test
        void isEmptyForAnEmptyJournal() {
            assertThat(Repository.project(Journal.createJournal())).isEmpty();
        }
    }

    @Nested
    class CollectionsAndCounts {

        @Test
        void nameTheCollectionsHoldingAnything() {
            assertThat(Repository.collections(filled())).containsExactly("drafts", "looms");
            assertThat(Repository.counts(filled())).isEqualTo(Json.obj("drafts", 1, "looms", 2));
        }

        @Test
        void dropsACollectionOnceItsLastRecordGoes() {
            Repository repository = filled();
            Repository.remove(repository, "drafts", "D1");
            assertThat(Repository.collections(repository)).containsExactly("looms");
        }
    }

    @Nested
    class ReconcileWithJournal {

        @Test
        void agreesOnARepositoryThatHasOnlyBeenWrittenThrough() {
            assertThat(Repository.reconcileWithJournal(filled()))
                    .isEqualTo(new Repository.Reconciliation(true, List.of()));
        }

        @Test
        void catchesARecordWrittenStraightIntoTheState() {
            Repository repository = filled();
            repository.state().get("looms").put("L3", Json.obj("make", "smuggled"));
            Repository.Reconciliation result = Repository.reconcileWithJournal(repository);
            assertThat(result.agrees()).isFalse();
            assertThat(result.differences()).containsExactly(new Repository.Difference("looms", "L3"));
        }

        @Test
        void catchesARecordEditedBehindTheJournal() {
            Repository repository = filled();
            repository.state().get("looms").put("L1", Json.obj("make", "changed"));
            assertThat(Repository.reconcileWithJournal(repository).agrees()).isFalse();
        }
    }
}
