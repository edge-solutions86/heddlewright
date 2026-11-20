package io.github.rkaushikdev.heddlewright.tools;

import io.github.rkaushikdev.heddlewright.draft.Threading;
import io.github.rkaushikdev.heddlewright.draft.Tieup;
import io.github.rkaushikdev.heddlewright.draft.Treadling;
import io.github.rkaushikdev.heddlewright.service.App;
import io.github.rkaushikdev.heddlewright.service.Server;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * The smoke test.
 *
 * <p>It starts the service on a real socket and walks one order of work through it end to end: store
 * a draft, read its cloth, open a piece, book a fault, grade it, register a loom and get a plan back.
 * Anything the unit tests can fake, this one does not.
 */
public final class Smoke {

    /** One step of the walk: what was asked, what came back, and whether they agree. */
    public record Step(String name, Json expected, Json actual, boolean ok) {
        /** A step, passing when what came back is what was wanted. */
        public static Step of(String name, Object expected, Object actual) {
            Json wanted = Json.of(expected);
            Json got = Json.of(actual);
            return new Step(name, wanted, got, wanted.equals(got));
        }
    }

    /** Every step walked and the ones that failed. */
    public record Result(List<Step> steps, List<Step> failures) {
        /** Copies the lists. */
        public Result {
            steps = List.copyOf(steps);
            failures = List.copyOf(failures);
        }
    }

    private static final JsonObject DRAFT = Json.obj(
            "name",
            "smoke twill",
            "shafts",
            4,
            "treadles",
            4,
            "threading",
            Threading.straightDraw(4, 8),
            "tieup",
            Tieup.twillTieup(4, 2),
            "treadling",
            Treadling.straightTreadling(4, 8));

    private Smoke() {}

    private record Answer(int status, Json body) {}

    private static Answer send(HttpClient client, String method, String url, Object body)
            throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(Json.stringify(body));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .method(method, publisher)
                .header("content-type", "application/json")
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new Answer(response.statusCode(), response.body().isEmpty() ? Json.NULL : Json.parse(response.body()));
    }

    private static Answer fetch(HttpClient client, String url) throws IOException, InterruptedException {
        return send(client, "GET", url, null);
    }

    /** Walk the whole service and report every step. */
    public static Result smoke() throws IOException, InterruptedException {
        Server running = Server.createServer(App.createApp());
        String base = "http://127.0.0.1:" + Server.listen(running);
        HttpClient client = HttpClient.newHttpClient();
        List<Step> steps = new ArrayList<>();
        try {
            steps.add(Step.of(
                    "health answers",
                    true,
                    fetch(client, base + "/health").body().get("ok")));

            Answer created = send(client, "POST", base + "/drafts", DRAFT);
            steps.add(Step.of("draft stored", 201, created.status()));
            String id = created.body().get("id").asText();

            Json cloth =
                    fetch(client, base + "/drafts/" + id + "/cloth?rows=text").body();
            steps.add(Step.of("cloth derived", "XX..XX..", cloth.get("rows").at(0)));

            Json analysis = fetch(client, base + "/drafts/" + id + "/analysis").body();
            steps.add(Step.of("floats measured", 2, analysis.get("longestFloat").get("warp")));

            Answer piece = send(
                    client,
                    "POST",
                    base + "/pieces",
                    Json.obj("reference", "SMOKE-1", "yards", 100, "widthInches", 60));
            steps.add(Step.of("piece opened", 201, piece.status()));

            send(
                    client,
                    "POST",
                    base + "/pieces/SMOKE-1/defects",
                    Json.obj("defects", List.of(Json.obj("code", "weft_bar", "yard", 3, "inches", 12))));
            Json grade = fetch(client, base + "/pieces/SMOKE-1/grade").body();
            steps.add(Step.of("piece graded", 4, grade.get("score").get("totalPoints")));

            Answer loom = send(
                    client,
                    "POST",
                    base + "/looms",
                    Json.obj(
                            "reference", "SMOKE-L1",
                            "shedding", "dobby",
                            "reedWidthCm", 200,
                            "shafts", 16,
                            "picksMinute", 220));
            steps.add(Step.of("loom registered", 201, loom.status()));

            JsonObject run = Json.obj(
                    "order",
                    "SMOKE-SO-1",
                    "style",
                    "smoke twill",
                    "metres",
                    100,
                    "picksPerCm",
                    20,
                    "requirements",
                    Json.obj("widthCm", 150, "shafts", 4, "hooks", 0, "weftColours", 1));
            Json plan = send(client, "POST", base + "/plan", Json.obj("runs", List.of(run)))
                    .body();
            steps.add(Step.of("plan made", "SMOKE-L1", plan.get("placed").at(0).get("loom")));

            Json sound = fetch(client, base + "/journal/verify").body();
            steps.add(Step.of("journal sound", true, sound.get("sound")));
        } finally {
            Server.close(running);
        }
        return new Result(steps, steps.stream().filter(step -> !step.ok()).toList());
    }

    /** Walk the service, print every step, and exit non-zero if any failed. */
    public static void main(String[] args) throws IOException, InterruptedException {
        Result result = smoke();
        for (Step step : result.steps()) {
            System.out.println((step.ok() ? "ok   " : "FAIL ") + step.name());
        }
        for (Step step : result.failures()) {
            System.err.println("  " + step.name() + ": wanted " + Json.stringify(step.expected()) + ", got "
                    + Json.stringify(step.actual()));
        }
        System.exit(result.failures().isEmpty() ? 0 : 1);
    }
}
