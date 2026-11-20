package io.github.rkaushikdev.heddlewright.service;

import io.github.rkaushikdev.heddlewright.analysis.Floats;
import io.github.rkaushikdev.heddlewright.analysis.Interlacement;
import io.github.rkaushikdev.heddlewright.draft.Draft;
import io.github.rkaushikdev.heddlewright.draft.Drawdown;
import io.github.rkaushikdev.heddlewright.draft.Wif;
import io.github.rkaushikdev.heddlewright.draft.WifWrite;
import io.github.rkaushikdev.heddlewright.service.Router.Response;
import io.github.rkaushikdev.heddlewright.store.Repository;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.util.List;

/**
 * The draft side of the service.
 *
 * <p>A draft is stored as the plain record that built it, not as a drawdown: the cloth is derived on
 * the way out, so a stored draft can never disagree with the cloth it makes.
 */
public final class DraftRoutes {

    /** The collection drafts are stored in. */
    public static final String COLLECTION = "drafts";

    private DraftRoutes() {}

    /** Add the draft routes to a router, reading and writing through a repository. */
    public static Router mountDraftRoutes(Router router, Repository repository) {
        Router.route(router, "GET", "/drafts", call -> Repository.list(repository, COLLECTION).stream()
                .map(record -> Draft.create(record).summary())
                .toList());

        Router.route(router, "GET", "/drafts/:id", call -> stored(
                        repository, call.params().get("id"))
                .summary());

        Router.route(router, "GET", "/drafts/:id/cloth", call -> {
            Draft draft = stored(repository, call.params().get("id"));
            Matrix cloth = Drawdown.drawdownFromDraft(draft);
            boolean text = "text".equals(call.query().get("rows"));
            return Json.obj(
                    "id", draft.id(),
                    "picks", cloth.rows(),
                    "ends", cloth.cols(),
                    "rows", text ? Text.split(cloth.format(), "\n") : cloth);
        });

        Router.route(router, "GET", "/drafts/:id/analysis", call -> {
            Draft draft = stored(repository, call.params().get("id"));
            Matrix cloth = Drawdown.drawdownFromDraft(draft);
            String given = call.query().get("floatLimit");
            double limit = given == null ? 3 : Text.parseInt(given);
            if (Double.isNaN(limit) || limit < 1) {
                throw new InvalidArgumentException(
                        "floatLimit must be a whole number of threads", Json.obj("got", given));
            }
            int floatLimit = (int) limit;
            return Json.obj(
                    "id", draft.id(),
                    "longestFloat", Floats.longestFloat(cloth),
                    "over", Floats.floatsOver(cloth, floatLimit).size(),
                    "floatLimit", floatLimit,
                    "interlacement", Interlacement.interlacementFactor(cloth),
                    "firmness", Interlacement.firmnessClass(cloth),
                    "problems", draft.inspect());
        });

        Router.route(router, "GET", "/drafts/:id/wif", call -> {
            Draft draft = stored(repository, call.params().get("id"));
            return new Response(200, Json.obj("id", draft.id(), "wif", WifWrite.writeWif(draft)));
        });

        Router.route(router, "POST", "/drafts", call -> {
            JsonObject body = Check.plainObject(call.body(), "body");
            Json wif = body.get("wif");
            Draft draft = wif.isMissing() ? Draft.create(body) : Wif.draftFromWif(Text.string(wif));
            Repository.insert(repository, COLLECTION, draft.id(), storable(draft));
            return Response.of(201, draft.summary());
        });

        Router.route(router, "DELETE", "/drafts/:id", call -> {
            Repository.remove(repository, COLLECTION, call.params().get("id"));
            return new Response(204, Json.NULL);
        });

        return router;
    }

    private static Draft stored(Repository repository, String id) {
        return Draft.create(Repository.get(repository, COLLECTION, id));
    }

    /** The plain record a draft is stored as, which {@link Draft#create} reads straight back. */
    public static JsonObject storable(Draft draft) {
        return Json.obj(
                "name", draft.name(),
                "shafts", draft.shafts(),
                "treadles", draft.treadles(),
                "shed", draft.shed(),
                "threading", List.copyOf(draft.threading()),
                "tieup", draft.tieup(),
                "treadling", draft.treadling(),
                "notes", draft.notes());
    }
}
