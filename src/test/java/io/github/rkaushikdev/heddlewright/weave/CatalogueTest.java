package io.github.rkaushikdev.heddlewright.weave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.heddlewright.analysis.Interlacement.Firmness;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import io.github.rkaushikdev.heddlewright.weave.Catalogue.Measurement;
import io.github.rkaushikdev.heddlewright.weave.Catalogue.Weave;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CatalogueTest {

    @AfterEach
    void forgetTheShedSpecial() {
        Catalogue.removeWeave("shed special");
    }

    @Nested
    class ListWeaves {

        @Test
        void listsThemInNameOrder() {
            List<String> names =
                    Catalogue.listWeaves().stream().map(Weave::name).toList();
            assertThat(names).isSorted();
        }

        @Test
        void givesEveryWeaveAFamilyAndABuilder() {
            for (Weave weave : Catalogue.listWeaves()) {
                assertThat(weave.family()).isNotBlank();
                assertThat(weave.build()).isNotNull();
            }
        }

        @Test
        void groupsIntoTheFamiliesTheReportUses() {
            assertThat(Catalogue.families()).containsExactly("block", "crepe", "figured", "plain", "satin", "twill");
            assertThat(Catalogue.weavesInFamily("satin")).hasSize(3);
            assertThat(Catalogue.weavesInFamily("nothing")).isEmpty();
        }

        @Test
        void carriesTheBlockWeavesAsWovenClothRatherThanAsDrafts() {
            Measurement overshot = Catalogue.measure("overshot ABCD");
            assertThat(overshot.picks()).isEqualTo(16);
            assertThat(overshot.ends()).isEqualTo(16);
            assertThat(overshot.warpFace()).isEqualTo(0.5);
        }

        @Test
        void carriesTheCrepesAndReadsThemAsHavingNoLongLine() {
            for (Weave weave : Catalogue.weavesInFamily("crepe")) {
                assertThat(Catalogue.measure(weave.name()).longestFloat()).isLessThanOrEqualTo(3);
            }
        }
    }

    @Nested
    class WeaveByName {

        @Test
        void buildsOneRepeat() {
            assertThat(Catalogue.weaveByName("plain")).isEqualTo(Plain.plainWeave());
        }

        @Test
        void namesWhatItDoesHaveWhenItCannotFindOne() {
            NotFoundException err = catchThrowableOfType(NotFoundException.class, () -> Catalogue.weaveByName("gauze"));
            assertThat(err).isNotNull();
            assertThat(((JsonArray) err.detail().get("known")).items()).contains(Json.of("plain"));
        }

        @Test
        void buildsAFreshRepeatEachTimeSoACallerCannotEditTheCatalogue() {
            int[][] first = Catalogue.weaveByName("plain").toArray();
            first[0][0] = 0;
            assertThat(Catalogue.weaveByName("plain").get(0, 0)).isEqualTo(1);
        }
    }

    @Nested
    class Measure {

        @Test
        void readsPlainWeaveAsTheFirmestThingInTheBook() {
            Measurement plain = Catalogue.measure("plain");
            assertThat(plain.family()).isEqualTo("plain");
            assertThat(plain.picks()).isEqualTo(2);
            assertThat(plain.ends()).isEqualTo(2);
            assertThat(plain.warpFace()).isEqualTo(0.5);
            assertThat(plain.longestFloat()).isEqualTo(1);
            assertThat(plain.interlacement()).isEqualTo(1);
            assertThat(plain.firmness()).isEqualTo(Firmness.FIRM);
        }

        @Test
        void readsASatinAsLongFloatingAndOpen() {
            Measurement satin = Catalogue.measure("satin 8");
            assertThat(satin.longestFloat()).isEqualTo(7);
            assertThat(satin.warpFace()).isEqualTo(0.875);
            assertThat(satin.firmness()).isEqualTo(Firmness.OPEN);
        }

        @Test
        void readsAWarpFacedTwillAsShowingMoreWarp() {
            assertThat(Catalogue.measure("twill 3/1").warpFace()).isEqualTo(0.75);
            assertThat(Catalogue.measure("twill 1/3").warpFace()).isEqualTo(0.25);
        }
    }

    @Nested
    class MeasureAll {

        @Test
        void measuresEveryWeaveInTheCatalogue() {
            assertThat(Catalogue.measureAll()).hasSameSizeAs(Catalogue.listWeaves());
        }

        @Test
        void neverReportsAFloatLongerThanTheRepeatItCameFrom() {
            for (Measurement row : Catalogue.measureAll()) {
                assertThat(row.longestFloat()).isLessThanOrEqualTo(Math.max(row.picks(), row.ends()));
            }
        }

        @Test
        void reportsAnInterlacementBetweenNothingAndOneForEveryWeave() {
            for (Measurement row : Catalogue.measureAll()) {
                assertThat(row.interlacement()).isGreaterThan(0).isLessThanOrEqualTo(1);
            }
        }
    }

    @Nested
    class TheFamilies {

        @Test
        void putsEveryWeaveInExactlyOneFamily() {
            Map<String, String> byName = new HashMap<>();
            for (Weave weave : Catalogue.listWeaves()) {
                assertThat(byName).doesNotContainKey(weave.name());
                byName.put(weave.name(), weave.family());
            }
            assertThat(byName).hasSameSizeAs(Catalogue.listWeaves());
        }

        @Test
        void hasEveryFamilyHoldingAtLeastOneWeave() {
            for (String family : Catalogue.families()) {
                assertThat(Catalogue.weavesInFamily(family)).isNotEmpty();
            }
        }

        @Test
        void accountsForEveryWeaveAcrossTheFamilies() {
            int counted = Catalogue.families().stream()
                    .mapToInt(family -> Catalogue.weavesInFamily(family).size())
                    .sum();
            assertThat(counted).isEqualTo(Catalogue.listWeaves().size());
        }

        @Test
        void readsTheFiguredFamilyAsLongFloating() {
            for (Weave weave : Catalogue.weavesInFamily("figured")) {
                assertThat(Catalogue.measure(weave.name()).longestFloat()).isGreaterThan(2);
            }
        }

        @Test
        void readsTheBlockFamilyAsBalancedCloth() {
            for (Weave weave : Catalogue.weavesInFamily("block")) {
                assertThat(Catalogue.measure(weave.name()).warpFace())
                        .isGreaterThan(0.4)
                        .isLessThan(0.6);
            }
        }
    }

    @Nested
    class WeavesWithin {

        @Test
        void keepsOnlyTheWeavesInsideAFloatLimitFirmestFirst() {
            List<Measurement> found = Catalogue.weavesWithin(2);
            assertThat(found).allMatch(row -> row.longestFloat() <= 2);
            assertThat(found.get(0).name()).isEqualTo("plain");
        }

        @Test
        void letsEverythingThroughOnALimitNothingExceeds() {
            assertThat(Catalogue.weavesWithin(99)).hasSameSizeAs(Catalogue.listWeaves());
        }

        @Test
        void keepsNothingOnALimitOfNothing() {
            assertThat(Catalogue.weavesWithin(0)).isEmpty();
        }
    }

    @Nested
    class AddWeaveAndRemoveWeave {

        @Test
        void takesAWeaveAShedAddsAndMeasuresItLikeAnyOther() {
            Catalogue.addWeave(new Weave("shed special", "plain", Plain::plainWeave));
            assertThat(Catalogue.measure("shed special").firmness()).isEqualTo(Firmness.FIRM);
            assertThat(Catalogue.listWeaves()).anyMatch(weave -> weave.name().equals("shed special"));
        }

        @Test
        void takesItBackOutAgain() {
            Catalogue.addWeave(new Weave("shed special", "plain", Plain::plainWeave));
            assertThat(Catalogue.removeWeave("shed special")).isTrue();
            assertThatThrownBy(() -> Catalogue.weaveByName("shed special")).isInstanceOf(NotFoundException.class);
        }

        @Test
        void refusesAnEntryWithNoBuilder() {
            assertThatThrownBy(() -> Catalogue.addWeave(new Weave("shed special", "plain", null)))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> Catalogue.addWeave(new Weave("", "plain", Plain::plainWeave)))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class TheNewFamiliesReadAsTheirOwnKindOfCloth {

        @Test
        void readsTheCrepesAsScatteredRatherThanAsLines() {
            // A crepe that scored a diagonal would be a twill wearing a crepe's name, which is the one
            // thing that can go wrong when a crepe is built by overlaying weaves, so it is checked here
            // rather than trusted.
            for (Weave weave : Catalogue.weavesInFamily("crepe")) {
                assertThat(Crepe.diagonalScore(Catalogue.weaveByName(weave.name())))
                        .isLessThan(1);
            }
        }

        @Test
        void readsMockLenoAsAnOpenClothBesideAPlainOne() {
            Measurement leno = Catalogue.measure("mock leno 3");
            Measurement plain = Catalogue.measure("plain");
            assertThat(leno.longestFloat()).isGreaterThan(plain.longestFloat());
            assertThat(leno.firmness()).isNotEqualTo(plain.firmness());
        }

        @Test
        void buildsEveryBlockWeaveOnAnEvenNumberOfEndsAndPicks() {
            // Blocks come in pairs, so neither side of the repeat can be odd. Summer and winter is 12 by
            // 24 rather than square, which is why the check is on the parity and not on the two being
            // equal.
            for (Weave weave : Catalogue.weavesInFamily("block")) {
                Matrix cloth = Catalogue.weaveByName(weave.name());
                assertThat(cloth.rows() % 2).isZero();
                assertThat(cloth.cols() % 2).isZero();
            }
        }

        @Test
        void sortsTheShortFloatingWeavesFirmestFirst() {
            List<Measurement> within = Catalogue.weavesWithin(3);
            assertThat(within.get(0).name()).isEqualTo("plain");
            assertThat(within).allMatch(row -> row.longestFloat() <= 3);
            for (int i = 1; i < within.size(); i += 1) {
                assertThat(within.get(i - 1).interlacement())
                        .isGreaterThanOrEqualTo(within.get(i).interlacement());
            }
        }

        @Test
        void takesTheCrepesAndMockLenoInUnderThatLimitAndLeavesSatinOut() {
            List<String> names =
                    Catalogue.weavesWithin(3).stream().map(Measurement::name).toList();
            assertThat(names).contains("crepe 8", "mock leno 2");
            assertThat(names).noneMatch(name -> name.startsWith("satin"));
        }
    }
}
