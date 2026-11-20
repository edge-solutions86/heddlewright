package io.github.rkaushikdev.heddlewright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Nested
    class CanonicalStringify {

        @Test
        void sortsObjectKeysSoKeyOrderCannotChangeAnId() {
            assertThat(Json.canonicalStringify(Json.obj("b", 1, "a", 2))).isEqualTo("{\"a\":2,\"b\":1}");
            assertThat(Json.canonicalStringify(Json.obj("a", 2, "b", 1))).isEqualTo("{\"a\":2,\"b\":1}");
        }

        @Test
        void sortsNestedKeysToo() {
            assertThat(Json.canonicalStringify(Json.obj("z", Json.obj("y", 1, "x", 2))))
                    .isEqualTo("{\"z\":{\"x\":2,\"y\":1}}");
        }

        @Test
        void keepsArrayOrderBecauseATreadlingIsNotASet() {
            assertThat(Json.canonicalStringify(List.of(3, 1, 2))).isEqualTo("[3,1,2]");
        }

        @Test
        void dropsUndefinedPropertiesButWritesUndefinedArrayHolesAsNull() {
            assertThat(Json.canonicalStringify(Json.obj("a", 1, "b", Json.MISSING)))
                    .isEqualTo("{\"a\":1}");
            assertThat(Json.canonicalStringify(Arrays.asList(1, Json.MISSING, 2)))
                    .isEqualTo("[1,null,2]");
        }

        @Test
        void foldsNegativeZeroSoARoundedFigureCannotForkAnId() {
            assertThat(Json.canonicalStringify(-0.0)).isEqualTo("0");
            assertThat(Json.canonicalStringify(Json.obj("v", -0.0))).isEqualTo("{\"v\":0}");
        }

        @Test
        void writesADateAsItsIsoString() {
            assertThat(Json.canonicalStringify(Instant.parse("2025-01-02T03:04:05Z")))
                    .isEqualTo("\"2025-01-02T03:04:05.000Z\"");
        }

        @Test
        void writesAMapLikeAnObjectAndASetLikeAnArray() {
            Map<String, Integer> map = new LinkedHashMap<>();
            map.put("b", 1);
            map.put("a", 2);
            assertThat(Json.canonicalStringify(map)).isEqualTo("{\"a\":2,\"b\":1}");
            assertThat(Json.canonicalStringify(new LinkedHashSet<>(List.of(2, 1))))
                    .isEqualTo("[2,1]");
        }

        @Test
        void refusesWhatJsonCannotCarryHonestly() {
            Supplier<String> function = () -> "x";
            assertThatThrownBy(() -> Json.canonicalStringify(Double.NaN)).hasMessageContaining("non-finite");
            assertThatThrownBy(() -> Json.canonicalStringify(Double.POSITIVE_INFINITY))
                    .hasMessageContaining("non-finite");
            assertThatThrownBy(() -> Json.canonicalStringify(BigInteger.TEN)).hasMessageContaining("bigint");
            assertThatThrownBy(() -> Json.canonicalStringify(Json.MISSING)).hasMessageContaining("undefined");
            assertThatThrownBy(() -> Json.canonicalStringify(function)).hasMessageContaining("function");
            // Java has no symbols; an opaque object with no JSON form is the nearest value.
            assertThatThrownBy(() -> Json.canonicalStringify(new Object()))
                    .isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void detectsACycleInsteadOfOverflowingTheStack() {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("name", "a");
            a.put("self", a);
            assertThatThrownBy(() -> Json.canonicalStringify(a)).hasMessageContaining("cycle");
        }

        @Test
        void allowsTheSameObjectTwiceWhenItIsNotACycle() {
            Map<String, Object> shared = Map.of("n", 1);
            Map<String, Object> outer = new LinkedHashMap<>();
            outer.put("x", shared);
            outer.put("y", shared);
            assertThat(Json.canonicalStringify(outer)).isEqualTo("{\"x\":{\"n\":1},\"y\":{\"n\":1}}");
        }
    }

    /** The JavaScript froze plain records in place; a JSON value here is immutable from the start. */
    @Nested
    class DeepFreeze {

        @Test
        void freezesNestedObjectsAndArrays() {
            JsonObject frozen = (JsonObject) Json.of(Map.of("a", Map.of("b", List.of(1, 2))));
            JsonObject inner = (JsonObject) frozen.get("a");
            JsonArray list = (JsonArray) inner.get("b");
            assertThatThrownBy(() -> frozen.asMap().put("c", Json.NULL))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> inner.asMap().clear()).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> list.items().add(Json.NULL)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void returnsPrimitivesUntouched() {
            assertThat(Json.of(4)).isEqualTo(new Json.Num(4));
            assertThat(Json.of((Object) null)).isEqualTo(Json.NULL);
        }

        @Test
        void survivesACycle() {
            // A JSON value is a tree, so it cannot hold a cycle; the Java map that would make one is refused.
            List<Object> loop = new ArrayList<>();
            loop.add(loop);
            assertThatThrownBy(() -> Json.of(loop)).hasMessageContaining("cycle");
        }
    }

    @Nested
    class CanonicalClone {

        @Test
        void producesAnEqualButSeparateStructure() {
            JsonObject source = Json.obj("b", Json.arr(1, Json.obj("c", 2)), "a", "x");
            Json copy = Json.canonicalClone(source);
            assertThat(copy).isEqualTo(source);
            JsonObject edited = ((JsonObject) copy).with("b", Json.arr(1, Json.obj("c", 99)));
            assertThat(edited.get("b").at(1).get("c")).isEqualTo(Json.of(99));
            assertThat(source.get("b").at(1).get("c")).isEqualTo(Json.of(2));
        }

        @Test
        void rejectsWhatCanonicalStringifyRejects() {
            assertThatThrownBy(() -> Json.canonicalClone(Json.obj("n", Double.NaN)))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class CanonicalEquals {

        @Test
        void ignoresKeyOrder() {
            assertThat(Json.canonicalEquals(Json.obj("a", 1, "b", 2), Json.obj("b", 2, "a", 1)))
                    .isTrue();
        }

        @Test
        void doesNotIgnoreArrayOrder() {
            assertThat(Json.canonicalEquals(List.of(1, 2), List.of(2, 1))).isFalse();
        }

        @Test
        void separates1From1AsAString() {
            assertThat(Json.canonicalEquals(Json.obj("v", 1), Json.obj("v", "1")))
                    .isFalse();
        }
    }

    @Nested
    class ParseJson {

        @Test
        void parsesValidJson() {
            assertThat(Json.parseJson("{\"a\":1}", "body")).isEqualTo(Json.obj("a", 1));
        }

        @Test
        void turnsASyntaxErrorIntoInvalidArgumentNamingTheField() {
            InvalidArgumentException err =
                    catchThrowableOfType(InvalidArgumentException.class, () -> Json.parseJson("{oops", "deck"));
            assertThat(err).isNotNull();
            assertThat(err.getMessage()).isEqualTo("deck is not valid JSON");
            assertThat(err.detail().get("field")).isEqualTo(Json.of("deck"));
            assertThat(err.detail().get("reason").isText()).isTrue();
        }

        @Test
        void refusesANonString() {
            assertThatThrownBy(() -> Json.parseJson(null, "body")).hasMessageContaining("body must be a string");
        }
    }
}
