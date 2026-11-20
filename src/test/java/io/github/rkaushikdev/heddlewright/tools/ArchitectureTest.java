package io.github.rkaushikdev.heddlewright.tools;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.heddlewright.tools.Architecture.Entry;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The layering is the one architectural rule this package has, so it is checked rather than
 * described. Each helper is also run against a planted tree that it has to reject, because a rule
 * that has quietly stopped matching anything reports a clean sweep and reads exactly like a clean
 * repository.
 */
class ArchitectureTest {

    private static final List<Entry> TREE = Architecture.readTree();

    private static Entry planted(String file, String layer, String... imports) {
        return new Entry(file, Optional.ofNullable(layer), List.of(imports));
    }

    private static final Set<String> KNOWN = Set.of(
            "util/Numeric.java",
            "loom/BoxMotion.java",
            "service/App.java",
            "service/Router.java",
            "util/Dates.java",
            "util/A.java",
            "loom/BoxPlan.java");

    @Nested
    class TheLayers {

        @Test
        void areDeclaredLowestFirstAndCoverEveryAreaInSrc() {
            assertThat(Architecture.LAYERS)
                    .extracting(Architecture.Layer::name)
                    .containsExactly("util", "craft", "plant", "works", "store", "edge");
            List<String> unplaced = Architecture.sourceFiles().stream()
                    .filter(file -> !file.equals(Architecture.BARREL)
                            && Architecture.layerOf(file).isEmpty())
                    .toList();
            assertThat(unplaced).isEmpty();
        }

        @Test
        void nameNoAreaTwice() {
            List<String> areas = Architecture.LAYERS.stream()
                    .flatMap(layer -> layer.areas().stream())
                    .toList();
            assertThat(new HashSet<>(areas)).hasSameSizeAs(areas);
        }

        @Test
        void rankFromTheFloorUp() {
            assertThat(Architecture.rankOf("util")).isEqualTo(0);
            assertThat(Architecture.rankOf("edge")).isEqualTo(Architecture.LAYERS.size() - 1);
            assertThat(Architecture.rankOf("nowhere")).isEqualTo(-1);
        }

        @Test
        void leaveTheBarrelOutBecauseItUsesEverythingOnPurpose() {
            assertThat(Architecture.layerOf(Architecture.BARREL)).isEmpty();
        }
    }

    @Nested
    class ImportsOf {

        @Test
        void resolvesAnImportToAPathUnderSrc() {
            String text = "import io.github.rkaushikdev.heddlewright.util.Numeric;\n";
            assertThat(Architecture.importsOf("loom/BoxMotion.java", text, KNOWN))
                    .containsExactly("util/Numeric.java");
        }

        @Test
        void resolvesOneInsideTheSamePackage() {
            String text = "class App {\n    Router router = Router.createRouter();\n}\n";
            assertThat(Architecture.importsOf("service/App.java", text, KNOWN)).containsExactly("service/Router.java");
        }

        @Test
        void ignoresALibraryImportWhichCannotBeALayer() {
            assertThat(Architecture.importsOf("util/Dates.java", "import java.time.LocalDate;\n", KNOWN))
                    .isEmpty();
        }

        @Test
        void namesAnImportOnceHoweverManyTimesItAppears() {
            String text = "import io.github.rkaushikdev.heddlewright.loom.BoxPlan;\n"
                    + "import io.github.rkaushikdev.heddlewright.loom.BoxPlan.Something;\n"
                    + "class BoxMotion { BoxPlan a; BoxPlan b; }\n";
            assertThat(Architecture.importsOf("loom/BoxMotion.java", text, KNOWN))
                    .containsExactly("loom/BoxPlan.java");
        }
    }

    @Nested
    class TheTreeAsItStands {

        @Test
        void breaksTheLayeringNowhere() {
            assertThat(Architecture.violations(TREE)).isEmpty();
        }

        @Test
        void hasReadEveryFileAndGivenEachOneImportsOrNone() {
            assertThat(TREE.size()).isGreaterThan(100);
            assertThat(TREE).allMatch(entry -> entry.imports() != null);
        }

        @Test
        void keepsTheWeavingArithmeticClearOfTheServiceAndTheStore() {
            Set<String> reached = new HashSet<>();
            for (Entry entry : TREE) {
                if (entry.layer().equals(Optional.of("craft"))) {
                    entry.imports()
                            .forEach(target -> Architecture.layerOf(target).ifPresent(reached::add));
                }
            }
            assertThat(reached).doesNotContain("edge", "store", "works");
        }
    }

    @Nested
    class Violations {

        @Test
        void catchesAnImportThatGoesUpALayer() {
            assertThat(Architecture.violations(List.of(planted("util/Numeric.java", "util", "service/App.java"))))
                    .containsExactly("util/Numeric.java imports service/App.java, which is a layer up");
        }

        @Test
        void letsAnImportGoDownOrSideways() {
            assertThat(Architecture.violations(
                            List.of(planted("service/App.java", "edge", "util/Numeric.java", "service/Router.java"))))
                    .isEmpty();
        }

        @Test
        void saysNothingAboutAFileInNoLayer() {
            assertThat(Architecture.violations(List.of(planted(Architecture.BARREL, null, "service/App.java"))))
                    .isEmpty();
        }
    }

    @Nested
    class LayerGraph {

        @Test
        void countsTheImportsBetweenLayersAndLeavesTheOnesInsideALayerOut() {
            List<Entry> tree = List.of(
                    planted("works/Orders.java", "works", "util/Numeric.java", "works/Stock.java"),
                    planted("works/Stock.java", "works", "util/Numeric.java"));
            assertThat(Architecture.layerGraph(tree)).containsExactly(new Architecture.Edge("works -> util", 2));
        }

        @Test
        void putsTheBusiestEdgeFirstAndTheRealGraphRestsOnUtil() {
            assertThat(Architecture.layerGraph(TREE).get(0).edge()).isEqualTo("craft -> util");
        }
    }

    @Nested
    class LayerSizes {

        @Test
        void countsTheFilesAndTheImportsInEachLayer() {
            List<Architecture.Size> rows = Architecture.layerSizes(TREE);
            assertThat(rows)
                    .extracting(Architecture.Size::layer)
                    .isEqualTo(Architecture.LAYERS.stream()
                            .map(Architecture.Layer::name)
                            .toList());
            assertThat(rows).allMatch(row -> row.files() > 0);
            assertThat(rows.stream()
                            .filter(row -> row.layer().equals("util"))
                            .findFirst()
                            .orElseThrow()
                            .imports())
                    .isGreaterThan(0);
        }

        @Test
        void reportsALayerWithNothingInItRatherThanDroppingIt() {
            List<Architecture.Size> rows = Architecture.layerSizes(List.of());
            assertThat(rows).hasSameSizeAs(Architecture.LAYERS);
            assertThat(rows).allMatch(row -> row.files() == 0 && row.imports() == 0);
        }
    }

    @Nested
    class Unimported {

        @Test
        void findsTheFilesThatAreWaysInRatherThanParts() {
            assertThat(Architecture.unimported(TREE)).contains("service/Server.java", "cli/Heddle.java");
        }

        @Test
        void leavesTheBarrelOutWhichNothingUsesEither() {
            assertThat(Architecture.unimported(TREE)).doesNotContain(Architecture.BARREL);
        }

        @Test
        void isEverythingWhenNothingUsesAnything() {
            List<Entry> tree = List.of(planted("util/A.java", "util"), planted("util/B.java", "util", "util/A.java"));
            assertThat(Architecture.unimported(tree)).containsExactly("util/B.java");
        }
    }

    @Nested
    class TheDocument {

        @Test
        void saysHowItIsMadeAndNamesEveryLayer() {
            String text = Architecture.render(TREE);
            assertThat(text).contains(Architecture.COMMAND);
            for (Architecture.Layer layer : Architecture.LAYERS) {
                assertThat(text).contains("**" + layer.name() + "**");
            }
        }

        @Test
        void matchesTheCopyOnDisk() {
            assertThat(Architecture.check()).isEqualTo(new Architecture.Check(true, ""));
        }

        @Test
        void isTheCopyOnDiskByteForByte() {
            assertThat(Sources.read(Architecture.output())).isEqualTo(Architecture.render(TREE));
        }

        @Test
        void doesNotRunItselfOnLoad() {
            String text = Sources.read(Sources.mainSources().resolve("tools/Architecture.java"));
            assertThat(text).contains("public static void main(String[] args)");
            assertThat(text).doesNotContainPattern("(?m)^\\s*static\\s*\\{");
        }
    }
}
