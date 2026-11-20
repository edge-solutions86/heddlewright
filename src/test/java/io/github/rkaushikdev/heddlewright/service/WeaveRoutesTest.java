package io.github.rkaushikdev.heddlewright.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.heddlewright.Fixtures;
import io.github.rkaushikdev.heddlewright.service.Router.Request;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WeaveRoutesTest {

    private static final JsonObject DRAFT = Fixtures.twill().ends(16).picks(16).record();

    private Router router;

    @BeforeEach
    void mount() {
        router = WeaveRoutes.mountWeaveRoutes(Router.createRouter());
    }

    /** A row the service printed, with o for each raised end, which is how these tests write cloth. */
    private static String raised(Json row) {
        return row.asText().replace('X', 'o');
    }

    private Json get(String path) {
        return Router.handle(router, Request.get(path)).body();
    }

    private Json post(String path, Object body) {
        return Router.handle(router, Request.of("POST", path, body)).body();
    }

    @Nested
    class GetWeaves {

        @Test
        void listsTheWholeCatalogueMeasured() {
            Json rows = get("/weaves");
            assertThat(((JsonArray) rows).size())
                    .isEqualTo(WeaveRoutes.weaveNames().size());
            assertThat(rows.at(0).get("warpFace").isMissing()).isFalse();
        }

        @Test
        void filtersToAFamily() {
            Json rows = Router.handle(router, Request.get("/weaves", Map.of("family", "satin")))
                    .body();
            assertThat(((JsonArray) rows).items())
                    .allMatch(row -> row.get("family").equals(Json.of("satin")));
        }

        @Test
        void listsTheFamilies() {
            assertThat(((JsonArray) get("/weaves/families")).items()).contains(Json.of("twill"));
        }
    }

    @Nested
    class GetWeavesName {

        @Test
        void measuresOneWeaveAndHandsBackItsRepeat() {
            Json plain = get("/weaves/plain");
            assertThat(plain.get("picks")).isEqualTo(Json.of(2));
            assertThat(plain.get("rows")).isEqualTo(Json.arr(Json.arr(1, 0), Json.arr(0, 1)));
        }

        @Test
        void writesTheRepeatAsTextWhenAsked() {
            Json plain = Router.handle(router, Request.get("/weaves/plain", Map.of("rows", "text")))
                    .body();
            assertThat(plain.get("rows")).isEqualTo(Json.arr("X.", ".X"));
        }

        @Test
        void isAFourHundredAndFourForAWeaveNobodyHas() {
            assertThat(Router.handle(router, Request.get("/weaves/gauze")).status())
                    .isEqualTo(404);
        }

        @Test
        void readsAWeaveForWhetherItIsACrepe() {
            assertThat(get("/weaves/twill%202%2F2/crepe").get("reads")).isEqualTo(Json.of(false));
            assertThat(get("/weaves/crepe%20rearranged/crepe").get("reads")).isEqualTo(Json.of(true));
        }
    }

    @Nested
    class PostTwill {

        @Test
        void weavesATwillFromItsNotationAndReportsItsBalance() {
            Json twill = post("/twill", Json.obj("notation", "3/1"));
            assertThat(raised(twill.get("rows").at(0))).isEqualTo("ooo.");
            assertThat(twill.get("balance")).isEqualTo(Json.obj("warp", 3, "weft", 1, "repeat", 4));
            assertThat(twill.get("angle")).isEqualTo(Json.NULL);
        }

        @Test
        void worksTheAngleOutWhenASettIsGiven() {
            Json twill = post("/twill", Json.obj("notation", "2/2", "endsPerInch", 48, "picksPerInch", 24));
            assertThat(twill.get("angle")).isEqualTo(Json.of(63.43));
        }

        @Test
        void refusesNotationItCannotRead() {
            assertThat(Router.handle(router, Request.of("POST", "/twill", Json.obj("notation", "2")))
                            .status())
                    .isEqualTo(422);
        }
    }

    @Nested
    class GetSatinThreads {

        @Test
        void weavesASatinAndNamesItsLegalMoves() {
            Json satin = get("/satin/5");
            assertThat(satin.get("regular")).isEqualTo(Json.of(true));
            assertThat(satin.get("moves")).isEqualTo(Json.arr(2, 3));
            assertThat(raised(satin.get("rows").at(0))).isEqualTo(".oooo");
        }

        @Test
        void takesAMoveFromTheQuery() {
            Json satin = Router.handle(router, Request.get("/satin/8", Map.of("move", "5")))
                    .body();
            assertThat(satin.get("move")).isEqualTo(Json.of(5));
        }

        @Test
        void saysPlainlyWhenNoRegularSatinExists() {
            Json satin = get("/satin/6");
            assertThat(satin.get("regular")).isEqualTo(Json.of(false));
            assertThat(satin.get("moves")).isEqualTo(Json.arr());
            assertThat(satin.get("reason").asText()).contains("coprime");
        }

        @Test
        void refusesAThreadCountThatIsNotANumber() {
            assertThat(Router.handle(router, Request.get("/satin/lots")).status())
                    .isEqualTo(400);
        }
    }

    @Nested
    class PostReport {

        @Test
        void reportsOnADraftHandedStraightIn() {
            Json report = post("/report", Json.obj("draft", DRAFT, "options", Json.obj("count", 30)));
            assertThat(report.get("structure").get("repeatEnds")).isEqualTo(Json.of(4));
            assertThat(report.get("floats").get("longestWarp")).isEqualTo(Json.of(2));
        }

        @Test
        void rendersItAsTextWhenAsked() {
            Json report = post("/report", Json.obj("draft", DRAFT, "options", Json.obj("count", 30), "render", "text"));
            assertThat(report.get("text").asText()).contains("Structure");
        }

        @Test
        void refusesADraftTheDomainWillNotTake() {
            Json body = Json.obj("draft", DRAFT.with("shafts", 0));
            assertThat(Router.handle(router, Request.of("POST", "/report", body))
                            .status())
                    .isEqualTo(400);
        }
    }
}
