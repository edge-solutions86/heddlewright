package io.github.rkaushikdev.heddlewright.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.weave.Plain;
import io.github.rkaushikdev.heddlewright.weave.Satin;
import io.github.rkaushikdev.heddlewright.weave.Twill;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MotifTest {

    private static final Matrix WIDE_PLAIN = Matrix.parse("X.X.X.\n.X.X.X\nX.X.X.\n.X.X.X");

    @Nested
    class TilesCloth {

        @Test
        void isTrueForABlockThatRebuildsTheCloth() {
            assertThat(Motif.tilesCloth(WIDE_PLAIN, 2, 2)).isTrue();
        }

        @Test
        void isFalseForOneThatOnlyMatchesTheFirstRepeat() {
            Matrix nearly = Matrix.parse("X.X.X.\n.X.X.X\nX.X.XX\n.X.X.X");
            assertThat(Motif.tilesCloth(nearly, 2, 2)).isFalse();
        }

        @Test
        void isTrueForTheWholeClothAsItsOwnBlock() {
            assertThat(Motif.tilesCloth(WIDE_PLAIN, 4, 6)).isTrue();
        }
    }

    @Nested
    class PickRepeatAndEndRepeat {

        @Test
        void findsTheRepeatOfAWidePlainWeave() {
            assertThat(Motif.pickRepeat(WIDE_PLAIN)).isEqualTo(2);
            assertThat(Motif.endRepeat(WIDE_PLAIN)).isEqualTo(2);
        }

        @Test
        void findsTheRepeatOfATwillDrawnOutWide() {
            Matrix cloth = Twill.twillWeave("2/2", Twill.Options.DEFAULT.size(8, 12));
            assertThat(Motif.pickRepeat(cloth)).isEqualTo(4);
            assertThat(Motif.endRepeat(cloth)).isEqualTo(4);
        }

        @Test
        void isTheWholeSizeWhenNothingRepeats() {
            Matrix satin = Satin.satinWeave(5, 2);
            assertThat(Motif.pickRepeat(satin)).isEqualTo(5);
            assertThat(Motif.endRepeat(satin)).isEqualTo(5);
        }
    }

    @Nested
    class MotifOf {

        @Test
        void cutsTheClothDownToItsSmallestBlock() {
            Motif found = Motif.motif(WIDE_PLAIN);
            assertThat(found.picks()).isEqualTo(2);
            assertThat(found.ends()).isEqualTo(2);
            assertThat(found.block()).isEqualTo(Plain.plainWeave());
        }

        @Test
        void leavesAClothThatIsAlreadyOneRepeatAlone() {
            assertThat(Motif.reduceToMotif(Satin.satinWeave(5, 2))).isEqualTo(Satin.satinWeave(5, 2));
        }
    }

    @Nested
    class RepeatsAcross {

        @Test
        void countsTheRepeatsEachWay() {
            assertThat(Motif.repeatsAcross(WIDE_PLAIN)).isEqualTo(new Motif.Repeats(2, 3, true));
        }

        @Test
        void reportsAClothCutPartWayThroughARepeat() {
            Matrix ragged = Matrix.parse("X.X.X\n.X.X.\nX.X.X\n.X.X.");
            assertThat(Motif.repeatsAcross(ragged).whole()).isFalse();
            assertThatThrownBy(() -> Motif.assertWholeRepeats(ragged)).isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void passesAClothThatIsWholeRepeats() {
            assertThat(Motif.assertWholeRepeats(WIDE_PLAIN).whole()).isTrue();
        }
    }

    @Nested
    class Compression {

        @Test
        void saysHowMuchSmallerTheMotifIs() {
            Motif.Compression reading = Motif.compression(WIDE_PLAIN);
            assertThat(reading.cells()).isEqualTo(24);
            assertThat(reading.motifCells()).isEqualTo(4);
            assertThat(reading.ratio()).isCloseTo(1.0 / 6, within(0.5e-6));
        }

        @Test
        void isOneForAClothAlreadyAtItsRepeat() {
            assertThat(Motif.compression(Plain.plainWeave()).ratio()).isEqualTo(1);
        }
    }

    @Nested
    class SameMotif {

        @Test
        void seesThroughTheNumberOfRepeats() {
            assertThat(Motif.sameMotif(WIDE_PLAIN, Plain.plainWeave())).isTrue();
        }

        @Test
        void separatesTwoDifferentWeaves() {
            assertThat(Motif.sameMotif(Plain.plainWeave(), Twill.twillWeave("2/2")))
                    .isFalse();
        }
    }
}
