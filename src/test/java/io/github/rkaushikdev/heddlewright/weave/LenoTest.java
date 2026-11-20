package io.github.rkaushikdev.heddlewright.weave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LenoTest {

    private static List<Leno.DoupPair> plan(int... ends) {
        List<Object> pairs = new java.util.ArrayList<>();
        for (int i = 0; i < ends.length; i += 2) {
            pairs.add(Json.obj("doup", ends[i], "standard", ends[i + 1]));
        }
        return Leno.doupPlan(pairs);
    }

    @Nested
    class MockLeno {

        @Test
        void putsPlainWeaveWhereAGroupMeetsItselfAndFloatsElsewhere() {
            assertThat(Leno.mockLeno(2).format()).isEqualTo(".XXX\nX.XX\nXX.X\nXXX.");
        }

        @Test
        void growsWithTheGroupSize() {
            assertThat(Leno.mockLeno(3).dimensions()).isEqualTo(new Matrix.Dimensions(6, 6));
        }

        @Test
        void interlacesInsideAGroupAndFloatsAcross() {
            Matrix repeat = Leno.mockLeno(3);
            assertThat(repeat.row(0).subList(0, 3)).containsExactly(0, 1, 0);
            assertThat(repeat.row(0).subList(3, 6)).containsExactly(1, 1, 1);
        }

        @Test
        void refusesAGroupOfOneWhichIsJustPlainWeave() {
            assertThatThrownBy(() -> Leno.mockLeno(1)).isInstanceOf(DraftException.class);
            assertThatThrownBy(() -> Leno.mockLeno(0)).isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void isWeavableOnShaftsWhichIsTheWholePointOfIt() {
            assertThat(Leno.weavableOnShafts(Leno.mockLeno(2))).isTrue();
        }
    }

    @Nested
    class HolePositions {

        @Test
        void putsAHoleAtEachCornerWhereTheGroupsMeet() {
            assertThat(Leno.holePositions(2)).containsExactly(new Leno.Hole(2, 2), new Leno.Hole(4, 4));
        }

        @Test
        void movesWithTheGroupSize() {
            assertThat(Leno.holePositions(3).get(1)).isEqualTo(new Leno.Hole(6, 6));
        }
    }

    @Nested
    class DoupPlan {

        @Test
        void recordsWhichEndCrossesAndWhichWay() {
            assertThat(plan(1, 2)).containsExactly(new Leno.DoupPair(1, 2, "left"));
            assertThat(plan(4, 3).get(0).side()).isEqualTo("right");
        }

        @Test
        void refusesAnEndCrossingOverItself() {
            assertThatThrownBy(() -> plan(1, 1)).hasMessageContaining("cannot cross over itself");
        }

        @Test
        void refusesAnEndNumberOfNothing() {
            assertThatThrownBy(() -> plan(0, 2)).isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void listsTheDoupEndsInOrder() {
            assertThat(Leno.doupEnds(plan(5, 6, 1, 2))).containsExactly(1, 5);
        }
    }

    @Nested
    class DoupConflicts {

        @Test
        void isQuietOnAPlanWhereEveryPairIsItsOwn() {
            List<Leno.DoupPair> plan = plan(1, 2, 3, 4);
            assertThat(Leno.doupConflicts(plan)).isEmpty();
            assertThat(Leno.assertDoupPlanSound(plan)).isSameAs(plan);
        }

        @Test
        void catchesTwoDoupsReachingForTheSameStandardEnd() {
            assertThat(Leno.doupConflicts(plan(1, 3, 2, 3)).stream().map(Leno.Conflict::code))
                    .contains("shared_standard");
        }

        @Test
        void catchesAnEndThatIsBothADoupAndAStandard() {
            assertThat(Leno.doupConflicts(plan(1, 2, 2, 3)).stream().map(Leno.Conflict::code))
                    .contains("doup_as_standard");
        }

        @Test
        void refusesAPlanThatWillNotOpen() {
            List<Leno.DoupPair> plan = plan(1, 3, 2, 3);
            assertThatThrownBy(() -> Leno.assertDoupPlanSound(plan)).isInstanceOf(DraftException.class);
        }
    }

    @Nested
    class EndsForLenoStripe {

        @Test
        void isTwoEndsAPairOverTheRepeats() {
            assertThat(Leno.endsForLenoStripe(6)).isEqualTo(12);
            assertThat(Leno.endsForLenoStripe(6, 4)).isEqualTo(48);
        }

        @Test
        void refusesAStripeWithNoPairs() {
            assertThatThrownBy(() -> Leno.endsForLenoStripe(0)).isInstanceOf(InvalidArgumentException.class);
        }
    }
}
