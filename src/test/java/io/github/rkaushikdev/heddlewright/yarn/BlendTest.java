package io.github.rkaushikdev.heddlewright.yarn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.yarn.Blend.Part;
import io.github.rkaushikdev.heddlewright.yarn.Blend.Share;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BlendTest {

    private static final List<Part> POLYCOTTON = List.of(new Part("cotton", 65), new Part("polyester", 35));

    private static final List<Part> THIRDS = List.of(new Part("a", 1), new Part("b", 1), new Part("c", 1));

    @Nested
    class NormaliseBlend {

        @Test
        void turnsTheSharesIntoFractionsThatAddToOne() {
            List<Part> parts = Blend.normaliseBlend(POLYCOTTON);
            assertThat(parts.get(0).share()).isCloseTo(0.65, within(0.5e-6));
            assertThat(parts.stream().mapToDouble(Part::share).sum()).isCloseTo(1, within(0.5e-10));
        }

        @Test
        void normalisesSharesThatWereNeverPercentages() {
            List<Part> parts = Blend.normaliseBlend(List.of(new Part("wool", 3), new Part("nylon", 1)));
            assertThat(parts.get(0).share()).isEqualTo(0.75);
        }

        @Test
        void refusesTheSameFibreTwice() {
            List<Part> doubled = new ArrayList<>(POLYCOTTON);
            doubled.add(new Part("cotton", 5));
            assertThatThrownBy(() -> Blend.normaliseBlend(doubled)).hasMessageContaining("in the blend twice");
        }

        @Test
        void refusesAShareOfNothingAndAnEmptyBlend() {
            assertThatThrownBy(() -> Blend.normaliseBlend(List.of(new Part("cotton", 0))))
                    .isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Blend.normaliseBlend(List.of())).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class AddsToWhole {

        @Test
        void isTrueWhenTheSharesAreAlreadyPercentages() {
            assertThat(Blend.addsToWhole(POLYCOTTON)).isTrue();
        }

        @Test
        void isFalseWhenTheyAreNot() {
            assertThat(Blend.addsToWhole(List.of(new Part("wool", 3)))).isFalse();
        }
    }

    @Nested
    class LabelSharesAndLabelText {

        @Test
        void writesWholePercentagesLargestFirst() {
            assertThat(Blend.labelShares(POLYCOTTON))
                    .containsExactly(new Share("cotton", 65), new Share("polyester", 35));
        }

        @Test
        void alwaysAddsToAHundredEvenWhenTheRoundingDoesNot() {
            assertThat(Blend.labelShares(THIRDS).stream()
                            .mapToInt(Share::percent)
                            .sum())
                    .isEqualTo(100);
        }

        @Test
        void givesTheRoundingRemainderToTheLargestFibre() {
            assertThat(Blend.labelShares(THIRDS).get(0).percent()).isEqualTo(34);
        }

        @Test
        void writesTheTicket() {
            assertThat(Blend.labelText(POLYCOTTON)).isEqualTo("65% cotton 35% polyester");
        }
    }

    @Nested
    class WeightedProperty {

        @Test
        void averagesByWeightNotByFibreCount() {
            double regain = Blend.weightedProperty(POLYCOTTON, Map.of("cotton", 8.5, "polyester", 0.4));
            assertThat(regain).isCloseTo(0.65 * 8.5 + 0.35 * 0.4, within(0.5e-4));
        }

        @Test
        void refusesAFibreWithNoValueAgainstIt() {
            assertThatThrownBy(() -> Blend.weightedProperty(POLYCOTTON, Map.of("cotton", 8.5)))
                    .hasMessageContaining("no value given for");
        }

        @Test
        void isTheValueItselfForASingleFibre() {
            assertThat(Blend.weightedProperty(List.of(new Part("cotton", 100)), Map.of("cotton", 8.5)))
                    .isEqualTo(8.5);
        }
    }

    @Nested
    class BatchWeights {

        @Test
        void splitsABatchByTheBlend() {
            assertThat(Blend.batchWeights(POLYCOTTON, 1000))
                    .containsExactly(new Blend.Weight("cotton", 650), new Blend.Weight("polyester", 350));
        }

        @Test
        void addsBackToTheBatch() {
            List<Blend.Weight> weights = Blend.batchWeights(POLYCOTTON, 777);
            assertThat(weights.stream().mapToDouble(Blend.Weight::kilograms).sum())
                    .isCloseTo(777, within(0.5e-3));
        }
    }

    @Nested
    class ConditionedWeightAndBlendRegain {

        @Test
        void addsTheAgreedRegainToTheDryWeight() {
            assertThat(Blend.conditionedWeight(1000, 8.5)).isEqualTo(1085);
            assertThat(Blend.conditionedWeight(1000, 0)).isEqualTo(1000);
        }

        @Test
        void averagesTheRegainAcrossTheBlend() {
            double regain = Blend.blendRegain(POLYCOTTON, Map.of("cotton", 8.5, "polyester", 0.4));
            assertThat(regain).isLessThan(8.5).isGreaterThan(0.4);
        }

        @Test
        void refusesANegativeRegain() {
            assertThatThrownBy(() -> Blend.conditionedWeight(1000, -1)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class WithinTolerance {

        @Test
        void passesABlendOnTarget() {
            assertThat(Blend.withinTolerance(POLYCOTTON, POLYCOTTON).within()).isTrue();
        }

        @Test
        void passesOneInsideTheAllowance() {
            List<Part> drifted = List.of(new Part("cotton", 67), new Part("polyester", 33));
            assertThat(Blend.withinTolerance(drifted, POLYCOTTON).within()).isTrue();
        }

        @Test
        void failsOneOutsideItNamingTheFibreAndHowFarOff() {
            List<Part> drifted = List.of(new Part("cotton", 55), new Part("polyester", 45));
            Blend.Tolerance reading = Blend.withinTolerance(drifted, POLYCOTTON);
            assertThat(reading.within()).isFalse();
            assertThat(reading.findings().get(0)).isEqualTo(new Blend.Finding("cotton", 65, 55, -10));
        }

        @Test
        void countsAFibreMissingAltogetherAsNothing() {
            Blend.Tolerance reading = Blend.withinTolerance(List.of(new Part("cotton", 100)), POLYCOTTON);
            assertThat(reading.findings()).anyMatch(finding -> finding.fibre().equals("polyester"));
        }
    }
}
