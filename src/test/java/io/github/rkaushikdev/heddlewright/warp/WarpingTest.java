package io.github.rkaushikdev.heddlewright.warp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WarpingTest {

    private static int spreadOf(List<Integer> groups) {
        return Collections.max(groups) - Collections.min(groups);
    }

    @Nested
    class BalanceEnds {

        @Test
        void dividesEvenlyWhenItCan() {
            assertThat(Warping.balanceEnds(600, 3)).containsExactly(200, 200, 200);
        }

        @Test
        void givesTheRemainderToTheEarliestGroups() {
            assertThat(Warping.balanceEnds(602, 3)).containsExactly(201, 201, 200);
            assertThat(Warping.balanceEnds(601, 3)).containsExactly(201, 200, 200);
        }

        @Test
        void alwaysAddsBackToTheTotal() {
            for (int[] pair : new int[][] {{1000, 7}, {4321, 9}, {5, 5}}) {
                assertThat(Warping.balanceEnds(pair[0], pair[1]).stream()
                                .mapToInt(Integer::intValue)
                                .sum())
                        .isEqualTo(pair[0]);
            }
        }

        @Test
        void neverLetsTwoGroupsDifferByMoreThanOneEnd() {
            assertThat(spreadOf(Warping.balanceEnds(4321, 9))).isLessThanOrEqualTo(1);
        }

        @Test
        void refusesMoreGroupsThanThereAreEnds() {
            assertThatThrownBy(() -> Warping.balanceEnds(3, 4)).hasMessageContaining("fewer ends than groups");
        }
    }

    @Nested
    class BeamWarpingPlan {

        @Test
        void splitsAWideWarpOverAsManyBackBeamsAsTheCreelNeeds() {
            Warping.BeamPlan plan = Warping.beamWarpingPlan(2400, 600, 1000);
            assertThat(plan.beams()).isEqualTo(4);
            assertThat(plan.endsPerBeam()).containsExactly(600, 600, 600, 600);
        }

        @Test
        void spreadsAnAwkwardEndCountRatherThanLeavingAThinBeam() {
            Warping.BeamPlan plan = Warping.beamWarpingPlan(2402, 600, 1000);
            assertThat(plan.beams()).isEqualTo(5);
            assertThat(spreadOf(plan.endsPerBeam())).isLessThanOrEqualTo(1);
        }

        @Test
        void needsOneBeamWhenTheCreelHoldsTheWholeWarp() {
            assertThat(Warping.beamWarpingPlan(500, 600, 100).beams()).isEqualTo(1);
        }
    }

    @Nested
    class SectionalPlan {

        @Test
        void worksTheSectionsAndTheirWidthsOutFromTheSett() {
            Warping.SectionalPlan plan = Warping.sectionalPlan(2400, 400, 40, 500);
            assertThat(plan.sections()).isEqualTo(6);
            assertThat(plan.endsPerSection()).containsExactly(400, 400, 400, 400, 400, 400);
            assertThat(plan.widthPerSectionInches().get(0)).isEqualTo(10);
            assertThat(plan.totalWidthInches()).isEqualTo(60);
            assertThat(plan.evenSections()).isTrue();
        }

        @Test
        void reportsUnevenSectionsRatherThanHidingThem() {
            Warping.SectionalPlan plan = Warping.sectionalPlan(2402, 400, 40, 500);
            assertThat(plan.sections()).isEqualTo(7);
            assertThat(plan.evenSections()).isFalse();
        }

        @Test
        void addsTheSectionWidthsBackToTheTotalWidth() {
            Warping.SectionalPlan plan = Warping.sectionalPlan(2402, 400, 40, 500);
            double sum = plan.widthPerSectionInches().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();
            assertThat(sum).isCloseTo(plan.totalWidthInches(), within(0.005));
        }
    }

    @Nested
    class SectionSpreadAndAssertSectionsEven {

        private final Warping.SectionalPlan even = Warping.sectionalPlan(2400, 400, 40, 500);
        private final Warping.SectionalPlan uneven = Warping.sectionalPlan(2402, 400, 40, 500);

        @Test
        void measuresTheSpreadAcrossTheSections() {
            assertThat(Warping.sectionSpread(even)).isEqualTo(new Warping.Spread(400, 400, 0));
            assertThat(Warping.sectionSpread(uneven).spread()).isEqualTo(1);
        }

        @Test
        void acceptsASpreadOfOneEndByDefault() {
            assertThat(Warping.assertSectionsEven(even)).isSameAs(even);
            assertThat(Warping.assertSectionsEven(uneven)).isSameAs(uneven);
        }

        @Test
        void refusesOneWiderThanTheShedAllows() {
            assertThatThrownBy(() -> Warping.assertSectionsEven(uneven, 0)).hasMessageContaining("too uneven");
        }
    }

    @Nested
    class WarpingYarnMetres {

        @Test
        void addsTheCreelWasteToTheUsefulLength() {
            Warping.Yarn yarn = Warping.warpingYarnMetres(600, 1000);
            assertThat(yarn.useful()).isEqualTo(600000);
            assertThat(yarn.waste()).isEqualTo(1200);
            assertThat(yarn.total()).isEqualTo(601200);
        }

        @Test
        void chargesTheWasteOncePerSection() {
            Warping.Yarn one = Warping.warpingYarnMetres(600, 1000, 2, 1);
            Warping.Yarn six = Warping.warpingYarnMetres(600, 1000, 2, 6);
            assertThat(six.waste()).isEqualTo(one.waste() * 6);
        }

        @Test
        void readsTheWasteShareAsASmallFractionOnALongWarp() {
            assertThat(Warping.warpingYarnMetres(600, 1000).wasteShare()).isLessThan(0.01);
        }
    }

    @Nested
    class WarpingHours {

        @Test
        void addsTheSectionChangesToTheRunningTime() {
            Warping.Time time = Warping.warpingHours(500, 6, 300, 8);
            assertThat(time.runningMinutes()).isEqualTo(10);
            assertThat(time.changeMinutes()).isEqualTo(40);
            assertThat(time.hours()).isCloseTo(50.0 / 60, within(0.5e-3));
        }

        @Test
        void chargesNoChangeTimeOnASingleSection() {
            assertThat(Warping.warpingHours(500, 1, 300).changeMinutes()).isEqualTo(0);
        }

        @Test
        void refusesAWarpingSpeedOfNothing() {
            assertThatThrownBy(() -> Warping.warpingHours(500, 1, 0)).isInstanceOf(InvalidArgumentException.class);
        }
    }
}
