package io.github.rkaushikdev.heddlewright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MatrixTest {

    private static final Matrix PLAIN = Matrix.parse("X.\n.X");

    @Nested
    class CreateMatrix {

        @Test
        void buildsARectangleOfTheRequestedFill() {
            assertThat(Matrix.create(2, 3)).isEqualTo(Matrix.of(new int[][] {{0, 0, 0}, {0, 0, 0}}));
            assertThat(Matrix.create(1, 2, 1)).isEqualTo(Matrix.of(new int[][] {{1, 1}}));
        }

        @Test
        void givesEveryRowItsOwnArray() {
            int[][] cells = Matrix.create(2, 2).toArray();
            cells[0][0] = 1;
            assertThat(cells[1][0]).isEqualTo(0);
        }

        @Test
        void refusesZeroDimensionsAndAFillThatIsNotABit() {
            assertThatThrownBy(() -> Matrix.create(0, 3)).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Matrix.create(2, 0)).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Matrix.create(2, 2, 2)).hasMessageContaining("must be 0 or 1");
        }
    }

    @Nested
    class AssertBit {

        @Test
        void takesOnlyTheTwoNumbersNotTheirTruthyNeighbours() {
            assertThat(Matrix.assertBit(0, "c")).isEqualTo(0);
            assertThat(Matrix.assertBit(1, "c")).isEqualTo(1);
            assertThatThrownBy(() -> Matrix.assertBit(true, "c")).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Matrix.assertBit("1", "c")).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class AssertMatrix {

        @Test
        void rejectsARaggedMatrixAndSaysWhichRow() {
            InvalidArgumentException err = catchThrowableOfType(
                    InvalidArgumentException.class, () -> Matrix.of(List.of(List.of(1, 0), List.of(1))));
            assertThat(err).isNotNull();
            assertThat(err.detail().get("row")).isEqualTo(Json.of(1));
            assertThat(err.detail().get("expected")).isEqualTo(Json.of(2));
            assertThat(err.detail().get("got")).isEqualTo(Json.of(1));
        }

        @Test
        void rejectsAnEmptyMatrixAndAnEmptyRow() {
            assertThatThrownBy(() -> Matrix.of(List.of())).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Matrix.of(List.of(List.of()))).hasMessageContaining("must not be empty");
        }

        @Test
        void rejectsANonBitCellNamingItsPosition() {
            assertThatThrownBy(() -> Matrix.of(new int[][] {{0, 2}})).hasMessageContaining("matrix[0][1]");
        }
    }

    @Nested
    class ShapeReaders {

        @Test
        void reportsDimensionsRowsFirst() {
            assertThat(Matrix.create(3, 5).dimensions()).isEqualTo(new Matrix.Dimensions(3, 5));
        }

        @Test
        void readsAColumnDownAndARowAcross() {
            Matrix m = Matrix.parse("XX.\n..X");
            assertThat(m.column(0)).containsExactly(1, 0);
            assertThat(m.column(2)).containsExactly(0, 1);
            assertThat(m.row(1)).containsExactly(0, 0, 1);
        }

        @Test
        void columnsListsEveryColumnLeftToRight() {
            assertThat(Matrix.parse("X.\n.X").columns()).isEqualTo(List.of(List.of(1, 0), List.of(0, 1)));
        }

        @Test
        void boundsChecksTheIndex() {
            assertThatThrownBy(() -> PLAIN.column(2)).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> PLAIN.row(-1)).isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void handsBackCopiesSoEditingTheResultLeavesTheMatrixAlone() {
            assertThatThrownBy(() -> PLAIN.row(0).set(0, 0)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> PLAIN.column(0).set(0, 0)).isInstanceOf(UnsupportedOperationException.class);
            PLAIN.toArray()[0][0] = 0;
            assertThat(PLAIN.get(0, 0)).isEqualTo(1);
        }
    }

    @Nested
    class MatrixEquals {

        @Test
        void isTrueOnlyForTheSameShapeAndTheSameCells() {
            assertThat(PLAIN).isEqualTo(Matrix.parse("X.\n.X"));
            assertThat(PLAIN).isNotEqualTo(Matrix.parse(".X\nX."));
            assertThat(PLAIN).isNotEqualTo(Matrix.parse("X.\n.X\nX."));
            assertThat(PLAIN).isNotEqualTo(Matrix.parse("X..\n.X."));
        }
    }

    @Nested
    class Transforms {

        private final Matrix m = Matrix.parse("XX.\n..X");

        @Test
        void transposesRowsIntoColumns() {
            assertThat(m.transpose().format()).isEqualTo("X.\nX.\n.X");
            assertThat(m.transpose().transpose()).isEqualTo(m);
        }

        @Test
        void flipsVerticallyAndHorizontally() {
            assertThat(m.flipVertical().format()).isEqualTo("..X\nXX.");
            assertThat(m.flipHorizontal().format()).isEqualTo(".XX\nX..");
        }

        @Test
        void invertsEveryCellWhichIsTheBackOfTheCloth() {
            assertThat(m.invert().format()).isEqualTo("..X\nXX.");
            assertThat(m.invert().invert()).isEqualTo(m);
        }

        @Test
        void flippingDoesNotTouchTheOriginal() {
            String original = m.format();
            m.flipVertical();
            m.flipHorizontal();
            m.invert();
            assertThat(m.format()).isEqualTo(original);
        }
    }

    @Nested
    class RollMatrix {

        private final Matrix m = Matrix.parse("X..\n.X.\n..X");

        @Test
        void movesRowsDownAndColumnsRightOnAPositiveShift() {
            assertThat(m.roll(1, 0).format()).isEqualTo("..X\nX..\n.X.");
            assertThat(m.roll(0, 1).format()).isEqualTo(".X.\n..X\nX..");
        }

        @Test
        void wrapsANegativeShiftTheOtherWay() {
            assertThat(m.roll(-1, 0)).isEqualTo(m.roll(2, 0));
        }

        @Test
        void aShiftOfAWholeRepeatIsTheIdentity() {
            assertThat(m.roll(3, 3)).isEqualTo(m);
            assertThat(m.roll(0, 0)).isEqualTo(m);
        }
    }

    @Nested
    class Tile {

        @Test
        void repeatsTheBlockDownAndAcross() {
            Matrix t = PLAIN.tile(2, 3);
            assertThat(t.dimensions()).isEqualTo(new Matrix.Dimensions(4, 6));
            assertThat(t.format()).isEqualTo("X.X.X.\n.X.X.X\nX.X.X.\n.X.X.X");
        }

        @Test
        void oneByOneGivesAnEqualMatrix() {
            assertThat(PLAIN.tile(1, 1)).isEqualTo(PLAIN);
        }

        @Test
        void needsAtLeastOneRepeatEachWay() {
            assertThatThrownBy(() -> PLAIN.tile(0, 1)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class CountOnes {

        @Test
        void countsRaisedCellsOverTheWholeMatrix() {
            assertThat(PLAIN.countOnes()).isEqualTo(2);
            assertThat(Matrix.create(3, 4, 1).countOnes()).isEqualTo(12);
            assertThat(Matrix.create(3, 4).countOnes()).isEqualTo(0);
        }
    }

    @Nested
    class ParseMatrixAndFormatMatrix {

        @Test
        void roundTripsThroughTheTextForm() {
            String text = "XX..\n..XX\nX..X";
            assertThat(Matrix.parse(text).format()).isEqualTo(text);
        }

        @Test
        void ignoresBlankLinesAndSurroundingSpace() {
            assertThat(Matrix.parse("\n  X.  \n  .X\n\n").format()).isEqualTo("X.\n.X");
        }

        @Test
        void takesDifferentSymbolsWhenAsked() {
            Matrix m = Matrix.parse("10\n01", '1', '0');
            assertThat(m).isEqualTo(PLAIN);
            assertThat(m.format('#', ' ')).isEqualTo("# \n #");
        }

        @Test
        void namesTheCharacterItCouldNotRead() {
            InvalidArgumentException err =
                    catchThrowableOfType(InvalidArgumentException.class, () -> Matrix.parse("X.\n.?"));
            assertThat(err.detail()).isEqualTo(Json.obj("row", 1, "col", 1, "char", "?"));
        }

        @Test
        void refusesEmptyInputAndANonString() {
            assertThatThrownBy(() -> Matrix.parse("   \n\n")).hasMessageContaining("found no rows");
            assertThatThrownBy(() -> Matrix.parse(null)).hasMessageContaining("needs a string");
        }
    }
}
