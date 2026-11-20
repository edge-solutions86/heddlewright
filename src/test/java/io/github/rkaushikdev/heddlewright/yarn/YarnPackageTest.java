package io.github.rkaushikdev.heddlewright.yarn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.yarn.YarnPackage.Kind;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class YarnPackageTest {

    private static final YarnPackage CONE =
            YarnPackage.create(Json.obj("grossKg", 2.05, "tareKg", 0.05, "count", 20, "system", "tex"));

    @Nested
    class CreatePackage {

        @Test
        void worksTheNetWeightAndTheLengthOutFromTheCount() {
            assertThat(CONE.netKg()).isEqualTo(2);
            assertThat(CONE.metres()).isEqualTo(100000);
            assertThat(CONE.kind()).isEqualTo(Kind.CONE);
        }

        @Test
        void namesTheKindsItKnows() {
            assertThat(Kind.PACKAGE_KINDS.stream().map(Kind::toString))
                    .containsExactly("cone", "cheese", "pirn", "spool", "beam");
            JsonObject sack = ((JsonObject) Json.of(CONE)).with("kind", "sack");
            assertThatThrownBy(() -> YarnPackage.create(sack)).isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void readsACountInAnotherSystem() {
            YarnPackage inNe = YarnPackage.create(Json.obj("grossKg", 2, "count", 30, "system", "ne"));
            assertThat(inNe.metres()).isGreaterThan(CONE.metres());
        }

        @Test
        void refusesPackingThatWeighsAsMuchAsTheYarn() {
            assertThatThrownBy(() -> YarnPackage.create(Json.obj("grossKg", 1, "tareKg", 1, "count", 20)))
                    .hasMessageContaining("packing weighs");
        }

        @Test
        void freezesTheRecord() {
            // A record's fields are final, so a package cannot be edited once it is described.
            assertThat(YarnPackage.class.isRecord()).isTrue();
            assertThat(YarnPackage.create(Json.of(CONE))).isEqualTo(CONE);
        }
    }

    @Nested
    class RunsForMinutes {

        @Test
        void dividesTheLengthByTheRateItIsUsedAt() {
            assertThat(CONE.runsForMinutes(500)).isEqualTo(200);
        }

        @Test
        void refusesARateOfNothing() {
            assertThatThrownBy(() -> CONE.runsForMinutes(0)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class PackagesFor {

        @Test
        void roundsUpToAWholePackageAndSaysWhatIsLeftOnTheLast() {
            YarnPackage.Needed needed = CONE.packagesFor(250000);
            assertThat(needed.packages()).isEqualTo(3);
            assertThat(needed.leftOverMetres()).isEqualTo(50000);
            assertThat(needed.totalKg()).isEqualTo(6);
        }

        @Test
        void needsOnePackageForAnythingUpToItsLength() {
            assertThat(CONE.packagesFor(100000).packages()).isEqualTo(1);
            assertThat(CONE.packagesFor(100001).packages()).isEqualTo(2);
        }

        @Test
        void leavesNothingOverWhenTheLengthDividesExactly() {
            assertThat(CONE.packagesFor(200000).leftOverMetres()).isEqualTo(0);
        }
    }

    @Nested
    class UsableMetres {

        @Test
        void takesTheTailOff() {
            assertThat(CONE.usableMetres()).isEqualTo(98000);
            assertThat(CONE.usableMetres(0)).isEqualTo(100000);
        }

        @Test
        void refusesAWasteShareOverEverything() {
            assertThatThrownBy(() -> CONE.usableMetres(101)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class AssertFitsCreel {

        @Test
        void passesAPackageThePegTakes() {
            assertThat(CONE.assertFitsCreel(3)).isSameAs(CONE);
        }

        @Test
        void refusesOneThatIsTooHeavy() {
            assertThatThrownBy(() -> CONE.assertFitsCreel(1)).isInstanceOf(CapacityException.class);
        }

        @Test
        void refusesOneThatIsTooWideWhenAWidthIsGiven() {
            assertThatThrownBy(() -> CONE.assertFitsCreel(3, OptionalDouble.of(150), OptionalDouble.of(200)))
                    .hasMessageContaining("wider than the creel");
        }

        @Test
        void ignoresTheWidthWhenNobodyMeasuredIt() {
            assertThat(CONE.assertFitsCreel(3, OptionalDouble.of(150), OptionalDouble.empty()))
                    .isSameAs(CONE);
        }
    }

    @Nested
    class DeliveryTotalsAndDeliveryCost {

        private final List<YarnPackage> delivery =
                List.of(CONE, CONE, YarnPackage.create(Json.obj("grossKg", 1.05, "tareKg", 0.05, "count", 20)));

        @Test
        void addsTheWeightsAndTheLengths() {
            YarnPackage.Totals totals = YarnPackage.deliveryTotals(delivery);
            assertThat(totals.packages()).isEqualTo(3);
            assertThat(totals.netKg()).isEqualTo(5);
            assertThat(totals.metres()).isEqualTo(250000);
        }

        @Test
        void pricesOnTheNetWeightNotTheGross() {
            assertThat(YarnPackage.deliveryCost(delivery, 4)).isEqualTo(20);
        }

        @Test
        void refusesAnEmptyDelivery() {
            assertThatThrownBy(() -> YarnPackage.deliveryTotals(List.of()))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }
}
