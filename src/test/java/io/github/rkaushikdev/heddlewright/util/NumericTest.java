package io.github.rkaushikdev.heddlewright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NumericTest {

    @Nested
    class GcdAndLcm {

        @Test
        void agreesWithTheWorkedCasesASatinRepeatNeeds() {
            assertThat(Numeric.gcd(8, 12)).isEqualTo(4);
            assertThat(Numeric.gcd(5, 2)).isEqualTo(1);
            assertThat(Numeric.gcd(6, 3)).isEqualTo(3);
            assertThat(Numeric.gcd(0, 7)).isEqualTo(7);
            assertThat(Numeric.gcd(7, 0)).isEqualTo(7);
            assertThat(Numeric.gcd(0, 0)).isEqualTo(0);
        }

        @Test
        void ignoresSign() {
            assertThat(Numeric.gcd(-8, 12)).isEqualTo(4);
            assertThat(Numeric.gcd(8, -12)).isEqualTo(4);
            assertThat(Numeric.gcd(-8, -12)).isEqualTo(4);
        }

        @Test
        void lcmIsTheRepeatLengthOfTwoCyclesRunTogether() {
            assertThat(Numeric.lcm(4, 6)).isEqualTo(12);
            assertThat(Numeric.lcm(5, 8)).isEqualTo(40);
            assertThat(Numeric.lcm(3, 3)).isEqualTo(3);
            assertThat(Numeric.lcm(0, 5)).isEqualTo(0);
        }

        @Test
        void refusesANonInteger() throws NoSuchMethodException {
            // gcd(4.5, 2) cannot be written in Java: the parameters are ints, so the compiler refuses it.
            assertThat(Numeric.class.getMethod("gcd", int.class, int.class)).isNotNull();
            assertThatThrownBy(() -> Numeric.class.getMethod("gcd", double.class, double.class))
                    .isInstanceOf(NoSuchMethodException.class);
        }
    }

    @Nested
    class Coprimality {

        @Test
        void areCoprimeIsGcdOfOne() {
            assertThat(Numeric.areCoprime(5, 2)).isTrue();
            assertThat(Numeric.areCoprime(6, 4)).isFalse();
            assertThat(Numeric.areCoprime(1, 1)).isTrue();
        }

        @Test
        void coprimesBelowGivesTheCandidateMoveNumbersForAnNEndSatin() {
            assertThat(Numeric.coprimesBelow(5)).containsExactly(1, 2, 3, 4);
            assertThat(Numeric.coprimesBelow(8)).containsExactly(1, 3, 5, 7);
            assertThat(Numeric.coprimesBelow(6)).containsExactly(1, 5);
            assertThat(Numeric.coprimesBelow(1)).isEmpty();
        }

        @Test
        void sixHasNoMoveNumberLeftOnce1AndNMinus1AreBarredWhichIsWhyThereIsNoRegularSixEndSatin() {
            List<Integer> usable = Numeric.coprimesBelow(6).stream()
                    .filter(m -> m > 1 && m < 5)
                    .toList();
            assertThat(usable).isEmpty();
            List<Integer> forFive = Numeric.coprimesBelow(5).stream()
                    .filter(m -> m > 1 && m < 4)
                    .toList();
            assertThat(forFive).containsExactly(2, 3);
        }
    }

    @Nested
    class Mod {

        @Test
        void neverReturnsANegativeUnlikeTheOperator() {
            assertThat(-1 % 8).isEqualTo(-1);
            assertThat(Numeric.mod(-1, 8)).isEqualTo(7);
            assertThat(Numeric.mod(9, 8)).isEqualTo(1);
            assertThat(Numeric.mod(-16, 8)).isEqualTo(0);
        }

        @Test
        void needsAPositiveModulus() {
            assertThatThrownBy(() -> Numeric.mod(3, 0)).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Numeric.mod(3, -4)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class Clamp {

        @Test
        void holdsAValueInsideTheRange() {
            assertThat(Numeric.clamp(5, 1, 8)).isEqualTo(5);
            assertThat(Numeric.clamp(-2, 1, 8)).isEqualTo(1);
            assertThat(Numeric.clamp(99, 1, 8)).isEqualTo(8);
        }

        @Test
        void refusesAnInvertedRangeRatherThanSilentlySwappingIt() {
            assertThatThrownBy(() -> Numeric.clamp(5, 8, 1)).hasMessageContaining("min <= max");
        }
    }

    @Nested
    class RoundTo {

        @Test
        void roundsToTheRequestedPlaces() {
            assertThat(Numeric.roundTo(3.14159, 2)).isEqualTo(3.14);
            assertThat(Numeric.roundTo(3.14159, 4)).isEqualTo(3.1416);
            assertThat(Numeric.roundTo(2.5, 0)).isEqualTo(3);
            assertThat(Numeric.roundTo(12.345, 2)).isEqualTo(12.35);
        }

        @Test
        void takesATieAwayFromZeroOnBothSigns() {
            assertThat(Numeric.roundTo(-2.5, 0)).isEqualTo(-3);
            assertThat(Numeric.roundTo(-0.125, 2)).isEqualTo(-0.13);
        }

        @Test
        void defaultsToTwoPlaces() {
            assertThat(Numeric.roundTo(1.23456)).isEqualTo(1.23);
        }
    }

    @Nested
    class SummaryStatistics {

        @Test
        void sumsAndAverages() {
            assertThat(Numeric.sum(List.of(1, 2, 3, 4))).isEqualTo(10);
            assertThat(Numeric.sum(List.of())).isEqualTo(0);
            assertThat(Numeric.mean(List.of(2, 4, 6))).isEqualTo(4);
        }

        @Test
        void meanRefusesAnEmptyListRatherThanReturningNaN() {
            assertThatThrownBy(() -> Numeric.mean(List.of())).isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void stdevIsThePopulationFigureNotTheSampleOne() {
            assertThat(Numeric.stdev(List.of(2, 4, 4, 4, 5, 5, 7, 9))).isEqualTo(2);
            assertThat(Numeric.stdev(List.of(5, 5, 5))).isEqualTo(0);
        }

        @Test
        void percentileInterpolatesBetweenNeighbours() {
            List<Integer> values = List.of(1, 2, 3, 4);
            assertThat(Numeric.percentile(values, 0)).isEqualTo(1);
            assertThat(Numeric.percentile(values, 100)).isEqualTo(4);
            assertThat(Numeric.percentile(values, 50)).isEqualTo(2.5);
            assertThat(Numeric.percentile(List.of(7), 42)).isEqualTo(7);
        }

        @Test
        void percentileDoesNotDisturbTheCallerList() {
            List<Integer> values = new ArrayList<>(List.of(9, 1, 5));
            Numeric.percentile(values, 50);
            assertThat(values).containsExactly(9, 1, 5);
        }

        @Test
        void rejectsAPercentileOutside0To100() {
            assertThatThrownBy(() -> Numeric.percentile(List.of(1, 2), 101))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class SumBy {

        private final List<JsonObject> pieces =
                List.of(Json.obj("yards", 40), Json.obj("yards", 55.5), Json.obj("yards", 4.5));

        @Test
        void addsAFieldUpOverRecords() {
            assertThat(Numeric.sumBy(pieces, piece -> piece.get("yards"))).isEqualTo(100);
        }

        @Test
        void isZeroOverNothing() {
            assertThat(Numeric.sumBy(List.<JsonObject>of(), piece -> piece.get("yards")))
                    .isEqualTo(0);
        }

        @Test
        void handsTheIndexToTheReader() {
            assertThat(Numeric.sumBy(pieces, (piece, index) -> index)).isEqualTo(3);
        }

        @Test
        void namesTheRecordWhoseFieldIsMissingRatherThanGivingBackNaN() {
            List<JsonObject> withHole = List.of(Json.obj("yards", 1), Json.obj(), Json.obj("yards", 3));
            assertThatThrownBy(() -> Numeric.sumBy(withHole, piece -> piece.get("yards")))
                    .isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Numeric.sumBy(withHole, piece -> piece.get("yards")))
                    .hasMessageContaining("items[1]");
        }

        @Test
        void takesTheFieldNameTheCallerUsesForItsOwnRecords() {
            assertThatThrownBy(() -> Numeric.sumBy(List.of(Json.obj()), piece -> piece.get("yards"), "pieces"))
                    .hasMessageContaining("pieces[0]");
        }

        @Test
        void refusesSomethingThatIsNotAList() {
            assertThatThrownBy(() -> Numeric.sumBy((List<Object>) null, piece -> piece))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class MeanBy {

        @Test
        void averagesAFieldOverRecords() {
            List<Map<String, Integer>> entries = List.of(Map.of("n", 2), Map.of("n", 4), Map.of("n", 9));
            assertThat(Numeric.meanBy(entries, entry -> entry.get("n"))).isEqualTo(5);
        }

        @Test
        void readsAnEmptyListAsZeroRatherThanThrowingUnlikeMean() {
            // A run with no readings has no average, and every caller here wants a
            // number back to put in a report rather than an error to handle.
            assertThat(Numeric.meanBy(List.<JsonObject>of(), entry -> entry.get("n")))
                    .isEqualTo(0);
            assertThatThrownBy(() -> Numeric.mean(List.of())).isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void namesTheRecordAtFaultTheSameWay() {
            List<JsonObject> readings = List.of(Json.obj("n", 1), Json.obj());
            assertThatThrownBy(() -> Numeric.meanBy(readings, entry -> entry.get("n"), "readings"))
                    .hasMessageContaining("readings[1]");
        }
    }
}
