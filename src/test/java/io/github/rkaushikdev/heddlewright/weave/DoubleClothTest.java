package io.github.rkaushikdev.heddlewright.weave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.weave.DoubleCloth.Built;
import io.github.rkaushikdev.heddlewright.weave.DoubleCloth.Layer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DoubleClothTest {

    private static final Built BUILT = DoubleCloth.doubleCloth(Plain.plainWeave(), Plain.plainWeave());

    private static Built withCloth(String rows) {
        return new Built(Matrix.parse(rows, 'o', '.'), BUILT.endLayers(), BUILT.pickLayers());
    }

    private static List<Integer> indicesOf(List<Layer> layers, Layer wanted) {
        List<Integer> out = new ArrayList<>();
        for (int index = 0; index < layers.size(); index += 1) {
            if (layers.get(index) == wanted) {
                out.add(index);
            }
        }
        return out;
    }

    @Nested
    class EndLayersAndPickLayers {

        @Test
        void runTheOrderOutAcrossTheThreads() {
            assertThat(DoubleCloth.endLayers(List.of("face", "back"), 4))
                    .containsExactly(Layer.FACE, Layer.BACK, Layer.FACE, Layer.BACK);
            assertThat(DoubleCloth.pickLayers(List.of("face", "face", "back"), 6))
                    .containsExactly(Layer.FACE, Layer.FACE, Layer.BACK, Layer.FACE, Layer.FACE, Layer.BACK);
        }

        @Test
        void namesItsTwoLayersAndRefusesAnythingElse() {
            assertThat(Layer.LAYERS.stream().map(Layer::toString)).containsExactly("face", "back");
            assertThatThrownBy(() -> DoubleCloth.endLayers(List.of("face", "middle"), 4))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class DoubleClothBuilt {

        @Test
        void buildsARepeatHoldingBothCloths() {
            assertThat(BUILT.cloth().dimensions()).isEqualTo(new Matrix.Dimensions(4, 4));
            assertThat(BUILT.cloth().format('o', '.')).isEqualTo("o...\nooo.\n..o.\no.oo");
        }

        @Test
        void keepsTheBackEndsDownOnAFacePick() {
            int facePick = BUILT.pickLayers().indexOf(Layer.FACE);
            for (int end : indicesOf(BUILT.endLayers(), Layer.BACK)) {
                assertThat(BUILT.cloth().get(facePick, end)).isEqualTo(0);
            }
        }

        @Test
        void keepsTheFaceEndsUpOnABackPick() {
            int backPick = BUILT.pickLayers().indexOf(Layer.BACK);
            for (int end : indicesOf(BUILT.endLayers(), Layer.FACE)) {
                assertThat(BUILT.cloth().get(backPick, end)).isEqualTo(1);
            }
        }

        @Test
        void takesADifferentWeaveInEachLayer() {
            Built mixed = DoubleCloth.doubleCloth(Twill.twillWeave("2/2"), Plain.plainWeave());
            assertThat(DoubleCloth.extractLayer(mixed, Layer.FACE)).isEqualTo(Twill.twillWeave("2/2"));
        }

        @Test
        void takesAnUnevenOrderTwoFaceEndsToOneBack() {
            Built built =
                    DoubleCloth.doubleCloth(Plain.plainWeave(), Plain.plainWeave(), List.of("face", "face", "back"));
            assertThat(DoubleCloth.layerCounts(built).faceEnds()).isEqualTo(4);
            assertThat(DoubleCloth.layerCounts(built).backEnds()).isEqualTo(2);
        }
    }

    @Nested
    class StitchingPoints {

        @Test
        void findsNothingInAClothBuiltByTheRules() {
            assertThat(DoubleCloth.stitchingPoints(BUILT)).isEmpty();
            assertThat(DoubleCloth.assertLayersSeparate(BUILT)).isSameAs(BUILT);
        }

        @Test
        void findsABackEndRaisedOnAFacePick() {
            assertThat(DoubleCloth.stitchingPoints(withCloth("oo..\nooo.\n..o.\no.oo")))
                    .containsExactly(new DoubleCloth.StitchingPoint(1, 2, "back end raised on a face pick"));
        }

        @Test
        void findsAFaceEndLoweredOnABackPick() {
            assertThat(DoubleCloth.stitchingPoints(withCloth("o...\n.oo.\n..o.\no.oo"))
                            .get(0)
                            .kind())
                    .isEqualTo("face end lowered on a back pick");
        }

        @Test
        void refusesAClothThatIsStitchedNamingTheFirstPoint() {
            DraftException err = catchThrowableOfType(
                    DraftException.class, () -> DoubleCloth.assertLayersSeparate(withCloth("oo..\nooo.\n..o.\no.oo")));
            assertThat(err).isNotNull();
            assertThat(err.detail().get("first").get("pick")).isEqualTo(Json.of(1));
        }
    }

    @Nested
    class ExtractLayer {

        @Test
        void pullsEachClothBackOutOnItsOwnNumbering() {
            assertThat(DoubleCloth.extractLayer(BUILT, Layer.FACE).format()).isEqualTo("X.\n.X");
            assertThat(DoubleCloth.extractLayer(BUILT, Layer.BACK).format()).isEqualTo("X.\n.X");
        }

        @Test
        void refusesALayerThatIsNotThere() {
            assertThatThrownBy(() -> DoubleCloth.extractLayer(BUILT, Layer.parse("middle", "layer")))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class LayerCounts {

        @Test
        void countsWhatEachLayerTakesOfTheRepeat() {
            assertThat(DoubleCloth.layerCounts(BUILT)).isEqualTo(new DoubleCloth.LayerCounts(2, 2, 2, 2));
        }
    }
}
