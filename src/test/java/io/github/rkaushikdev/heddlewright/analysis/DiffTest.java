package io.github.rkaushikdev.heddlewright.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.weave.Plain;
import io.github.rkaushikdev.heddlewright.weave.Twill;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DiffTest {

    private static final Matrix A = Matrix.parse("XX..\n.XX.\n..XX\nX..X");
    private static final Matrix B = Matrix.parse("XX..\n.XX.\n..XX\nX.XX");

    @Nested
    class AssertSameShape {

        @Test
        void passesTwoClothsOfTheSameSize() {
            assertThat(Diff.assertSameShape(A, B)).isEqualTo(new Matrix.Dimensions(4, 4));
        }

        @Test
        void refusesTwoOfDifferentSizes() {
            assertThatThrownBy(() -> Diff.assertSameShape(A, Plain.plainWeave()))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class DifferenceMask {

        @Test
        void setsACellWhereverTheTwoDisagree() {
            assertThat(Diff.differenceMask(A, B).format()).isEqualTo("....\n....\n....\n..X.");
        }

        @Test
        void isEmptyForTwoIdenticalCloths() {
            assertThat(Diff.differenceMask(A, A).format()).isEqualTo("....\n....\n....\n....");
        }
    }

    @Nested
    class DifferenceCount {

        @Test
        void countsTheCellsAndTheShareOfTheCloth() {
            assertThat(Diff.differenceCount(A, B)).isEqualTo(new Diff.Difference(1, 16, 0.0625));
        }

        @Test
        void isNothingForTheSameClothTwice() {
            assertThat(Diff.differenceCount(A, A).cells()).isEqualTo(0);
            assertThat(Diff.sameCloth(A, A)).isTrue();
            assertThat(Diff.sameCloth(A, B)).isFalse();
        }

        @Test
        void readsAWholeClothChangedAsAShareOfOne() {
            assertThat(Diff.differenceCount(A, A.invert()).share()).isEqualTo(1);
        }
    }

    @Nested
    class EndsChangedAndPicksChanged {

        @Test
        void namesTheThreadsThatMovedAndHowMuch() {
            assertThat(Diff.endsChanged(A, B)).containsExactly(new Diff.EndChange(3, 1));
            assertThat(Diff.picksChanged(A, B)).containsExactly(new Diff.PickChange(4, 1));
        }

        @Test
        void isEmptyWhenNothingMoved() {
            assertThat(Diff.endsChanged(A, A)).isEmpty();
            assertThat(Diff.picksChanged(A, A)).isEmpty();
        }

        @Test
        void countsEveryCellOnAThreadThatChangedTwice() {
            Matrix changed = Matrix.parse(".X..\n.XX.\n..XX\n.X.X");
            assertThat(Diff.endsChanged(A, changed))
                    .containsExactly(new Diff.EndChange(1, 2), new Diff.EndChange(2, 1));
        }
    }

    @Nested
    class DescribeChange {

        @Test
        void callsAOneCellChangeLocal() {
            Diff.Change change = Diff.describeChange(A, B);
            assertThat(change.kind()).isEqualTo("local");
            assertThat(change.firstEnd()).hasValue(3);
            assertThat(change.firstPick()).hasValue(4);
        }

        @Test
        void callsADifferentWeaveWholesale() {
            Diff.Change change = Diff.describeChange(Twill.twillWeave("2/2"), Twill.twillWeave("3/1"));
            assertThat(change.kind()).isEqualTo("wholesale");
            assertThat(change.endsChanged()).isEqualTo(4);
        }

        @Test
        void callsNoChangeNoneWithNoFirstThreadToName() {
            Diff.Change change = Diff.describeChange(A, A);
            assertThat(change.kind()).isEqualTo("none");
            assertThat(change.firstEnd()).isEmpty();
            assertThat(change.firstPick()).isEmpty();
            assertThat(change.toJson().get("firstEnd")).isEqualTo(Json.NULL);
        }

        @Test
        void takesADifferentLineBetweenLocalAndWholesale() {
            assertThat(Diff.describeChange(A, B, 0.01).kind()).isEqualTo("wholesale");
        }

        @Test
        void refusesAShareOutsideNoughtToOne() {
            assertThatThrownBy(() -> Diff.describeChange(A, B, 2)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class RenderSideBySide {

        @Test
        void putsTheTwoClothsBesideEachOtherWithTheChangesMarked() {
            String[] lines = Diff.renderSideBySide(A, B).split("\n");
            assertThat(lines[0]).isEqualTo("XX..   XX..       ");
            assertThat(lines[3]).isEqualTo("X..X   X.XX     ! ");
        }

        @Test
        void takesADifferentGap() {
            assertThat(Diff.renderSideBySide(A, A, "|").split("\n")[0]).isEqualTo("XX..|XX..|    ");
        }
    }
}
