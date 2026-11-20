package io.github.rkaushikdev.heddlewright.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.heddlewright.util.Json;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * A journal file written in the stored format loads, verifies and projects. The file and its head
 * hash were produced by an independent implementation of the format (FNV-1a over the UTF-8 of each
 * entry's canonical JSON), so this pins the format itself rather than whatever this code happens to
 * write.
 */
class JournalFileTest {

    private static final String HEAD = "c9bb0d0e";

    private static String fixture() throws IOException {
        try (InputStream in = JournalFileTest.class.getResourceAsStream("journal-v1.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void loadsAStoredJournalAndReadsTheSameHead() throws IOException {
        Journal journal = Journal.fromJson(fixture());
        assertThat(Journal.length(journal)).isEqualTo(4);
        assertThat(Journal.verify(journal).sound()).isTrue();
        assertThat(Journal.head(journal)).isEqualTo(HEAD);
    }

    @Test
    void writesTheStoredJournalBackByteForByte() throws IOException {
        String text = fixture();
        assertThat(Journal.toJson(Journal.fromJson(text))).isEqualTo(text);
    }

    @Test
    void projectsTheStoredRecordsIncludingARemoval() throws IOException {
        Repository repository = Repository.createRepository(Journal.fromJson(fixture()));
        assertThat(Repository.counts(repository)).isEqualTo(Json.obj("drafts", 1, "looms", 1));
        // The name is written with a precomposed U+00C9, which the hash covers as two UTF-8 bytes.
        assertThat(Repository.get(repository, "drafts", "D1").get("name"))
                .isEqualTo(Json.of((char) 0xc9 + "cru twill"));
        assertThat(Repository.get(repository, "drafts", "D1").get("ratio")).isEqualTo(Json.of(0.5));
        assertThat(Repository.has(repository, "looms", "L2")).isFalse();
    }

    @Test
    void carriesTheTimesTheEntriesWereWrittenAt() throws IOException {
        Journal journal = Journal.fromJson(fixture());
        assertThat(Journal.at(journal, 1).at()).isEqualTo(Json.of("2025-04-01T09:00:00Z"));
        assertThat(Journal.at(journal, 3).at()).isEqualTo(Json.NULL);
    }
}
