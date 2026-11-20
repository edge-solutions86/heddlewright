package io.github.rkaushikdev.heddlewright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.Table.Align;
import io.github.rkaushikdev.heddlewright.util.Table.Column;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TableTest {

    private static final List<JsonObject> ROWS = List.of(
            Json.obj("shaft", 1, "ends", 12, "note", "ground"), Json.obj("shaft", 2, "ends", 4, "note", "selvedge"));

    private static List<String> lines(String text) {
        return Arrays.asList(text.split("\n", -1));
    }

    @Nested
    class RenderTable {

        @Test
        void alignsNumbersRightAndTextLeftByDefault() {
            String text = Table.renderTable(ROWS, List.of(Column.of("shaft"), Column.of("ends"), Column.of("note")));
            assertThat(lines(text))
                    .containsExactly(
                            "shaft  ends  note",
                            "-----  ----  --------",
                            "    1    12  ground",
                            "    2     4  selvedge");
        }

        @Test
        void takesAnExplicitLabelAndAlignment() {
            String text = Table.renderTable(
                    ROWS,
                    List.of(
                            Column.of("note").labelled("Purpose"),
                            Column.of("ends").labelled("N").aligned(Align.LEFT)));
            assertThat(lines(text).get(0)).isEqualTo("Purpose   N");
            assertThat(lines(text).get(2)).isEqualTo("ground    12");
        }

        @Test
        void widensAColumnToFitItsLongestValue() {
            String text = Table.renderTable(
                    List.of(Json.obj("a", "x"), Json.obj("a", "a much longer value")), List.of(Column.of("a")));
            assertThat(lines(text).get(1)).isEqualTo("-".repeat("a much longer value".length()));
        }

        @Test
        void dropsTheRuleWhenAskedAndHonoursTheGap() {
            String text = Table.renderTable(ROWS, List.of(Column.of("shaft"), Column.of("ends")), 1, false);
            assertThat(lines(text)).containsExactly("shaft ends", "    1   12", "    2    4");
        }

        @Test
        void rendersAHeaderOnItsOwnWhenThereAreNoRows() {
            assertThat(Table.renderTable(List.of(), List.of(Column.of("shaft"))))
                    .isEqualTo("shaft\n-----");
        }

        @Test
        void writesAnEmptyCellForAMissingOrNullValue() {
            String text = Table.renderTable(
                    List.of(Json.obj("a", "x", "b", Json.NULL), Json.obj("a", "y")),
                    List.of(Column.of("a"), Column.of("b")));
            assertThat(lines(text).get(2)).isEqualTo("x");
        }

        @Test
        void joinsAnArrayCellWithSpacesAndTrimsAFraction() {
            String text = Table.renderTable(
                    List.of(Json.obj("shafts", List.of(1, 2, 3), "ratio", 1.23456789)),
                    List.of(Column.of("shafts"), Column.of("ratio")));
            assertThat(lines(text).get(2)).isEqualTo("1 2 3   1.2346");
        }

        @Test
        void neverLeavesTrailingSpacesOnALine() {
            String text = Table.renderTable(ROWS, List.of(Column.of("shaft"), Column.of("note")));
            for (String line : lines(text)) {
                assertThat(line).isEqualTo(Text.trimEnd(line));
            }
        }

        @Test
        void checksItsArguments() {
            assertThatThrownBy(() -> Table.renderTable(ROWS, List.of())).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() ->
                            Table.renderTable(ROWS, List.of(Column.of("shaft").aligned(Align.parse("centre")))))
                    .hasMessageContaining("left, right");
            assertThatThrownBy(() -> Table.renderTable(ROWS, Arrays.asList((Column) null)))
                    .hasMessageContaining("is required");
        }
    }

    @Nested
    class RenderPairs {

        @Test
        void padsLabelsToACommonWidth() {
            assertThat(Table.renderPairs(Json.obj("shafts", 8, "treadles", 10))).isEqualTo("shafts  : 8\ntreadles: 10");
        }

        @Test
        void takesAMapAsWellAsAnObject() {
            Map<String, Integer> map = new LinkedHashMap<>();
            map.put("a", 1);
            assertThat(Table.renderPairs(map)).isEqualTo("a: 1");
        }

        @Test
        void isEmptyForNothingToShow() {
            assertThat(Table.renderPairs(Json.obj())).isEqualTo("");
            assertThat(Table.renderPairs(null)).isEqualTo("");
        }

        @Test
        void takesADifferentSeparator() {
            assertThat(Table.renderPairs(Json.obj("a", 1), " = ")).isEqualTo("a = 1");
        }
    }

    @Nested
    class WrapText {

        @Test
        void breaksBetweenWordsAtTheGivenWidth() {
            assertThat(Table.wrapText("one two three four", 9)).isEqualTo("one two\nthree\nfour");
        }

        @Test
        void leavesAWordLongerThanTheWidthOnItsOwnLine() {
            assertThat(Table.wrapText("a supercalifragilistic b", 5)).isEqualTo("a\nsupercalifragilistic\nb");
        }

        @Test
        void collapsesRunsOfWhitespace() {
            assertThat(Table.wrapText("  one   two  ", 40)).isEqualTo("one two");
        }

        @Test
        void isEmptyForEmptyInput() {
            assertThat(Table.wrapText("   ")).isEqualTo("");
        }
    }
}
