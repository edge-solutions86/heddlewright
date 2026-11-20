package io.github.rkaushikdev.heddlewright.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A binary matrix, rows by columns, every cell 0 or 1.
 *
 * <p>A drawdown, a liftplan, a tie-up and a punched card are all one of these, so every rotation,
 * mirror and comparison in the package lands here. Rows are picks and columns are ends, unless a
 * caller says otherwise. A matrix is immutable: every transform hands back a new one.
 */
public final class Matrix implements Json.Writable {

    /** How many rows and columns a matrix has, rows first. */
    public record Dimensions(int rows, int cols) {}

    private final int[][] cells;

    private Matrix(int[][] cells) {
        this.cells = cells;
    }

    /** A matrix of zeros. */
    public static Matrix create(int rows, int cols) {
        return create(rows, cols, 0);
    }

    /** A rectangle of the requested fill. */
    public static Matrix create(int rows, int cols, int fill) {
        Check.positiveInteger(rows, "rows");
        Check.positiveInteger(cols, "cols");
        assertBit(fill, "fill");
        int[][] cells = new int[rows][cols];
        for (int[] row : cells) {
            Arrays.fill(row, fill);
        }
        return new Matrix(cells);
    }

    /** Check a value is 0 or 1 and return it; a boolean or a string of a digit is neither. */
    public static int assertBit(Object value, String field) {
        if (value instanceof Json.Num number) {
            return assertBit(number.value(), field);
        }
        if (value instanceof Number number && (number.doubleValue() == 0 || number.doubleValue() == 1)) {
            return number.intValue();
        }
        throw new InvalidArgumentException(field + " must be 0 or 1", Json.obj("field", field, "got", value));
    }

    /** Check a cell is 0 or 1 and return it. */
    public static int assertBit(int value, String field) {
        if (value != 0 && value != 1) {
            throw new InvalidArgumentException(field + " must be 0 or 1", Json.obj("field", field, "got", value));
        }
        return value;
    }

    /** Build a matrix from rows of bits, checking it really is rectangular and binary. */
    public static Matrix of(int[][] rows) {
        return of(rows, "matrix");
    }

    /** Build a matrix from rows of bits held in any list or array form, or from JSON. */
    public static Matrix of(Object rows) {
        return of(rows, "matrix");
    }

    /** {@link #of(Object)}, naming the field in any complaint. */
    public static Matrix of(Object rows, String field) {
        if (rows instanceof Matrix matrix) {
            return matrix;
        }
        if (rows instanceof int[][] grid && grid.length > 0 && grid[0].length > 0) {
            return ofGrid(grid, field);
        }
        List<Object> list = Check.array(rows, field, 1);
        List<Object> first = Check.array(list.get(0), field + "[0]");
        int cols = first.size();
        if (cols == 0) {
            throw new InvalidArgumentException(field + " rows must not be empty", Json.obj("field", field));
        }
        int[][] cells = new int[list.size()][];
        for (int r = 0; r < list.size(); r += 1) {
            List<Object> row = Check.array(list.get(r), field + "[" + r + "]");
            if (row.size() != cols) {
                throw new InvalidArgumentException(
                        field + " is ragged", Json.obj("field", field, "row", r, "expected", cols, "got", row.size()));
            }
            cells[r] = new int[cols];
            for (int c = 0; c < cols; c += 1) {
                cells[r][c] = assertBit(row.get(c), field + "[" + r + "][" + c + "]");
            }
        }
        return new Matrix(cells);
    }

    private static Matrix ofGrid(int[][] grid, String field) {
        int cols = grid[0].length;
        int[][] cells = new int[grid.length][];
        for (int r = 0; r < grid.length; r += 1) {
            if (grid[r].length != cols) {
                throw new InvalidArgumentException(
                        field + " is ragged",
                        Json.obj("field", field, "row", r, "expected", cols, "got", grid[r].length));
            }
            for (int c = 0; c < cols; c += 1) {
                assertBit(grid[r][c], field + "[" + r + "][" + c + "]");
            }
            cells[r] = grid[r].clone();
        }
        return new Matrix(cells);
    }

    /** How many rows. */
    public int rows() {
        return cells.length;
    }

    /** How many columns. */
    public int cols() {
        return cells[0].length;
    }

    /** The shape, rows first. */
    public Dimensions dimensions() {
        return new Dimensions(rows(), cols());
    }

    /** One cell, zero-based. */
    public int get(int row, int col) {
        return cells[row][col];
    }

    /** Whether a cell is set, zero-based. */
    public boolean isSet(int row, int col) {
        return cells[row][col] == 1;
    }

    /** One column down the matrix, zero-based. */
    public List<Integer> column(int index) {
        Check.integer(index, "index", Bounds.between(0, cols() - 1));
        List<Integer> out = new ArrayList<>(rows());
        for (int[] row : cells) {
            out.add(row[index]);
        }
        return List.copyOf(out);
    }

    /** One row across the matrix, zero-based. */
    public List<Integer> row(int index) {
        Check.integer(index, "index", Bounds.between(0, rows() - 1));
        return listOf(cells[index]);
    }

    /** Every row, top to bottom. */
    public List<List<Integer>> allRows() {
        List<List<Integer>> out = new ArrayList<>(rows());
        for (int[] row : cells) {
            out.add(listOf(row));
        }
        return List.copyOf(out);
    }

    /** Every column, left to right. */
    public List<List<Integer>> columns() {
        List<List<Integer>> out = new ArrayList<>(cols());
        for (int c = 0; c < cols(); c += 1) {
            out.add(column(c));
        }
        return List.copyOf(out);
    }

    /** A copy of the cells, for an algorithm that wants to index them directly. */
    public int[][] toArray() {
        int[][] copy = new int[rows()][];
        for (int r = 0; r < rows(); r += 1) {
            copy[r] = cells[r].clone();
        }
        return copy;
    }

    /** Swap rows for columns. A liftplan transposed is a shaft-by-pick view. */
    public Matrix transpose() {
        int[][] out = new int[cols()][rows()];
        for (int r = 0; r < rows(); r += 1) {
            for (int c = 0; c < cols(); c += 1) {
                out[c][r] = cells[r][c];
            }
        }
        return new Matrix(out);
    }

    /** Reverse the order of the rows, which turns a drawdown upside down. */
    public Matrix flipVertical() {
        int[][] out = new int[rows()][];
        for (int r = 0; r < rows(); r += 1) {
            out[r] = cells[rows() - 1 - r].clone();
        }
        return new Matrix(out);
    }

    /** Reverse the order of the columns, which mirrors a draft left to right. */
    public Matrix flipHorizontal() {
        int[][] out = new int[rows()][cols()];
        for (int r = 0; r < rows(); r += 1) {
            for (int c = 0; c < cols(); c += 1) {
                out[r][c] = cells[r][cols() - 1 - c];
            }
        }
        return new Matrix(out);
    }

    /** Swap every 0 for a 1. On a drawdown this is the reverse of the cloth. */
    public Matrix invert() {
        int[][] out = new int[rows()][cols()];
        for (int r = 0; r < rows(); r += 1) {
            for (int c = 0; c < cols(); c += 1) {
                out[r][c] = 1 - cells[r][c];
            }
        }
        return new Matrix(out);
    }

    /** Move rows down and columns right by a signed offset, wrapping round. */
    public Matrix roll(int rowShift, int colShift) {
        int rows = rows();
        int cols = cols();
        int[][] out = new int[rows][cols];
        for (int r = 0; r < rows; r += 1) {
            for (int c = 0; c < cols; c += 1) {
                out[r][c] = cells[Math.floorMod(r - rowShift, rows)][Math.floorMod(c - colShift, cols)];
            }
        }
        return new Matrix(out);
    }

    /** Repeat the matrix across and down, the way a repeat covers a cloth width. */
    public Matrix tile(int downRepeats, int acrossRepeats) {
        Check.positiveInteger(downRepeats, "downRepeats");
        Check.positiveInteger(acrossRepeats, "acrossRepeats");
        int[][] out = new int[rows() * downRepeats][cols() * acrossRepeats];
        for (int r = 0; r < out.length; r += 1) {
            for (int c = 0; c < out[r].length; c += 1) {
                out[r][c] = cells[r % rows()][c % cols()];
            }
        }
        return new Matrix(out);
    }

    /** Count the set cells, which is the raised-end count over a whole repeat. */
    public int countOnes() {
        int total = 0;
        for (int[] row : cells) {
            for (int cell : row) {
                total += cell;
            }
        }
        return total;
    }

    /** Read a matrix out of a string of rows, X for a set cell and a dot for a clear one. */
    public static Matrix parse(String text) {
        return parse(text, 'X', '.');
    }

    /** Read a matrix out of a string of rows, using {@code on} for a set cell. */
    public static Matrix parse(String text, char on, char off) {
        if (text == null) {
            throw new InvalidArgumentException("parseMatrix needs a string", Json.obj("got", "object"));
        }
        List<String> lines = Text.split(text, "\n").stream()
                .map(Text::trim)
                .filter(line -> !line.isEmpty())
                .toList();
        if (lines.isEmpty()) {
            throw new InvalidArgumentException("parseMatrix found no rows", JsonObject.EMPTY);
        }
        List<List<Integer>> rows = new ArrayList<>(lines.size());
        for (int r = 0; r < lines.size(); r += 1) {
            String line = lines.get(r);
            List<Integer> row = new ArrayList<>(line.length());
            for (int c = 0; c < line.length(); c += 1) {
                char ch = line.charAt(c);
                if (ch == on) {
                    row.add(1);
                } else if (ch == off) {
                    row.add(0);
                } else {
                    throw new InvalidArgumentException(
                            "unexpected character in row " + r,
                            Json.obj("row", r, "col", c, "char", String.valueOf(ch)));
                }
            }
            rows.add(row);
        }
        return of(rows);
    }

    /** The text form {@link #parse(String)} reads. */
    public String format() {
        return format('X', '.');
    }

    /** The text form, with the given symbols. */
    public String format(char on, char off) {
        StringBuilder out = new StringBuilder(rows() * (cols() + 1));
        for (int r = 0; r < rows(); r += 1) {
            if (r > 0) {
                out.append('\n');
            }
            for (int cell : cells[r]) {
                out.append(cell == 1 ? on : off);
            }
        }
        return out.toString();
    }

    /** The rows as JSON arrays of 0 and 1. */
    @Override
    public Json toJson() {
        return Json.of(cells);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Matrix matrix && Arrays.deepEquals(cells, matrix.cells);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(cells);
    }

    /** The text form. */
    @Override
    public String toString() {
        return format();
    }

    private static List<Integer> listOf(int[] row) {
        List<Integer> out = new ArrayList<>(row.length);
        for (int cell : row) {
            out.add(cell);
        }
        return List.copyOf(out);
    }
}
