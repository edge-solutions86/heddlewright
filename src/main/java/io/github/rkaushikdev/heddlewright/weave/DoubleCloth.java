package io.github.rkaushikdev.heddlewright.weave;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Double cloth.
 *
 * <p>Two cloths woven at the same time, one above the other, sharing the loom but not the threads.
 * The warp is split between them end by end and the weft pick by pick, and two rules keep them
 * apart: on a face pick every back end must be down, so the face weft stays above the back warp; on
 * a back pick every face end must be up, so the back weft passes beneath the face cloth.
 *
 * <p>Break either one and the two cloths are stitched where they were not meant to be, which is the
 * fault this module exists to make visible.
 */
public final class DoubleCloth {

    /** Which of the two cloths a thread belongs to. */
    public enum Layer {
        /** The upper cloth. */
        FACE,
        /** The lower cloth. */
        BACK;

        /** The two layers, face first. */
        public static final List<Layer> LAYERS = List.of(values());

        /** Read a layer by its name, naming the field in any complaint. */
        public static Layer parse(Object text, String field) {
            return Check.member(text, field, values());
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** A double cloth: the combined repeat and the layer each end and pick belongs to. */
    public record Built(Matrix cloth, List<Layer> endLayers, List<Layer> pickLayers) {
        /** Copies the lists. */
        public Built {
            endLayers = List.copyOf(endLayers);
            pickLayers = List.copyOf(pickLayers);
        }
    }

    /** A cell where the two cloths are tied together. */
    public record StitchingPoint(int pick, int end, String kind) {}

    /** How many ends and picks each layer takes in the combined repeat. */
    public record LayerCounts(int faceEnds, int backEnds, int facePicks, int backPicks) {}

    private DoubleCloth() {}

    /** Which layer each end belongs to, from a repeating order of layer names. */
    public static List<Layer> endLayers(List<?> order, int ends) {
        List<Object> list = Check.array(order, "order", 1);
        Check.positiveInteger(ends, "ends");
        List<Layer> layers = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index += 1) {
            layers.add(Layer.parse(list.get(index), "order[" + index + "]"));
        }
        List<Layer> out = new ArrayList<>(ends);
        for (int e = 0; e < ends; e += 1) {
            out.add(layers.get(e % layers.size()));
        }
        return List.copyOf(out);
    }

    /** The same for the picks. */
    public static List<Layer> pickLayers(List<?> order, int picks) {
        return endLayers(order, picks);
    }

    /** A double cloth taking ends and picks face, back, face, back. */
    public static Built doubleCloth(Matrix face, Matrix back) {
        List<Layer> alternate = List.of(Layer.FACE, Layer.BACK);
        return doubleCloth(face, back, alternate, alternate);
    }

    /** A double cloth with the same layer order for the ends and the picks. */
    public static Built doubleCloth(Matrix face, Matrix back, List<?> endOrder) {
        return doubleCloth(face, back, endOrder, endOrder);
    }

    /**
     * Build a double cloth from a weave for each layer. The layer weaves are read on their own thread
     * numbering, so a face plain weave means plain weave in the face ends and face picks, not in the
     * combined cloth.
     */
    public static Built doubleCloth(Matrix face, Matrix back, List<?> endOrder, List<?> pickOrder) {
        Check.required(face, "face");
        Check.required(back, "back");
        int ends = endOrder.size() * Math.max(face.cols(), back.cols());
        int picks = pickOrder.size() * Math.max(face.rows(), back.rows());
        List<Layer> ofEnd = endLayers(endOrder, ends);
        List<Layer> ofPick = pickLayers(pickOrder, picks);
        List<Integer> endIndex = countWithin(ofEnd);
        List<Integer> pickIndex = countWithin(ofPick);
        int[][] cloth = new int[picks][ends];
        for (int p = 0; p < picks; p += 1) {
            for (int e = 0; e < ends; e += 1) {
                if (ofPick.get(p) == Layer.FACE) {
                    cloth[p][e] = ofEnd.get(e) == Layer.FACE
                            ? face.get(pickIndex.get(p) % face.rows(), endIndex.get(e) % face.cols())
                            : 0;
                } else {
                    cloth[p][e] = ofEnd.get(e) == Layer.BACK
                            ? back.get(pickIndex.get(p) % back.rows(), endIndex.get(e) % back.cols())
                            : 1;
                }
            }
        }
        return new Built(Matrix.of(cloth), ofEnd, ofPick);
    }

    private static List<Integer> countWithin(List<Layer> layers) {
        Map<Layer, Integer> counters = new EnumMap<>(Layer.class);
        List<Integer> out = new ArrayList<>(layers.size());
        for (Layer layer : layers) {
            int at = counters.getOrDefault(layer, 0);
            out.add(at);
            counters.put(layer, at + 1);
        }
        return out;
    }

    /**
     * Every cell where the two rules are broken, which is every place the cloths are stitched
     * together whether the designer meant it or not.
     */
    public static List<StitchingPoint> stitchingPoints(Built built) {
        List<StitchingPoint> found = new ArrayList<>();
        Matrix cloth = built.cloth();
        for (int p = 0; p < cloth.rows(); p += 1) {
            for (int e = 0; e < cloth.cols(); e += 1) {
                Layer pick = built.pickLayers().get(p);
                Layer end = built.endLayers().get(e);
                if (pick == Layer.FACE && end == Layer.BACK && cloth.isSet(p, e)) {
                    found.add(new StitchingPoint(p + 1, e + 1, "back end raised on a face pick"));
                }
                if (pick == Layer.BACK && end == Layer.FACE && !cloth.isSet(p, e)) {
                    found.add(new StitchingPoint(p + 1, e + 1, "face end lowered on a back pick"));
                }
            }
        }
        return List.copyOf(found);
    }

    /** Refuse a double cloth that is stitched where it should be two cloths. */
    public static Built assertLayersSeparate(Built built) {
        List<StitchingPoint> points = stitchingPoints(built);
        if (!points.isEmpty()) {
            throw new DraftException(
                    "the two cloths are stitched together", Json.obj("points", points.size(), "first", points.get(0)));
        }
        return built;
    }

    /** Pull one layer back out of a double cloth, on its own thread numbering. */
    public static Matrix extractLayer(Built built, Layer layer) {
        Check.required(layer, "layer");
        List<Integer> ends = indicesOf(built.endLayers(), layer);
        List<Integer> picks = indicesOf(built.pickLayers(), layer);
        int[][] out = new int[picks.size()][ends.size()];
        for (int p = 0; p < picks.size(); p += 1) {
            for (int e = 0; e < ends.size(); e += 1) {
                out[p][e] = built.cloth().get(picks.get(p), ends.get(e));
            }
        }
        return Matrix.of(out);
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

    /** How many ends and picks each layer takes in the combined repeat. */
    public static LayerCounts layerCounts(Built built) {
        return new LayerCounts(
                indicesOf(built.endLayers(), Layer.FACE).size(),
                indicesOf(built.endLayers(), Layer.BACK).size(),
                indicesOf(built.pickLayers(), Layer.FACE).size(),
                indicesOf(built.pickLayers(), Layer.BACK).size());
    }
}
