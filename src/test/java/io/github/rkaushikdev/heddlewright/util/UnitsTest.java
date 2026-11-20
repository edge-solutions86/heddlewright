package io.github.rkaushikdev.heddlewright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UnitsTest {

    /** Close to a count of decimal places: the difference is under half of ten to the minus that count. */
    private static org.assertj.core.data.Offset<Double> digits(int count) {
        return within(Math.pow(10, -count) / 2);
    }

    @Nested
    class DimensionOf {

        @Test
        void sortsTheUnitsIntoDimensions() {
            assertThat(Units.dimensionOf("cm")).isEqualTo(Units.Dimension.LENGTH);
            assertThat(Units.dimensionOf("kg")).isEqualTo(Units.Dimension.MASS);
            assertThat(Units.dimensionOf("yd2")).isEqualTo(Units.Dimension.AREA);
            assertThat(Units.dimensionOf("cm")).hasToString("length");
        }

        @Test
        void namesWhatItKnowsWhenItDoesNotKnowOne() {
            InvalidArgumentException err =
                    catchThrowableOfType(InvalidArgumentException.class, () -> Units.dimensionOf("furlong"));
            assertThat(err).isNotNull();
            assertThat(((JsonArray) err.detail().get("known")).items()).contains(Json.of("yd"));
        }

        @Test
        void listsItsUnitsSorted() {
            List<String> units = Units.knownUnits();
            assertThat(units).isSorted();
            assertThat(units).contains("in");
        }
    }

    @Nested
    class Convert {

        @Test
        void convertsWithinLengthExactly() {
            assertThat(Units.convert(1, "yd", "m")).isEqualTo(0.9144);
            assertThat(Units.convert(1, "in", "cm")).isEqualTo(2.54);
            assertThat(Units.convert(100, "cm", "m")).isEqualTo(1);
        }

        @Test
        void convertsWithinMassExactlyGivenThePlacesToShowItIn() {
            assertThat(Units.convert(1, "lb", "kg", 12)).isCloseTo(0.45359237, digits(8));
            assertThat(Units.convert(16, "oz", "lb", 12)).isCloseTo(1, digits(8));
        }

        @Test
        void convertsWithinArea() {
            assertThat(Units.convert(1, "yd2", "m2", 12)).isCloseTo(0.83612736, digits(8));
        }

        @Test
        void roundsToSixPlacesByDefaultWhichIsEnoughForAShedDocket() {
            assertThat(Units.convert(1, "lb", "kg")).isEqualTo(0.453592);
        }

        @Test
        void roundTripsWithoutDrifting() {
            for (List<String> pair : List.of(List.of("yd", "m"), List.of("lb", "kg"), List.of("in2", "m2"))) {
                double there = Units.convert(7, pair.get(0), pair.get(1), 12);
                assertThat(Units.convert(there, pair.get(1), pair.get(0), 8)).isCloseTo(7, digits(6));
            }
        }

        @Test
        void refusesTwoUnitsThatDoNotMeasureTheSameThing() {
            assertThatThrownBy(() -> Units.convert(1, "kg", "m")).hasMessageContaining("do not measure the same thing");
            assertThat(Units.comparable("kg", "m")).isFalse();
            assertThat(Units.comparable("kg", "lb")).isTrue();
            assertThat(Units.comparable("kg", "furlong")).isFalse();
        }
    }

    @Nested
    class PerInchAndPerCentimetre {

        @Test
        void convertsASettBothWays() {
            assertThat(Units.perInchToPerCm(25.4)).isEqualTo(10);
            assertThat(Units.perCmToPerInch(10)).isEqualTo(25.4);
        }

        @Test
        void roundTrips() {
            assertThat(Units.perCmToPerInch(Units.perInchToPerCm(60))).isCloseTo(60, digits(3));
        }

        @Test
        void refusesANegativeSett() {
            assertThatThrownBy(() -> Units.perInchToPerCm(-1)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class GsmAndOuncesASquareYard {

        @Test
        void convertsACommonClothWeight() {
            assertThat(Units.gsmToOzYd2(339)).isCloseTo(10, digits(1));
        }

        @Test
        void roundTrips() {
            assertThat(Units.ozYd2ToGsm(Units.gsmToOzYd2(250))).isCloseTo(250, digits(2));
        }

        @Test
        void isNotAPlainUnitConversionBecauseBothPartsChange() {
            assertThat(Units.gsmToOzYd2(339)).isNotCloseTo(Units.convert(339, "g", "oz"), digits(1));
        }
    }

    @Nested
    class Format {

        @Test
        void writesAValueWithItsUnit() {
            assertThat(Units.format(1.23456, "m")).isEqualTo("1.23 m");
            assertThat(Units.format(1.23456, "m", 4)).isEqualTo("1.2346 m");
        }

        @Test
        void refusesAUnitItDoesNotKnow() {
            assertThatThrownBy(() -> Units.format(1, "furlong")).isInstanceOf(InvalidArgumentException.class);
        }
    }
}
