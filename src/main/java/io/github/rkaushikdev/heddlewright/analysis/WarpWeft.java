package io.github.rkaushikdev.heddlewright.analysis;

/** One reading taken in each direction of the cloth: down the warp and across the weft. */
public record WarpWeft(double warp, double weft) {}
