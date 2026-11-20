package io.github.rkaushikdev.heddlewright.cloth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.heddlewright.cloth.Lot.Reading;
import io.github.rkaushikdev.heddlewright.util.ConflictException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LotTest {

    private static final Reading A = shade("P1", 60, 2, -1);
    private static final Reading B = shade("P2", 60.4, 2.1, -1.1);
    private static final Reading C = shade("P3", 64, 3, -2);

    private static Reading shade(String reference, double lightness, double red, double yellow) {
        return Lot.readShade(Json.obj("reference", reference, "lightness", lightness, "red", red, "yellow", yellow));
    }

    @Nested
    class ReadShade {

        @Test
        void takesAReadingOnTheThreeAxes() {
            assertThat(A).isEqualTo(new Reading("P1", 60, 2, -1));
        }

        @Test
        void refusesAReadingWithNoReferenceOrAMissingAxis() {
            assertThatThrownBy(() -> Lot.readShade(Json.obj("lightness", 60, "red", 2, "yellow", 0)))
                    .isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Lot.readShade(Json.obj("reference", "P1", "lightness", 60, "red", 2)))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class ShadeDistance {

        @Test
        void isNothingBetweenAReadingAndItself() {
            assertThat(Lot.shadeDistance(A, A)).isEqualTo(0);
        }

        @Test
        void isTheStraightDistanceInColourSpace() {
            assertThat(Lot.shadeDistance(A, B)).isCloseTo(Math.sqrt(0.16 + 0.01 + 0.01), within(0.00005));
        }

        @Test
        void doesNotCareWhichWayRoundItIsAsked() {
            assertThat(Lot.shadeDistance(A, C)).isEqualTo(Lot.shadeDistance(C, A));
        }
    }

    @Nested
    class OutliersAndLotHolds {

        @Test
        void findsNothingInATightLot() {
            assertThat(Lot.outliers(List.of(A, B), 1)).isEmpty();
            assertThat(Lot.lotHolds(List.of(A, B), 1)).isTrue();
        }

        @Test
        void findsThePairThatIsTooFarApartWorstFirst() {
            List<Lot.Pair> found = Lot.outliers(List.of(A, B, C), 1);
            assertThat(found).hasSize(2);
            assertThat(found.get(0).distance()).isGreaterThan(found.get(1).distance());
            assertThat(Lot.lotHolds(List.of(A, B, C), 1)).isFalse();
        }

        @Test
        void refusesAToleranceOfNothing() {
            assertThatThrownBy(() -> Lot.outliers(List.of(A), 0)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class BuildLots {

        @Test
        void putsPiecesThatMatchIntoOneLot() {
            List<Lot.ShadeLot> lots = Lot.buildLots(List.of(A, B), 1);
            assertThat(lots).hasSize(1);
            assertThat(lots.get(0).pieces()).containsExactly("P1", "P2");
        }

        @Test
        void opensASecondLotForAPieceThatDoesNotMatch() {
            List<Lot.ShadeLot> lots = Lot.buildLots(List.of(A, B, C), 1);
            assertThat(lots).hasSize(2);
            assertThat(lots.get(1).pieces()).containsExactly("P3");
        }

        @Test
        void willNotLetALotDriftAcrossTheSpaceOneStepAtATime() {
            Reading middle = shade("P4", 62, 2.5, -1.5);
            List<Lot.ShadeLot> lots = Lot.buildLots(List.of(A, middle, C), 2.5);
            assertThat(lots.size()).isGreaterThan(1);
            for (Lot.ShadeLot lot : lots) {
                assertThat(lot.spread()).isLessThanOrEqualTo(2.5);
            }
        }

        @Test
        void reportsTheCentreAndTheSpreadOfEachLot() {
            List<Lot.ShadeLot> lots = Lot.buildLots(List.of(A, B), 1);
            assertThat(lots.get(0).centre().lightness()).isEqualTo(60.2);
            assertThat(lots.get(0).spread()).isEqualTo(Lot.shadeDistance(A, B));
        }

        @Test
        void countsTheLotsADeliveryNeeds() {
            assertThat(Lot.lotCount(List.of(A, B, C), 1)).isEqualTo(2);
            assertThat(Lot.lotCount(List.of(A, B, C), 10)).isEqualTo(1);
        }
    }

    @Nested
    class LotCentreAndLotSpread {

        @Test
        void averagesASingleReadingToItself() {
            assertThat(Lot.lotCentre(List.of(A))).isEqualTo(new Lot.Shade(60, 2, -1));
            assertThat(Lot.lotSpread(List.of(A))).isEqualTo(0);
        }

        @Test
        void takesTheWidestGapNotTheLastOne() {
            assertThat(Lot.lotSpread(List.of(A, B, C))).isEqualTo(Lot.shadeDistance(A, C));
        }
    }

    @Nested
    class AssertLotHolds {

        @Test
        void passesALotThatHolds() {
            assertThat(Lot.assertLotHolds(List.of(A, B), 1)).containsExactly(A, B);
        }

        @Test
        void refusesOneThatDoesNotNamingTheWidestPair() {
            ConflictException err =
                    catchThrowableOfType(ConflictException.class, () -> Lot.assertLotHolds(List.of(A, C), 1));
            assertThat(err).isNotNull();
            assertThat(err.detail().get("widest").get("left")).isEqualTo(Json.of("P1"));
        }
    }

    @Nested
    class LotOf {

        @Test
        void findsWhichLotAPieceWentIn() {
            List<Lot.ShadeLot> lots = Lot.buildLots(List.of(A, B, C), 1);
            assertThat(Lot.lotOf(lots, "P2")).isEqualTo(1);
            assertThat(Lot.lotOf(lots, "P3")).isEqualTo(2);
        }

        @Test
        void refusesAPieceNothingHolds() {
            assertThatThrownBy(() -> Lot.lotOf(Lot.buildLots(List.of(A), 1), "P9"))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class ByDyeLot {

        @Test
        void groupsPiecesOnTheLotTheyWereDyedIn() {
            List<?> pieces = List.of(
                    Json.obj("reference", "P1", "lot", "D1"),
                    Json.obj("reference", "P2", "lot", "D1"),
                    Json.obj("reference", "P3", "lot", "D2"));
            assertThat(Lot.byDyeLot(pieces))
                    .containsExactly(new Lot.DyeLot("D1", List.of("P1", "P2")), new Lot.DyeLot("D2", List.of("P3")));
        }

        @Test
        void gathersThePiecesNobodyRecordedALotFor() {
            assertThat(Lot.byDyeLot(List.of(Json.obj("reference", "P1"))))
                    .containsExactly(new Lot.DyeLot("unrecorded", List.of("P1")));
        }
    }
}
