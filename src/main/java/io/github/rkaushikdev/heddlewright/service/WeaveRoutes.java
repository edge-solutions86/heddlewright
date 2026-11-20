package io.github.rkaushikdev.heddlewright.service;

import io.github.rkaushikdev.heddlewright.analysis.Report;
import io.github.rkaushikdev.heddlewright.draft.Draft;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Text;
import io.github.rkaushikdev.heddlewright.weave.Catalogue;
import io.github.rkaushikdev.heddlewright.weave.Crepe;
import io.github.rkaushikdev.heddlewright.weave.Satin;
import io.github.rkaushikdev.heddlewright.weave.Twill;
import java.util.List;

/**
 * The weave side of the service.
 *
 * <p>Nothing here is stored. Every answer is worked out from the catalogue or from the arguments,
 * which is what makes these routes safe to call as often as a design tool wants to: there is no
 * state to get out of step.
 */
public final class WeaveRoutes {

    private WeaveRoutes() {}

    /** Add the weave routes to a router. */
    public static Router mountWeaveRoutes(Router router) {
        Router.route(router, "GET", "/weaves", call -> {
            List<Catalogue.Measurement> rows = Catalogue.measureAll();
            String family = call.query().get("family");
            if (family == null) {
                return rows;
            }
            return rows.stream().filter(row -> row.family().equals(family)).toList();
        });

        Router.route(router, "GET", "/weaves/families", call -> Catalogue.families());

        Router.route(router, "GET", "/weaves/:name", call -> {
            String name = call.params().get("name");
            Matrix repeat = Catalogue.weaveByName(name);
            return ((JsonObject) Json.of(Catalogue.measure(name)))
                    .with("rows", rows(repeat, call.query().get("rows")));
        });

        Router.route(router, "GET", "/weaves/:name/crepe", call -> {
            String name = call.params().get("name");
            return Json.obj("name", name).merge((JsonObject) Json.of(Crepe.readsAsCrepe(Catalogue.weaveByName(name))));
        });

        Router.route(router, "POST", "/twill", call -> {
            JsonObject body = Check.plainObject(call.body(), "body");
            Json given = body.get("notation");
            String notation = given.isNullish() ? "2/2" : Text.string(given);
            Json optionsGiven = body.get("options");
            Json options = optionsGiven.isNullish() ? Json.obj() : optionsGiven;
            Matrix repeat = Twill.twillWeave(notation, Twill.Options.fromRecord(options));
            Json endsPerInch = body.get("endsPerInch");
            Object angle = Json.NULL;
            if (!endsPerInch.isMissing()) {
                Json step = optionsGiven.isNullish() ? Json.MISSING : optionsGiven.get("step");
                Json picks = body.get("picksPerInch");
                angle = Twill.twillAngle(
                        Check.integer(step.isNullish() ? Json.of(1) : step, "step"),
                        Check.number(endsPerInch, "endsPerInch"),
                        Check.number(picks.isNullish() ? endsPerInch : picks, "picksPerInch"));
            }
            return Json.obj(
                    "notation",
                    notation,
                    "balance",
                    Twill.twillBalance(notation),
                    "angle",
                    angle,
                    "rows",
                    Text.split(repeat.format(), "\n"));
        });

        Router.route(router, "GET", "/satin/:threads", call -> {
            String given = call.params().get("threads");
            double parsed = Text.parseInt(given);
            if (Double.isNaN(parsed) || parsed < 1) {
                throw new InvalidArgumentException("the thread count must be a whole number", Json.obj("got", given));
            }
            int threads = (int) parsed;
            List<Integer> moves = Satin.regularSatinMoves(threads);
            if (!Satin.hasRegularSatin(threads)) {
                return Json.obj(
                        "threads",
                        threads,
                        "regular",
                        false,
                        "moves",
                        moves,
                        "reason",
                        "no move number is coprime and clear of one");
            }
            String moveGiven = call.query().get("move");
            int move = moveGiven == null ? moves.get(0) : Check.integer((Object) Text.parseInt(moveGiven), "move");
            return Json.obj(
                    "threads", threads,
                    "regular", true,
                    "moves", moves,
                    "move", move,
                    "rows", Text.split(Satin.satinWeave(threads, move).format(), "\n"));
        });

        Router.route(router, "POST", "/report", call -> {
            JsonObject body = Check.plainObject(call.body(), "body");
            Json draftGiven = body.get("draft");
            Draft draft = Draft.create(draftGiven.isNullish() ? body : draftGiven);
            Json optionsGiven = body.get("options");
            Json options = optionsGiven.isNullish() ? Json.obj("count", 30) : optionsGiven;
            Report.ClothReport report = Report.clothReport(draft, Report.Options.fromRecord(options));
            return body.get("render").equals(Json.of("text")) ? Json.obj("text", Report.renderReport(report)) : report;
        });

        return router;
    }

    private static Object rows(Matrix repeat, String form) {
        return "text".equals(form) ? Text.split(repeat.format(), "\n") : repeat;
    }

    /** The names a design tool can ask for, for a picker. */
    public static List<String> weaveNames() {
        return Catalogue.listWeaves().stream().map(Catalogue.Weave::name).toList();
    }
}
