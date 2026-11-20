package io.github.rkaushikdev.heddlewright.service;

import io.github.rkaushikdev.heddlewright.service.Router.Request;
import io.github.rkaushikdev.heddlewright.service.Router.Response;
import io.github.rkaushikdev.heddlewright.store.Journal;
import io.github.rkaushikdev.heddlewright.store.Repository;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole service in one object.
 *
 * <p>Everything is built round a single journal, so the state of the service is always exactly the
 * projection of one append-only log. The health route says so out loud: it reports the journal head,
 * which is a fingerprint of every write the service has taken.
 *
 * @param router the routing table
 * @param repository the repository over the journal
 */
public record App(Router router, Repository repository) {

    /** The version the service reports, which the build's version is held to. */
    public static final String VERSION = "0.2.0";

    /** Answer one request. */
    public Response request(Request request) {
        return Router.handle(router, request);
    }

    /** Build the service over a new, empty journal. */
    public static App createApp() {
        return createApp(Journal.createJournal());
    }

    /** Build the service over a journal, with every route mounted. */
    public static App createApp(Journal journal) {
        Repository repository = Repository.createRepository(journal);
        Router router = Router.createRouter();

        Router.route(
                router,
                "GET",
                "/health",
                call -> Json.obj(
                        "ok", true,
                        "version", VERSION,
                        "entries", Journal.length(repository.journal()),
                        "head", Journal.head(repository.journal()),
                        "records", Repository.counts(repository)));

        Router.route(router, "GET", "/routes", call -> Router.describeRoutes(router));

        Router.route(router, "GET", "/journal", call -> {
            String since = call.query().get("since");
            double from = since == null ? 0 : Text.parseInt(since);
            double after = Double.isNaN(from) ? 0 : from;
            return Journal.entries(repository.journal()).stream()
                    .filter(entry -> entry.sequence() > after)
                    .map(entry -> Json.obj("sequence", entry.sequence(), "type", entry.type(), "at", entry.at()))
                    .toList();
        });

        Router.route(router, "GET", "/journal/verify", call -> Journal.verify(repository.journal()));

        DraftRoutes.mountDraftRoutes(router, repository);
        ClothRoutes.mountClothRoutes(router, repository);
        FloorRoutes.mountFloorRoutes(router, repository);
        WeaveRoutes.mountWeaveRoutes(router);

        return new App(router, repository);
    }

    /** The routes the service answers on, sorted, for a smoke test to walk. */
    public static List<String> routeTable(App app) {
        return Router.describeRoutes(app.router()).stream().sorted().toList();
    }

    /** The routes declared more than once with the same method and path. */
    public static List<String> duplicateRoutes(App app) {
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (String line : Router.describeRoutes(app.router())) {
            seen.merge(line, 1, Integer::sum);
        }
        return seen.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();
    }
}
