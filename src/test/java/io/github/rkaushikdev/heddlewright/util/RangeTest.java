package io.github.rkaushikdev.heddlewright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RangeTest {

    @Nested
    class ParseRange {

        @Test
        void readsSingleValuesAndSpans() {
            assertThat(Range.parseRange("3")).containsExactly(3);
            assertThat(Range.parseRange("1-4")).containsExactly(1, 2, 3, 4);
            assertThat(Range.parseRange("1-3,7,9-10")).containsExactly(1, 2, 3, 7, 9, 10);
        }

        @Test
        void sortsAndDeduplicatesOverlappingParts() {
            assertThat(Range.parseRange("5,1-3,2")).containsExactly(1, 2, 3, 5);
            assertThat(Range.parseRange("4-6,5-7")).containsExactly(4, 5, 6, 7);
        }

        @Test
        void toleratesSpacesAroundPartsAndHyphens() {
            assertThat(Range.parseRange(" 1 - 3 , 8 ")).containsExactly(1, 2, 3, 8);
        }

        @Test
        void readsNegativeValuesWhichADefectOffsetCanBe() {
            assertThat(Range.parseRange("-2--1")).containsExactly(-2, -1);
            assertThat(Range.parseRange("-1,2")).containsExactly(-1, 2);
        }

        @Test
        void rejectsABackwardsSpanRatherThanSilentlyReversingIt() {
            assertThatThrownBy(() -> Range.parseRange("7-3")).hasMessageContaining("counts backwards");
        }

        @Test
        void rejectsEmptyPartsAndUnreadableText() {
            assertThatThrownBy(() -> Range.parseRange("1,,3")).hasMessageContaining("empty part");
            assertThatThrownBy(() -> Range.parseRange("1-")).hasMessageContaining("not a number or a span");
            assertThatThrownBy(() -> Range.parseRange("a-b")).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Range.parseRange("")).isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void enforcesTheBoundsItIsGiven() {
            assertThat(Range.parseRange("1-4", Bounds.between(1, 8))).containsExactly(1, 2, 3, 4);
            assertThatThrownBy(() -> Range.parseRange("0-4", Bounds.atLeast(1))).hasMessageContaining("below 1");
            assertThatThrownBy(() -> Range.parseRange("1-9", Bounds.atMost(8))).hasMessageContaining("above 8");
        }
    }

    @Nested
    class FormatRange {

        @Test
        void foldsConsecutiveRunsIntoSpans() {
            assertThat(Range.formatRange(List.of(1, 2, 3, 7, 9, 10))).isEqualTo("1-3,7,9-10");
            assertThat(Range.formatRange(List.of(4))).isEqualTo("4");
            assertThat(Range.formatRange(List.of())).isEqualTo("");
        }

        @Test
        void sortsAndDeduplicatesFirst() {
            assertThat(Range.formatRange(List.of(3, 1, 2, 2))).isEqualTo("1-3");
        }

        @Test
        void doesNotFoldAPairIntoASpanItCannotShorten() {
            assertThat(Range.formatRange(List.of(1, 2))).isEqualTo("1-2");
            assertThat(Range.formatRange(List.of(1, 3))).isEqualTo("1,3");
        }

        @Test
        void roundTripsWithParseRange() {
            for (String text : List.of("1-3,7,9-10", "2", "1-2,4-5")) {
                assertThat(Range.formatRange(Range.parseRange(text))).isEqualTo(text);
            }
        }

        @Test
        void refusesANonIntegerMember() {
            assertThatThrownBy(() -> Range.formatRange(List.of(1, 2.5))).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class RangeSizeAndRangeIncludes {

        @Test
        void countsTheValuesARangeCovers() {
            assertThat(Range.rangeSize("1-4,9")).isEqualTo(5);
            assertThat(Range.rangeSize("3")).isEqualTo(1);
        }

        @Test
        void answersMembership() {
            assertThat(Range.rangeIncludes("1-4,9", 3)).isTrue();
            assertThat(Range.rangeIncludes("1-4,9", 5)).isFalse();
            assertThat(Range.rangeIncludes("1-4,9", 9)).isTrue();
        }
    }

    @Nested
    class Inclusive {

        @Test
        void countsUpAndDown() {
            assertThat(Range.inclusive(1, 4)).containsExactly(1, 2, 3, 4);
            assertThat(Range.inclusive(4, 1)).containsExactly(4, 3, 2, 1);
            assertThat(Range.inclusive(3, 3)).containsExactly(3);
        }
    }
}
