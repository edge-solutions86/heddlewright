package io.github.rkaushikdev.heddlewright.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.store.Journal.Entry;
import io.github.rkaushikdev.heddlewright.util.ConflictException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JournalTest {

    private static Journal filled() {
        Journal journal = Journal.createJournal();
        Journal.append(journal, "draft.created", Json.obj("id", "draft-1"));
        Journal.append(journal, "draft.revised", Json.obj("id", "draft-1", "shafts", 8));
        Journal.append(journal, "piece.opened", Json.obj("reference", "P1"));
        return journal;
    }

    private static Entry withPayload(Entry entry, Json payload) {
        return new Entry(entry.sequence(), entry.type(), payload, entry.at(), entry.previous(), entry.hash());
    }

    /** The journal file text with one entry edited by hand, the way a meddler would. */
    private static String edited(Journal journal, int index, JsonObject entry) {
        List<Json> items = new ArrayList<>(((JsonArray) Json.parse(Journal.toJson(journal))).items());
        items.set(index, entry);
        return Json.stringify(items);
    }

    @Nested
    class Append {

        @Test
        void numbersEntriesFromOneAndChainsThem() {
            Journal journal = filled();
            assertThat(Journal.length(journal)).isEqualTo(3);
            assertThat(Journal.entries(journal).get(0).sequence()).isEqualTo(1);
            assertThat(Journal.entries(journal).get(0).previous()).isEqualTo(Journal.GENESIS);
            assertThat(Journal.entries(journal).get(1).previous())
                    .isEqualTo(Journal.entries(journal).get(0).hash());
        }

        @Test
        void freezesWhatItWrites() {
            Entry entry = Journal.append(Journal.createJournal(), "x");
            // A record has no setters and a JSON payload cannot be edited, so nothing written can change.
            assertThat(entry.getClass().isRecord()).isTrue();
            assertThatThrownBy(() -> ((JsonObject) entry.payload()).asMap().put("x", Json.NULL))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void defaultsThePayloadAndTheTime() {
            Entry entry = Journal.append(Journal.createJournal(), "x");
            assertThat(entry.payload()).isEqualTo(Json.obj());
            assertThat(entry.at()).isEqualTo(Json.NULL);
        }

        @Test
        void keepsATimeWhenOneIsGiven() {
            Entry entry = Journal.append(Journal.createJournal(), "x", Json.obj(), "2025-04-01T09:00:00Z");
            assertThat(entry.at()).isEqualTo(Json.of("2025-04-01T09:00:00Z"));
        }

        @Test
        void refusesAnEntryWithNoType() {
            assertThatThrownBy(() -> Journal.append(Journal.createJournal(), ""))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class Reading {

        private final Journal journal = filled();

        @Test
        void givesEveryEntryOldestFirst() {
            assertThat(Journal.entries(journal))
                    .extracting(Entry::type)
                    .containsExactly("draft.created", "draft.revised", "piece.opened");
        }

        @Test
        void catchesAReaderUpFromWhereItLeftOff() {
            assertThat(Journal.since(journal, 1)).extracting(Entry::sequence).containsExactly(2, 3);
            assertThat(Journal.since(journal, 3)).isEmpty();
            assertThat(Journal.since(journal, 0)).hasSize(3);
        }

        @Test
        void filtersByType() {
            assertThat(Journal.ofType(journal, "draft.created")).hasSize(1);
            assertThat(Journal.ofType(journal, "nothing.happened")).isEmpty();
        }

        @Test
        void findsOneEntryByNumberAndRefusesOnePastTheEnd() {
            assertThat(Journal.at(journal, 2).type()).isEqualTo("draft.revised");
            assertThatThrownBy(() -> Journal.at(journal, 9)).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> Journal.at(journal, 0)).isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void namesTheHeadAsTheLastHashAndGenesisWhenEmpty() {
            assertThat(Journal.head(journal))
                    .isEqualTo(Journal.entries(journal).get(2).hash());
            assertThat(Journal.head(Journal.createJournal())).isEqualTo(Journal.GENESIS);
        }
    }

    @Nested
    class Verify {

        @Test
        void readsAFreshJournalAsSound() {
            Journal.Verification full = Journal.verify(filled());
            assertThat(full.sound()).isTrue();
            assertThat(full.entries()).hasValue(3);
            assertThat(Journal.verify(Journal.createJournal()).entries()).hasValue(0);
        }

        @Test
        void spotsAnEntryAlteredAfterItWasWritten() {
            Journal journal = filled();
            List<Entry> list = journal.entryList();
            list.set(1, withPayload(list.get(1), Json.obj("id", "draft-1", "shafts", 16)));
            Journal.Verification result = Journal.verify(journal);
            assertThat(result.sound()).isFalse();
            assertThat(result.sequence()).hasValue(2);
            assertThat(result.reason()).contains("altered entry");
        }

        @Test
        void spotsABrokenChain() {
            Journal journal = filled();
            List<Entry> list = journal.entryList();
            list.remove(1);
            Entry moved = list.get(1);
            list.set(1, new Entry(2, moved.type(), moved.payload(), moved.at(), moved.previous(), moved.hash()));
            assertThat(Journal.verify(journal).sound()).isFalse();
        }

        @Test
        void spotsEntriesOutOfOrder() {
            Journal journal = filled();
            Collections.reverse(journal.entryList());
            Journal.Verification result = Journal.verify(journal);
            assertThat(result.sound()).isFalse();
            assertThat(result.reason()).contains("out of order");
        }

        @Test
        void assertSoundPassesAGoodJournalAndThrowsOnABadOne() {
            Journal journal = filled();
            assertThat(Journal.assertSound(journal)).isSameAs(journal);
            Entry first = journal.entryList().get(0);
            journal.entryList()
                    .set(
                            0,
                            new Entry(
                                    first.sequence(),
                                    "draft.deleted",
                                    first.payload(),
                                    first.at(),
                                    first.previous(),
                                    first.hash()));
            assertThatThrownBy(() -> Journal.assertSound(journal)).isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class EntryHash {

        private final JsonObject base = Json.obj(
                "sequence", 1, "type", "a", "payload", Json.obj(), "at", Json.NULL, "previous", Journal.GENESIS);

        @Test
        void changesWithEveryFieldItCovers() {
            String hash = Journal.entryHash(base);
            assertThat(Journal.entryHash(base.with("type", "b"))).isNotEqualTo(hash);
            assertThat(Journal.entryHash(base.with("payload", Json.obj("x", 1))))
                    .isNotEqualTo(hash);
            assertThat(Journal.entryHash(base.with("previous", "aaaaaaaa"))).isNotEqualTo(hash);
        }

        @Test
        void doesNotChangeWhenAPayloadKeyMoves() {
            assertThat(Journal.entryHash(base.with("payload", Json.obj("a", 1, "b", 2))))
                    .isEqualTo(Journal.entryHash(base.with("payload", Json.obj("b", 2, "a", 1))));
        }
    }

    @Nested
    class ToJsonAndFromJson {

        @Test
        void roundTripsAJournal() {
            Journal journal = filled();
            Journal back = Journal.fromJson(Journal.toJson(journal));
            assertThat(Journal.length(back)).isEqualTo(3);
            assertThat(Journal.head(back)).isEqualTo(Journal.head(journal));
        }

        @Test
        void refusesAFileWhoseEntriesHaveBeenMeddledWith() {
            Journal journal = filled();
            JsonObject second = (JsonObject) Json.parse(Journal.toJson(journal)).at(1);
            JsonObject payload = ((JsonObject) second.get("payload")).with("shafts", 16);
            String tampered = edited(journal, 1, second.with("payload", payload));
            assertThatThrownBy(() -> Journal.fromJson(tampered)).hasMessageContaining("altered since it was written");
        }

        @Test
        void refusesAFileWithTheEntriesReordered() {
            List<Json> items = new ArrayList<>(((JsonArray) Json.parse(Journal.toJson(filled()))).items());
            Collections.reverse(items);
            assertThatThrownBy(() -> Journal.fromJson(Json.stringify(items))).isInstanceOf(ConflictException.class);
        }

        @Test
        void refusesAFileThatIsNotAnArray() {
            assertThatThrownBy(() -> Journal.fromJson("{}")).isInstanceOf(InvalidArgumentException.class);
        }
    }
}
