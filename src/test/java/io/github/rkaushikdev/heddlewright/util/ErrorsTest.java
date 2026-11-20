package io.github.rkaushikdev.heddlewright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ErrorsTest {

    @Nested
    class ErrorHierarchy {

        @Test
        void everySubclassIsAHeddleErrorAndAnError() {
            List<RuntimeException> errors = List.of(
                    new InvalidArgumentException("a"),
                    new DraftException("b"),
                    new CapacityException("c"),
                    new NotFoundException("d"),
                    new ConflictException("e"));
            for (RuntimeException err : errors) {
                assertThat(err).isInstanceOf(HeddleException.class).isInstanceOf(RuntimeException.class);
            }
        }

        @Test
        void carriesTheCodeTheSubclassFixes() {
            assertThat(new InvalidArgumentException("x").code()).isEqualTo("invalid_argument");
            assertThat(new DraftException("x").code()).isEqualTo("draft_error");
            assertThat(new CapacityException("x").code()).isEqualTo("capacity_error");
            assertThat(new NotFoundException("x").code()).isEqualTo("not_found");
            assertThat(new ConflictException("x").code()).isEqualTo("conflict");
        }

        @Test
        void namesItselfAfterTheSubclassNotAfterError() {
            assertThat(new CapacityException("x").name()).isEqualTo("CapacityException");
            assertThat(new HeddleException("x", "zzz").name()).isEqualTo("HeddleException");
        }

        @Test
        void keepsStructuredDetailRatherThanFoldingItIntoTheMessage() {
            CapacityException err = new CapacityException("too many hooks", Json.obj("asked", 1600, "machine", 1344));
            assertThat(err.detail()).isEqualTo(Json.obj("asked", 1600, "machine", 1344));
            assertThat(err.getMessage()).isEqualTo("too many hooks");
        }

        @Test
        void detailDefaultsToAnEmptyObjectNeverUndefined() {
            assertThat(new NotFoundException("gone").detail()).isEqualTo(JsonObject.EMPTY);
        }
    }

    @Nested
    class ToJson {

        @Test
        void roundTripsThroughJsonWithTheCodeAndDetailIntact() {
            DraftException err = new DraftException("shaft 9 is not threaded", Json.obj("shaft", 9, "shafts", 8));
            Json plain = Json.parse(Json.stringify(err.toJson()));
            assertThat(plain)
                    .isEqualTo(Json.obj(
                            "name",
                            "DraftException",
                            "code",
                            "draft_error",
                            "message",
                            "shaft 9 is not threaded",
                            "detail",
                            Json.obj("shaft", 9, "shafts", 8)));
        }
    }

    @Nested
    class ReviveError {

        @Test
        void rebuildsTheExactSubclassForAKnownCode() {
            HeddleException revived = Errors.reviveError(
                    Json.obj("code", "not_found", "message", "no such loom", "detail", Json.obj("id", "L4")));
            assertThat(revived).isInstanceOf(NotFoundException.class);
            assertThat(revived.detail()).isEqualTo(Json.obj("id", "L4"));
        }

        @Test
        void fallsBackToHeddleErrorForACodeItDoesNotKnow() {
            HeddleException revived = Errors.reviveError(Json.obj("code", "something_new", "message", "later"));
            assertThat(revived).isInstanceOf(HeddleException.class).isNotInstanceOf(InvalidArgumentException.class);
            assertThat(revived.code()).isEqualTo("something_new");
        }

        @Test
        void doesNotShareTheDetailObjectWithTheInput() {
            JsonObject input = Json.obj("code", "conflict", "message", "clash", "detail", Json.obj("at", 3));
            HeddleException revived = Errors.reviveError(input);
            JsonObject changed = revived.detail().with("at", 99);
            assertThat(changed.get("at")).isEqualTo(Json.of(99));
            assertThat(revived.detail().get("at")).isEqualTo(Json.of(3));
            assertThat(input.get("detail").get("at")).isEqualTo(Json.of(3));
        }

        @Test
        void refusesANonObject() {
            assertThatThrownBy(() -> Errors.reviveError("not_found")).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Errors.reviveError(null)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class IsHeddleError {

        @Test
        void isFalseForAPlainError() {
            assertThat(Errors.isHeddleError(new RuntimeException("plain"))).isFalse();
            assertThat(Errors.isHeddleError("not_found")).isFalse();
        }

        @Test
        void narrowsOnCodeWhenOneIsGiven() {
            NotFoundException err = new NotFoundException("gone");
            assertThat(Errors.isHeddleError(err)).isTrue();
            assertThat(Errors.isHeddleError(err, "not_found")).isTrue();
            assertThat(Errors.isHeddleError(err, "conflict")).isFalse();
        }
    }

    @Nested
    class KnownErrorCodes {

        @Test
        void listsExactlyTheCodesReviveErrorReconstructsSorted() {
            List<String> codes = Errors.knownErrorCodes();
            assertThat(codes)
                    .containsExactly("capacity_error", "conflict", "draft_error", "invalid_argument", "not_found");
            for (String code : codes) {
                assertThat(Errors.reviveError(Json.obj("code", code, "message", "x"))
                                .code())
                        .isEqualTo(code);
            }
        }
    }
}
