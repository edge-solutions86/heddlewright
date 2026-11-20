package io.github.rkaushikdev.heddlewright.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.service.Router.Request;
import io.github.rkaushikdev.heddlewright.service.Router.Response;
import io.github.rkaushikdev.heddlewright.util.ConflictException;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RouterTest {

    private static Router built() {
        Router router = Router.createRouter();
        Router.route(router, "GET", "/drafts", call -> List.of(Json.obj("id", "d1")));
        Router.route(
                router,
                "GET",
                "/drafts/:id",
                call -> Json.obj("id", call.params().get("id")));
        Router.route(
                router,
                "GET",
                "/drafts/:id/cloth",
                call -> Json.obj("cloth", call.params().get("id")));
        Router.route(router, "POST", "/drafts", call -> new Response(201, call.body()));
        return router;
    }

    private static List<Json> items(Json value) {
        return ((JsonArray) value).items();
    }

    @Nested
    class Route {

        @Test
        void namesTheMethodsItTakes() {
            assertThat(Router.METHODS).containsExactly("GET", "POST", "PUT", "DELETE");
        }

        @Test
        void refusesAMethodItDoesNotKnow() {
            assertThatThrownBy(() -> Router.route(Router.createRouter(), "PATCH", "/x", call -> null))
                    .isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void refusesAPathWithNoLeadingSlashAndAMissingHandler() {
            assertThatThrownBy(() -> Router.route(Router.createRouter(), "GET", "x", call -> null))
                    .hasMessageContaining("starts with a slash");
            assertThatThrownBy(() -> Router.route(Router.createRouter(), "GET", "/x", null))
                    .hasMessageContaining("handler function");
        }
    }

    @Nested
    class Match {

        private final Router router = built();

        @Test
        void matchesAPlainPath() {
            assertThat(Router.match(router, "GET", "/drafts").orElseThrow().params())
                    .isEmpty();
        }

        @Test
        void capturesNamedSegments() {
            assertThat(Router.match(router, "GET", "/drafts/d7").orElseThrow().params())
                    .isEqualTo(Map.of("id", "d7"));
        }

        @Test
        void doesNotLetAParameterSwallowALongerPath() {
            assertThat(Router.match(router, "GET", "/drafts/d7/cloth")
                            .orElseThrow()
                            .route()
                            .path())
                    .isEqualTo("/drafts/:id/cloth");
        }

        @Test
        void isNullForAPathNothingDeclares() {
            assertThat(Router.match(router, "GET", "/looms")).isEmpty();
            assertThat(Router.match(router, "DELETE", "/drafts")).isEmpty();
        }

        @Test
        void decodesASegmentWrittenWithAnEscape() {
            assertThat(Router.match(router, "GET", "/drafts/a%2Fb")
                            .orElseThrow()
                            .params()
                            .get("id"))
                    .isEqualTo("a/b");
        }

        @Test
        void ignoresTrailingAndRepeatedSlashes() {
            assertThat(Router.match(router, "GET", "/drafts/")).isPresent();
            assertThat(Router.match(router, "GET", "//drafts//")).isPresent();
        }
    }

    @Nested
    class Handle {

        private final Router router = built();

        @Test
        void returnsWhatTheHandlerGivesAsATwoHundred() {
            assertThat(Router.handle(router, Request.get("/drafts/d7")))
                    .isEqualTo(new Response(200, Json.obj("id", "d7")));
        }

        @Test
        void letsAHandlerSetItsOwnStatus() {
            assertThat(Router.handle(router, Request.of("POST", "/drafts", Json.obj("a", 1))))
                    .isEqualTo(new Response(201, Json.obj("a", 1)));
        }

        @Test
        void turnsNothingAtAllIntoATwoHundredAndFour() {
            Router quiet = Router.createRouter();
            Router.route(quiet, "DELETE", "/drafts/:id", call -> null);
            assertThat(Router.handle(quiet, Request.of("DELETE", "/drafts/d1")))
                    .isEqualTo(new Response(204, Json.NULL));
        }

        @Test
        void answersAMissingRouteWithThePathsItDoesKnow() {
            Response response = Router.handle(router, Request.get("/looms"));
            assertThat(response.status()).isEqualTo(404);
            assertThat(items(response.body().get("known"))).contains(Json.of("/drafts"));
        }

        @Test
        void tellsAWrongMethodApartFromAMissingPath() {
            Response response = Router.handle(router, Request.of("DELETE", "/drafts"));
            assertThat(response.status()).isEqualTo(405);
            assertThat(response.body().get("allowed")).isEqualTo(Json.arr("GET", "POST"));
        }

        @Test
        void stillAnswersFourHundredAndFourWhenNothingDeclaresThePath() {
            assertThat(Router.handle(router, Request.of("DELETE", "/looms")).status())
                    .isEqualTo(404);
        }

        @Test
        void readsAWrongMethodOnAParameterPathToo() {
            Response response = Router.handle(router, Request.of("POST", "/drafts/d7"));
            assertThat(response.status()).isEqualTo(405);
            assertThat(response.body().get("allowed")).isEqualTo(Json.arr("GET"));
        }

        @Test
        void defaultsTheMethodToGet() {
            assertThat(Router.handle(router, new Request(null, "/drafts", null, null))
                            .status())
                    .isEqualTo(200);
        }

        @Test
        void waitsForAHandlerThatAnswersLater() {
            Router slow = Router.createRouter();
            Router.route(slow, "GET", "/x", call -> CompletableFuture.supplyAsync(() -> Json.obj("ok", true)));
            assertThat(Router.handle(slow, Request.get("/x")).body()).isEqualTo(Json.obj("ok", true));
        }

        @Test
        void refusesARequestWithNoPath() {
            assertThatThrownBy(() -> Router.handle(router, new Request(null, null, null, null)))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class ErrorResponse {

        @Test
        void mapsEachOfOurCodesToAStatus() {
            assertThat(Router.errorResponse(new NotFoundException("gone")).status())
                    .isEqualTo(404);
            assertThat(Router.errorResponse(new InvalidArgumentException("bad")).status())
                    .isEqualTo(400);
            assertThat(Router.errorResponse(new DraftException("unweavable")).status())
                    .isEqualTo(422);
            assertThat(Router.errorResponse(new ConflictException("clash")).status())
                    .isEqualTo(409);
        }

        @Test
        void keepsTheCodeAndTheDetailInTheBody() {
            Response response = Router.errorResponse(new DraftException("unweavable", Json.obj("ends", List.of(3))));
            assertThat(response.body().get("code")).isEqualTo(Json.of("draft_error"));
            assertThat(response.body().get("detail")).isEqualTo(Json.obj("ends", List.of(3)));
        }

        @Test
        void fallsBackToAFiveHundredForAnythingElse() {
            Response response = Router.errorResponse(new IllegalStateException("boom"));
            assertThat(response.status()).isEqualTo(500);
            assertThat(response.body().get("message")).isEqualTo(Json.of("boom"));
        }

        @Test
        void isWhatAThrowingHandlerProduces() {
            Router router = Router.createRouter();
            Router.route(router, "GET", "/x", call -> {
                throw new NotFoundException("nothing here");
            });
            assertThat(Router.handle(router, Request.get("/x")).status()).isEqualTo(404);
        }
    }

    @Nested
    class MethodsFor {

        private final Router router = built();

        @Test
        void namesEveryMethodDeclaredForAPath() {
            assertThat(Router.methodsFor(router, "/drafts")).containsExactly("GET", "POST");
            assertThat(Router.methodsFor(router, "/drafts/d7")).containsExactly("GET");
        }

        @Test
        void isEmptyForAPathNobodyDeclared() {
            assertThat(Router.methodsFor(router, "/looms")).isEmpty();
        }

        @Test
        void doesNotLetAParameterPathAnswerForALongerOne() {
            assertThat(Router.methodsFor(router, "/drafts/d7/nothing")).isEmpty();
        }
    }

    @Nested
    class PathsForAndDescribeRoutes {

        @Test
        void listsThePathsAMethodReachesSorted() {
            assertThat(Router.pathsFor(built(), "GET")).containsExactly("/drafts", "/drafts/:id", "/drafts/:id/cloth");
            assertThat(Router.pathsFor(built(), "DELETE")).isEmpty();
        }

        @Test
        void describesTheWholeTableInDeclarationOrder() {
            assertThat(Router.describeRoutes(built()))
                    .containsExactly("GET /drafts", "GET /drafts/:id", "GET /drafts/:id/cloth", "POST /drafts");
        }
    }
}
