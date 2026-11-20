package io.github.rkaushikdev.heddlewright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CheckTest {

    @Nested
    class Required {

        @Test
        void passesAnythingThatIsNotNullOrUndefinedIncluding0AndEmptyString() {
            assertThat(Check.required(0, "x")).isEqualTo(0);
            assertThat(Check.required("", "x")).isEqualTo("");
            assertThat(Check.required(false, "x")).isEqualTo(false);
        }

        @Test
        void namesTheFieldAndWhichOfTheTwoAbsencesItSaw() {
            assertThatThrownBy(() -> Check.required(null, "shafts")).hasMessageContaining("shafts is required");
            InvalidArgumentException missing =
                    catchThrowableOfType(InvalidArgumentException.class, () -> Check.required(Json.MISSING, "shafts"));
            assertThat(missing.detail()).isEqualTo(Json.obj("field", "shafts", "got", "undefined"));
            InvalidArgumentException nulled =
                    catchThrowableOfType(InvalidArgumentException.class, () -> Check.required(null, "shafts"));
            assertThat(nulled.detail().get("got")).isEqualTo(Json.of("null"));
        }
    }

    @Nested
    class Integer {

        @Test
        void acceptsIntegersAndRejectsEverythingElse() {
            assertThat(Check.integer((Object) 4, "n")).isEqualTo(4);
            assertThat(Check.integer((Object) (-2), "n")).isEqualTo(-2);
            assertThatThrownBy(() -> Check.integer(4.5, "n")).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Check.integer("4", "n")).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Check.integer(Double.NaN, "n")).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Check.integer(Double.POSITIVE_INFINITY, "n"))
                    .isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void enforcesMinAndMaxInclusively() {
            assertThat(Check.integer(1, "n", Bounds.between(1, 8))).isEqualTo(1);
            assertThat(Check.integer(8, "n", Bounds.between(1, 8))).isEqualTo(8);
            assertThatThrownBy(() -> Check.integer(0, "n", Bounds.atLeast(1))).hasMessageContaining("at least 1");
            assertThatThrownBy(() -> Check.integer(9, "n", Bounds.atMost(8))).hasMessageContaining("at most 8");
        }

        @Test
        void reportsTheBoundItBrokeInTheDetail() {
            InvalidArgumentException err = catchThrowableOfType(
                    InvalidArgumentException.class, () -> Check.integer(12, "shafts", Bounds.atMost(8)));
            assertThat(err.detail()).isEqualTo(Json.obj("field", "shafts", "got", 12, "max", 8));
        }
    }

    @Nested
    class IsInteger {

        @Test
        void isThePredicateBehindIntegerWithoutTheThrow() {
            assertThat(Check.isInteger(3)).isTrue();
            assertThat(Check.isInteger(3.1)).isFalse();
            assertThat(Check.isInteger("3")).isFalse();
            assertThat(Check.isInteger(null)).isFalse();
        }
    }

    @Nested
    class PositiveInteger {

        @Test
        void rejectsZeroAndNegatives() {
            assertThat(Check.positiveInteger(1, "n")).isEqualTo(1);
            assertThatThrownBy(() -> Check.positiveInteger(0, "n")).hasMessageContaining("at least 1");
            assertThatThrownBy(() -> Check.positiveInteger(-3, "n")).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class NumberCheck {

        @Test
        void takesFractionsButNotNonFiniteValues() {
            assertThat(Check.number(2.75, "x", Bounds.NONE)).isEqualTo(2.75);
            assertThatThrownBy(() -> Check.number(Double.NaN, "x", Bounds.NONE)).hasMessageContaining("finite number");
            assertThatThrownBy(() -> Check.number(Double.POSITIVE_INFINITY, "x", Bounds.NONE))
                    .hasMessageContaining("finite number");
        }

        @Test
        void positiveNumberExcludesZeroWhereNumberAllowsIt() {
            assertThat(Check.number(0, "x", Bounds.NONE)).isEqualTo(0);
            assertThatThrownBy(() -> Check.positiveNumber(0, "x")).hasMessageContaining("greater than zero");
            assertThat(Check.positiveNumber(0.001, "x")).isEqualTo(0.001);
        }
    }

    @Nested
    class NonEmptyString {

        @Test
        void rejectsWhitespaceOnlyStrings() {
            assertThat(Check.nonEmptyString("twill", "name")).isEqualTo("twill");
            assertThatThrownBy(() -> Check.nonEmptyString("   ", "name")).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Check.nonEmptyString("", "name")).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Check.nonEmptyString(7, "name")).isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void keepsSurroundingWhitespaceRatherThanTrimmingForTheCaller() {
            assertThat(Check.nonEmptyString(" twill ", "name")).isEqualTo(" twill ");
        }
    }

    @Nested
    class OneOf {

        @Test
        void acceptsAMemberAndListsTheAlternativesWhenItDoesNot() {
            assertThat(Check.oneOf("rising", "shed", List.of("rising", "sinking")))
                    .isEqualTo("rising");
            assertThatThrownBy(() -> Check.oneOf("floating", "shed", List.of("rising", "sinking")))
                    .hasMessageContaining("rising, sinking");
        }

        @Test
        void worksFromASetAsWellAsAnArray() {
            LinkedHashSet<String> hands = new LinkedHashSet<>(List.of("S", "Z"));
            assertThat(Check.oneOf("S", "hand", hands)).isEqualTo("S");
            assertThatThrownBy(() -> Check.oneOf("Q", "hand", hands)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class Array {

        @Test
        void checksTheTypeBeforeTheLength() {
            assertThatThrownBy(() -> Check.array("abc", "picks", 1)).hasMessageContaining("must be an array");
        }

        @Test
        void enforcesMinAndMaxLengths() {
            assertThat(Check.array(List.of(1, 2), "picks", 1, 4)).isEqualTo(List.of(1, 2));
            assertThatThrownBy(() -> Check.array(List.of(), "picks", 1)).hasMessageContaining("at least 1 entries");
            assertThatThrownBy(() -> Check.array(List.of(1, 2, 3), "picks", 0, 2))
                    .hasMessageContaining("at most 2 entries");
        }
    }

    @Nested
    class PlainObject {

        @Test
        void rejectsArraysAndNullWhichTypeofCallsObjects() {
            assertThat(Check.plainObject(Map.of("a", 1), "o")).isEqualTo(Json.obj("a", 1));
            assertThatThrownBy(() -> Check.plainObject(List.of(), "o")).hasMessageContaining("must be an object");
            assertThatThrownBy(() -> Check.plainObject(null, "o")).hasMessageContaining("must be an object");
        }
    }

    @Nested
    class Describe {

        @Test
        void summarisesWithoutLeakingALongValueIntoAnErrorMessage() {
            Runnable function = () -> {};
            assertThat(Check.describe(null)).isEqualTo("null");
            assertThat(Check.describe(Json.MISSING)).isEqualTo("undefined");
            assertThat(Check.describe(List.of(1, 2, 3))).isEqualTo("array(3)");
            assertThat(Check.describe("twill")).isEqualTo("\"twill\"");
            assertThat(Check.describe("x".repeat(40))).isEqualTo("string(40)");
            assertThat(Check.describe(12)).isEqualTo("12");
            assertThat(Check.describe(true)).isEqualTo("true");
            assertThat(Check.describe(Map.of())).isEqualTo("object");
            assertThat(Check.describe(function)).isEqualTo("function");
        }
    }
}
