package io.github.rkaushikdev.heddlewright.draft;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.heddlewright.Fixtures;
import io.github.rkaushikdev.heddlewright.util.Json;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EquivalenceTest {

    private static final Draft TWILL = Fixtures.twill().name("twill").draft();

    private static final Draft RENUMBERED = Draft.create(Json.obj(
            "name",
            "twill renumbered",
            "shafts",
            4,
            "treadles",
            4,
            "threading",
            Threading.straightDraw(4, 8).stream()
                    .map(shaft -> ((shaft + 1) % 4) + 1)
                    .toList(),
            "tieup",
            Tieup.twillTieup(4, 2).stream()
                    .map(entry -> entry.stream()
                            .map(shaft -> ((shaft + 1) % 4) + 1)
                            .sorted()
                            .toList())
                    .toList(),
            "treadling",
            Treadling.straightTreadling(4, 8)));

    private static final Draft OTHER =
            Draft.create(TWILL.toRecord().with("name", "other", "tieup", Tieup.twillTieup(4, 3)));

    @Nested
    class ClothKey {

        @Test
        void isStableForTheSameDraft() {
            assertThat(Equivalence.clothKey(TWILL)).isEqualTo(Equivalence.clothKey(TWILL));
        }

        @Test
        void separatesTwoDraftsWeavingDifferentCloth() {
            assertThat(Equivalence.clothKey(TWILL)).isNotEqualTo(Equivalence.clothKey(OTHER));
        }

        @Test
        void readsAsEightHexDigits() {
            assertThat(Equivalence.clothKey(TWILL)).matches("^[0-9a-f]{8}$");
        }
    }

    @Nested
    class SameWriting {

        @Test
        void isTrueOnlyForTwoDraftsWrittenTheSameWay() {
            assertThat(Equivalence.sameWriting(
                            TWILL, Draft.create(TWILL.toRecord().with("name", "copy"))))
                    .isTrue();
            assertThat(Equivalence.sameWriting(TWILL, RENUMBERED)).isFalse();
        }
    }

    @Nested
    class ShaftPermutation {

        @Test
        void findsTheRenumberingBetweenTwoThreadings() {
            Map<Integer, Integer> mapping = Equivalence.shaftPermutation(List.of(1, 2, 3), List.of(3, 4, 1))
                    .orElseThrow();
            assertThat(mapping.get(1)).isEqualTo(3);
            assertThat(mapping.get(3)).isEqualTo(1);
        }

        @Test
        void isNullWhenOneShaftWouldHaveToBecomeTwo() {
            assertThat(Equivalence.shaftPermutation(List.of(1, 1), List.of(2, 3)))
                    .isEmpty();
        }

        @Test
        void isNullWhenTwoShaftsWouldHaveToBecomeOne() {
            assertThat(Equivalence.shaftPermutation(List.of(1, 2), List.of(3, 3)))
                    .isEmpty();
        }

        @Test
        void isNullForThreadingsOfDifferentLengths() {
            assertThat(Equivalence.shaftPermutation(List.of(1, 2), List.of(1, 2, 3)))
                    .isEmpty();
        }

        @Test
        void readsARenumberedThreadingAsTheSameOne() {
            assertThat(Equivalence.sameThreadingUpToNumbering(TWILL.threading(), RENUMBERED.threading()))
                    .isTrue();
            assertThat(Equivalence.sameThreadingUpToNumbering(List.of(1, 2, 1), List.of(1, 2, 2)))
                    .isFalse();
        }
    }

    @Nested
    class CompareDrafts {

        @Test
        void callsTwoIdenticalDraftsIdentical() {
            assertThat(Equivalence.compareDrafts(
                                    TWILL, Draft.create(TWILL.toRecord().with("name", "copy")))
                            .verdict())
                    .isEqualTo("identical");
        }

        @Test
        void seesThroughARenumbering() {
            Equivalence.Comparison verdict = Equivalence.compareDrafts(TWILL, RENUMBERED);
            assertThat(verdict.cloth()).isTrue();
            assertThat(verdict.verdict()).isEqualTo("same cloth, shafts renumbered");
        }

        @Test
        void saysPlainlyWhenTheClothDiffers() {
            assertThat(Equivalence.compareDrafts(TWILL, OTHER))
                    .isEqualTo(new Equivalence.Comparison("different cloth", false));
        }
    }

    @Nested
    class ShaftsNeededAndSimpler {

        @Test
        void countsTheShaftsAThreadingActuallyUses() {
            assertThat(Equivalence.shaftsNeeded(TWILL)).isEqualTo(4);
            Draft sparse = Draft.create(TWILL.toRecord().with("threading", List.of(1, 2, 1, 2, 1, 2, 1, 2)));
            assertThat(Equivalence.shaftsNeeded(sparse)).isEqualTo(2);
        }

        @Test
        void prefersTheDraftOnFewerShafts() {
            assertThat(Equivalence.simpler(TWILL, RENUMBERED)).contains("neither");
        }

        @Test
        void willNotRankTwoDraftsWeavingDifferentCloth() {
            assertThat(Equivalence.simpler(TWILL, OTHER)).isEmpty();
        }
    }

    @Nested
    class GroupByCloth {

        @Test
        void gathersTheDraftsThatWeaveTheSameThing() {
            List<List<String>> groups = Equivalence.groupByCloth(List.of(TWILL, RENUMBERED, OTHER));
            assertThat(groups).hasSize(2);
            assertThat(groups.get(0)).hasSize(2);
        }

        @Test
        void countsTheDistinctClothsInAPile() {
            assertThat(Equivalence.distinctCloths(List.of(TWILL, RENUMBERED, OTHER)))
                    .isEqualTo(2);
            assertThat(Equivalence.distinctCloths(List.of(TWILL))).isEqualTo(1);
        }
    }

    @Nested
    class SameCloth {

        @Test
        void isTheQuestionTheWholeModuleIsFor() {
            assertThat(Equivalence.sameCloth(TWILL, RENUMBERED)).isTrue();
            assertThat(Equivalence.sameCloth(TWILL, OTHER)).isFalse();
        }
    }
}
