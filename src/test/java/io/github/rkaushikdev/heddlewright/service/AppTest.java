package io.github.rkaushikdev.heddlewright.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.heddlewright.Fixtures;
import io.github.rkaushikdev.heddlewright.service.Router.Request;
import io.github.rkaushikdev.heddlewright.service.Router.Response;
import io.github.rkaushikdev.heddlewright.store.Journal;
import io.github.rkaushikdev.heddlewright.store.Tampering;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AppTest {

    private static final JsonObject DRAFT = Fixtures.twillRecord();

    private App app;

    @BeforeEach
    void open() {
        app = App.createApp();
    }

    private Json body(String path) {
        return app.request(Request.get(path)).body();
    }

    private void postDraft() {
        app.request(Request.of("POST", "/drafts", DRAFT));
    }

    private static int size(Json value) {
        return ((JsonArray) value).size();
    }

    @Nested
    class GetHealth {

        @Test
        void reportsAnEmptyService() {
            Json health = body("/health");
            assertThat(health.get("ok")).isEqualTo(Json.of(true));
            assertThat(health.get("version")).isEqualTo(Json.of(App.VERSION));
            assertThat(health.get("entries")).isEqualTo(Json.of(0));
            assertThat(health.get("records")).isEqualTo(Json.obj());
        }

        @Test
        void movesTheHeadOnWithEveryWrite() {
            Json before = body("/health").get("head");
            postDraft();
            Json after = body("/health").get("head");
            assertThat(after).isNotEqualTo(before);
            assertThat(body("/health").get("entries")).isEqualTo(Json.of(1));
        }

        @Test
        void countsTheRecordsByCollection() {
            postDraft();
            assertThat(body("/health").get("records")).isEqualTo(Json.obj("drafts", 1));
        }
    }

    @Nested
    class TheRouteTable {

        @Test
        void mountsEveryPartOfTheService() {
            assertThat(App.routeTable(app)).contains("GET /drafts", "GET /pieces", "POST /plan", "GET /health");
        }

        @Test
        void declaresNoRouteTwice() {
            assertThat(App.duplicateRoutes(app)).isEmpty();
        }

        @Test
        void servesItsOwnTable() {
            assertThat(size(body("/routes"))).isEqualTo(App.routeTable(app).size());
        }

        @Test
        void answersAWrongMethodWithTheMethodsThatPathDoesTake() {
            Response response = app.request(Request.of("POST", "/health"));
            assertThat(response.status()).isEqualTo(405);
            assertThat(response.body().get("allowed")).isEqualTo(Json.arr("GET"));
        }

        @Test
        void answersAPathNobodyDeclaredWithTheOnesItKnows() {
            Response response = app.request(Request.get("/nothing"));
            assertThat(response.status()).isEqualTo(404);
            assertThat(((JsonArray) response.body().get("known")).items()).contains(Json.of("/health"));
        }
    }

    @Nested
    class GetJournal {

        @Test
        void listsWhatHasBeenWrittenWithoutThePayloads() {
            postDraft();
            Json listed = body("/journal");
            assertThat(size(listed)).isEqualTo(1);
            assertThat(listed.at(0).get("type")).isEqualTo(Json.of("record.put"));
            assertThat(listed.at(0).get("payload").isMissing()).isTrue();
        }

        @Test
        void catchesAReaderUpFromASequenceNumber() {
            postDraft();
            assertThat(app.request(Request.get("/journal", Map.of("since", "1")))
                            .body())
                    .isEqualTo(Json.arr());
        }

        @Test
        void treatsAnUnreadableSinceAsTheBeginning() {
            postDraft();
            assertThat(size(app.request(Request.get("/journal", Map.of("since", "lots")))
                            .body()))
                    .isEqualTo(1);
        }
    }

    @Nested
    class GetJournalVerify {

        @Test
        void readsAFreshServiceAsSound() {
            postDraft();
            Json verified = body("/journal/verify");
            assertThat(verified.get("sound")).isEqualTo(Json.of(true));
            assertThat(verified.get("entries")).isEqualTo(Json.of(1));
        }

        @Test
        void spotsAJournalThatHasBeenMeddledWith() {
            postDraft();
            Tampering.retype(app.repository().journal(), 0, "record.removed");
            assertThat(body("/journal/verify").get("sound")).isEqualTo(Json.of(false));
        }
    }

    @Nested
    class ReopeningFromAJournal {

        @Test
        void comesBackWithTheSameState() {
            postDraft();
            String saved = Journal.toJson(app.repository().journal());
            App reopened = App.createApp(Journal.fromJson(saved));
            Json drafts = reopened.request(Request.get("/drafts")).body();
            assertThat(size(drafts)).isEqualTo(1);
            assertThat(drafts.at(0).get("name")).isEqualTo(Json.of("four shaft twill"));
        }

        @Test
        void comesBackWithTheSameHead() {
            postDraft();
            App reopened = App.createApp(
                    Journal.fromJson(Journal.toJson(app.repository().journal())));
            assertThat(reopened.request(Request.get("/health")).body().get("head"))
                    .isEqualTo(body("/health").get("head"));
        }

        @Test
        void startsEmptyOnAFreshJournal() {
            App fresh = App.createApp(Journal.createJournal());
            assertThat(fresh.request(Request.get("/drafts")).body()).isEqualTo(Json.arr());
        }
    }

    @Nested
    class TheVersionTheServiceReports {

        @Test
        void isTheVersionThePackageDeclares() throws IOException {
            // Two places name the version and neither reads the other at run time, so this is the
            // thing that keeps them from drifting apart. The build's own version is the first
            // <version> after the artifact id.
            String pom = Files.readString(Path.of("pom.xml"));
            Matcher found = Pattern.compile("<artifactId>heddlewright</artifactId>\\s*<version>([^<]+)</version>")
                    .matcher(pom);
            assertThat(found.find()).isTrue();
            assertThat(App.VERSION).isEqualTo(found.group(1));
        }

        @Test
        void readsLikeAVersionRatherThanAWord() {
            assertThat(App.VERSION).matches("\\d+\\.\\d+\\.\\d+");
        }
    }
}
