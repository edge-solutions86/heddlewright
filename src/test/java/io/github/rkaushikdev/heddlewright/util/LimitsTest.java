package io.github.rkaushikdev.heddlewright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LimitsTest {

    @Nested
    class OverAndRoom {

        @Test
        void measureTheTwoSidesOfALimit() {
            assertThat(Limits.over(12, 10)).isEqualTo(2);
            assertThat(Limits.room(12, 10)).isEqualTo(0);
            assertThat(Limits.over(8, 10)).isEqualTo(0);
            assertThat(Limits.room(8, 10)).isEqualTo(2);
        }

        @Test
        void areBothNothingExactlyOnTheLimit() {
            assertThat(Limits.over(10, 10)).isEqualTo(0);
            assertThat(Limits.room(10, 10)).isEqualTo(0);
        }

        @Test
        void neverGoNegativeWhichIsTheMistakeTheyExistToStop() {
            assertThat(Limits.over(1, 100)).isEqualTo(0);
            assertThat(Limits.room(100, 1)).isEqualTo(0);
        }

        @Test
        void refuseSomethingThatIsNotANumber() {
            // over('twelve', 10) cannot be written against a double; NaN is the value that is not a number.
            assertThatThrownBy(() -> Limits.over(Double.NaN, 10)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class Fits {

        @Test
        void isTrueUpToAndIncludingTheLimit() {
            assertThat(Limits.fits(9, 10)).isTrue();
            assertThat(Limits.fits(10, 10)).isTrue();
            assertThat(Limits.fits(11, 10)).isFalse();
        }
    }

    @Nested
    class AssertFits {

        @Test
        void handsTheValueBackWhenItFits() {
            assertThat(Limits.assertFits(9, 10, "chain", "the rail holds ten")).isEqualTo(9);
        }

        @Test
        void throwsWithTheShortfallTheRightWayRound() {
            CapacityException err = catchThrowableOfType(
                    CapacityException.class, () -> Limits.assertFits(14, 10, "chain", "the rail holds ten"));
            assertThat(err).isNotNull();
            assertThat(err.getMessage()).isEqualTo("the rail holds ten");
            assertThat(err.detail()).isEqualTo(Json.obj("what", "chain", "wanted", 14, "available", 10, "over", 4));
        }

        @Test
        void carriesWhateverDetailTheCallerAdds() {
            CapacityException err = catchThrowableOfType(
                    CapacityException.class,
                    () -> Limits.assertFits(14, 10, "chain", "too long", Json.obj("cards", 14)));
            assertThat(err.detail().get("cards")).isEqualTo(Json.of(14));
        }

        @Test
        void needsANameAndAMessage() {
            assertThatThrownBy(() -> Limits.assertFits(1, 10, "", "x")).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Limits.assertFits(1, 10, "x", null)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class AssertAtLeast {

        @Test
        void passesAValueOnOrAboveTheFloor() {
            assertThat(Limits.assertAtLeast(10, 10, "warp", "ten ends")).isEqualTo(10);
        }

        @Test
        void throwsWithHowFarShortItIs() {
            CapacityException err = catchThrowableOfType(
                    CapacityException.class, () -> Limits.assertAtLeast(4, 10, "warp", "ten ends"));
            assertThat(err.detail().get("wanted")).isEqualTo(Json.of(4));
            assertThat(err.detail().get("floor")).isEqualTo(Json.of(10));
            assertThat(err.detail().get("short")).isEqualTo(Json.of(6));
        }
    }

    @Nested
    class Shortfalls {

        @Test
        void reportsEveryPositionThatIsOverAndOnlyThose() {
            assertThat(Limits.shortfalls(List.of(5, 12, 3), List.of(10, 10, 10), "shaft"))
                    .containsExactly(Json.obj("shaft", 2, "wanted", 12, "available", 10, "over", 2));
        }

        @Test
        void isEmptyWhenEverythingFits() {
            assertThat(Limits.shortfalls(List.of(1, 2), List.of(10, 10))).isEmpty();
        }

        @Test
        void labelsThePositionsFromOne() {
            assertThat(Limits.shortfalls(List.of(12), List.of(10)).get(0).get("index"))
                    .isEqualTo(Json.of(1));
        }

        @Test
        void refusesTwoListsOfDifferentLengths() {
            assertThatThrownBy(() -> Limits.shortfalls(List.of(1, 2), List.of(10)))
                    .hasMessageContaining("different lengths");
            assertThatThrownBy(() -> Limits.shortfalls(List.of(1), null)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class WorstShortfall {

        @Test
        void findsTheOneFurthestOver() {
            List<JsonObject> found = Limits.shortfalls(List.of(12, 20, 3), List.of(10, 10, 10));
            assertThat(Limits.worstShortfall(found).orElseThrow().get("over")).isEqualTo(Json.of(10));
        }

        @Test
        void isNullWhenNothingIsOver() {
            assertThat(Limits.worstShortfall(List.of())).isEmpty();
            assertThat(Limits.worstShortfall(null)).isEmpty();
        }
    }
}
