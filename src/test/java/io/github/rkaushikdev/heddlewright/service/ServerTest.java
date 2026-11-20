package io.github.rkaushikdev.heddlewright.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.heddlewright.Fixtures;
import io.github.rkaushikdev.heddlewright.util.Json;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServerTest {

    private static final String DRAFT = Json.stringify(Fixtures.twillRecord());

    private final HttpClient client = HttpClient.newHttpClient();
    private Server running;

    @AfterEach
    void stop() {
        if (running != null) {
            Server.close(running);
            running = null;
        }
    }

    private String start() throws IOException {
        running = Server.createServer(App.createApp());
        return "http://127.0.0.1:" + Server.listen(running);
    }

    private HttpResponse<String> send(String method, String url, String body) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publisher =
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .method(method, publisher)
                .header("content-type", "application/json")
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> fetch(String url) throws IOException, InterruptedException {
        return send("GET", url, null);
    }

    private String postDraft(String base) throws IOException, InterruptedException {
        return Json.parse(send("POST", base + "/drafts", DRAFT).body())
                .get("id")
                .asText();
    }

    @Nested
    class SplitUrl {

        @Test
        void splitsAPathFromItsQuery() {
            assertThat(Server.splitUrl("/drafts?rows=text"))
                    .isEqualTo(new Server.Url("/drafts", Map.of("rows", "text")));
        }

        @Test
        void hasAnEmptyQueryWhenThereIsNone() {
            assertThat(Server.splitUrl("/drafts")).isEqualTo(new Server.Url("/drafts", Map.of()));
        }

        @Test
        void readsAKeyWithNoValueAsAnEmptyString() {
            assertThat(Server.splitUrl("/x?flag").query()).isEqualTo(Map.of("flag", ""));
        }

        @Test
        void decodesEscapesAndReadsAPlusAsASpace() {
            assertThat(Server.splitUrl("/x?name=four+shaft%20twill").query().get("name"))
                    .isEqualTo("four shaft twill");
        }

        @Test
        void ignoresAnEmptyPair() {
            assertThat(Server.splitUrl("/x?&a=1").query()).isEqualTo(Map.of("a", "1"));
        }
    }

    @Nested
    class TheRunningServer {

        @Test
        void answersHealthOverHttp() throws Exception {
            HttpResponse<String> response = fetch(start() + "/health");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(Json.parse(response.body()).get("ok")).isEqualTo(Json.of(true));
        }

        @Test
        void takesAPostedDraftAndReadsItBack() throws Exception {
            String base = start();
            assertThat(send("POST", base + "/drafts", DRAFT).statusCode()).isEqualTo(201);
            assertThat(Json.parse(fetch(base + "/drafts").body()).at(0).isMissing())
                    .isFalse();
            assertThat(Json.parse(fetch(base + "/drafts").body()).at(1).isMissing())
                    .isTrue();
        }

        @Test
        void passesTheQueryStringThroughToTheHandler() throws Exception {
            String base = start();
            String id = postDraft(base);
            Json cloth = Json.parse(
                    fetch(base + "/drafts/" + id + "/cloth?rows=text").body());
            assertThat(cloth.get("rows").at(0)).isEqualTo(Json.of("XX..XX.."));
        }

        @Test
        void answersABadBodyWithFourHundredRatherThanFallingOver() throws Exception {
            HttpResponse<String> response = send("POST", start() + "/drafts", "{oops");
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(Json.parse(response.body()).get("code")).isEqualTo(Json.of("invalid_argument"));
        }

        @Test
        void answersAnUnknownPathWithFourHundredAndFour() throws Exception {
            assertThat(fetch(start() + "/nothing").statusCode()).isEqualTo(404);
        }

        @Test
        void answersAWrongMethodWithFourHundredAndFiveAndWhatIsAllowed() throws Exception {
            HttpResponse<String> response = send("DELETE", start() + "/health", null);
            assertThat(response.statusCode()).isEqualTo(405);
            assertThat(Json.parse(response.body()).get("allowed")).isEqualTo(Json.arr("GET"));
        }

        @Test
        void answersAWrongMethodOnAStoredRecordTheSameWay() throws Exception {
            String base = start();
            String id = postDraft(base);
            HttpResponse<String> response = send("PUT", base + "/drafts/" + id, null);
            assertThat(response.statusCode()).isEqualTo(405);
            assertThat(Json.parse(response.body()).get("allowed")).isEqualTo(Json.arr("DELETE", "GET"));
        }

        @Test
        void sendsAContentLengthThatMatchesTheBody() throws Exception {
            HttpResponse<String> response = fetch(start() + "/health");
            long length = response.headers().firstValueAsLong("content-length").orElse(-1);
            assertThat(length).isEqualTo(response.body().getBytes(StandardCharsets.UTF_8).length);
        }

        @Test
        void sendsNoBodyAtAllOnATwoHundredAndFour() throws Exception {
            String base = start();
            String id = postDraft(base);
            HttpResponse<String> removed = send("DELETE", base + "/drafts/" + id, null);
            assertThat(removed.statusCode()).isEqualTo(204);
            assertThat(removed.body()).isEmpty();
        }
    }
}
