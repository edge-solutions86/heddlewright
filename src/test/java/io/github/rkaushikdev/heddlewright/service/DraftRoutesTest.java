package io.github.rkaushikdev.heddlewright.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.Fixtures;
import io.github.rkaushikdev.heddlewright.draft.Draft;
import io.github.rkaushikdev.heddlewright.service.Router.Request;
import io.github.rkaushikdev.heddlewright.service.Router.Response;
import io.github.rkaushikdev.heddlewright.store.Repository;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DraftRoutesTest {

    private static final JsonObject INPUT = Fixtures.twillRecord();

    private Router router;
    private Repository repository;

    @BeforeEach
    void mount() {
        repository = Repository.createRepository();
        router = DraftRoutes.mountDraftRoutes(Router.createRouter(), repository);
    }

    private Response post(Object body) {
        return Router.handle(router, Request.of("POST", "/drafts", body));
    }

    private String created() {
        return post(INPUT).body().get("id").asText();
    }

    @Nested
    class PostDrafts {

        @Test
        void storesADraftAndAnswersWithItsSummary() {
            Response response = post(INPUT);
            assertThat(response.status()).isEqualTo(201);
            assertThat(response.body().get("name")).isEqualTo(Json.of("four shaft twill"));
            assertThat(Repository.list(repository, DraftRoutes.COLLECTION)).hasSize(1);
        }

        @Test
        void refusesADraftTheDomainWillNotAccept() {
            Response response = post(INPUT.with("shafts", 0));
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.body().get("code")).isEqualTo(Json.of("invalid_argument"));
        }

        @Test
        void refusesABodyThatIsNotAnObject() {
            assertThat(post("twill").status()).isEqualTo(400);
        }

        @Test
        void takesAWeavingFileInsteadOfADraftRecord() {
            String id = created();
            Json wif = Router.handle(router, Request.get("/drafts/" + id + "/wif"))
                    .body()
                    .get("wif");
            Router fresh = DraftRoutes.mountDraftRoutes(Router.createRouter(), Repository.createRepository());
            Response response = Router.handle(fresh, Request.of("POST", "/drafts", Json.obj("wif", wif)));
            assertThat(response.status()).isEqualTo(201);
            assertThat(response.body().get("id")).isEqualTo(Json.of(id));
        }

        @Test
        void refusesTheSameDraftTwice() {
            post(INPUT);
            assertThat(post(INPUT).status()).isEqualTo(409);
        }
    }

    @Nested
    class GetDrafts {

        @Test
        void isEmptyBeforeAnythingIsStored() {
            assertThat(Router.handle(router, Request.get("/drafts")).body()).isEqualTo(Json.arr());
        }

        @Test
        void summarisesEveryStoredDraft() {
            post(INPUT);
            Json listed = Router.handle(router, Request.get("/drafts")).body();
            assertThat(((JsonArray) listed).size()).isEqualTo(1);
            assertThat(listed.at(0).get("shafts")).isEqualTo(Json.of(4));
        }
    }

    @Nested
    class GetDraftsId {

        @Test
        void answersWithTheSummary() {
            Response response = Router.handle(router, Request.get("/drafts/" + created()));
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body().get("warpFaceRatio")).isEqualTo(Json.of(0.5));
        }

        @Test
        void isAFourHundredAndFourForADraftThatIsNotThere() {
            assertThat(Router.handle(router, Request.get("/drafts/draft-00000000"))
                            .status())
                    .isEqualTo(404);
        }
    }

    @Nested
    class GetDraftsIdCloth {

        @Test
        void derivesTheClothRatherThanStoringIt() {
            Json cloth = Router.handle(router, Request.get("/drafts/" + created() + "/cloth"))
                    .body();
            assertThat(cloth.get("picks")).isEqualTo(Json.of(8));
            assertThat(cloth.get("ends")).isEqualTo(Json.of(8));
            assertThat(cloth.get("rows").at(0)).isEqualTo(Json.arr(1, 1, 0, 0, 1, 1, 0, 0));
        }

        @Test
        void writesTheClothAsTextWhenAsked() {
            String path = "/drafts/" + created() + "/cloth";
            Json cloth = Router.handle(router, Request.get(path, Map.of("rows", "text")))
                    .body();
            assertThat(cloth.get("rows").at(0)).isEqualTo(Json.of("XX..XX.."));
        }
    }

    @Nested
    class GetDraftsIdAnalysis {

        @Test
        void readsTheFloatsAndTheFirmness() {
            Json analysis = Router.handle(router, Request.get("/drafts/" + created() + "/analysis"))
                    .body();
            assertThat(analysis.get("longestFloat").get("warp")).isEqualTo(Json.of(2));
            assertThat(analysis.get("firmness")).isEqualTo(Json.of("medium"));
            assertThat(analysis.get("problems")).isEqualTo(Json.arr());
        }

        @Test
        void takesAFloatLimitFromTheQuery() {
            String path = "/drafts/" + created() + "/analysis";
            Json analysis = Router.handle(router, Request.get(path, Map.of("floatLimit", "1")))
                    .body();
            assertThat(analysis.get("floatLimit")).isEqualTo(Json.of(1));
            assertThat(analysis.get("over").asDouble()).isGreaterThan(0);
        }

        @Test
        void refusesAFloatLimitThatIsNotAWholeNumber() {
            String path = "/drafts/" + created() + "/analysis";
            assertThat(Router.handle(router, Request.get(path, Map.of("floatLimit", "lots")))
                            .status())
                    .isEqualTo(400);
        }
    }

    @Nested
    class DeleteDraftsId {

        @Test
        void removesTheDraft() {
            String path = "/drafts/" + created();
            assertThat(Router.handle(router, Request.of("DELETE", path)).status())
                    .isEqualTo(204);
            assertThat(Repository.list(repository, DraftRoutes.COLLECTION)).isEmpty();
        }

        @Test
        void isAFourHundredAndFourForOneThatIsNotThere() {
            assertThat(Router.handle(router, Request.of("DELETE", "/drafts/draft-00000000"))
                            .status())
                    .isEqualTo(404);
        }
    }

    @Nested
    class Storable {

        @Test
        void roundTripsThroughCreateDraft() {
            Draft draft = Draft.create(INPUT);
            assertThat(Draft.create(DraftRoutes.storable(draft)).id()).isEqualTo(draft.id());
        }

        @Test
        void copiesTheArraysOutOfTheFrozenDraft() {
            Draft draft = Draft.create(INPUT);
            JsonObject record = DraftRoutes.storable(draft);
            // The stored record is a JSON value of its own: it cannot be edited, and the draft keeps
            // its threading whatever happens to the record.
            assertThatThrownBy(
                            () -> ((JsonArray) record.get("threading")).items().set(0, Json.of(3)))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(draft.threading().get(0)).isEqualTo(1);
        }
    }
}
