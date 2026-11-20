package io.github.rkaushikdev.heddlewright.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SurfaceTest {

    @Nested
    class RoutesSection {

        @Test
        void listsEveryRouteTheServiceAnswersOn() {
            String text = Surface.routesSection();
            assertThat(text).contains("GET /health");
            assertThat(text).contains("POST /plan");
        }

        @Test
        void writesEachRouteAsACodeSpanInAList() {
            List<String> lines = List.of(Surface.routesSection().split("\n", -1));
            for (String line : lines.subList(2, lines.size())) {
                assertThat(line).startsWith("- `");
            }
        }
    }

    @Nested
    class CommandsSection {

        @Test
        void listsEveryCommandWithItsSummary() {
            String text = Surface.commandsSection();
            assertThat(text).contains("draft");
            assertThat(text).contains("convert a yarn count between systems");
        }
    }

    @Nested
    class WeavesSection {

        @Test
        void reportsTheMeasurementsRatherThanAWrittenList() {
            String text = Surface.weavesSection();
            assertThat(text).contains("plain");
            assertThat(text).contains("satin 8");
            assertThat(text).contains("longest float");
        }

        @Test
        void carriesTheNumbersTheCatalogueActuallyMeasures() {
            assertThat(Surface.weavesSection()).containsPattern("satin 8\\s+satin\\s+8x8\\s+0.875\\s+7");
        }
    }

    @Nested
    class SurfaceDocument {

        @Test
        void saysItIsGeneratedAndHowToRegenerateIt() {
            assertThat(Surface.surface()).contains(Surface.COMMAND);
        }

        @Test
        void hasAllThreeSectionsInOrder() {
            String text = Surface.surface();
            assertThat(text.indexOf("## Routes")).isLessThan(text.indexOf("## Commands"));
            assertThat(text.indexOf("## Commands")).isLessThan(text.indexOf("## Weaves"));
        }

        @Test
        void endsWithANewline() {
            assertThat(Surface.surface()).endsWith("\n");
        }
    }

    @Nested
    class Check {

        @Test
        void saysTheDocumentOnDiskMatchesTheCode() {
            assertThat(Surface.check()).isEqualTo(new Surface.Check(true, ""));
        }

        @Test
        void readsTheFileItChecksAgainst() {
            assertThat(Sources.read(Surface.output())).isEqualTo(Surface.surface());
        }

        @Test
        void doesNotRunItselfOnLoad() {
            String text = Sources.read(Sources.mainSources().resolve("tools/Surface.java"));
            assertThat(text).contains("public static void main(String[] args)");
            assertThat(text).doesNotContainPattern("(?m)^\\s*static\\s*\\{");
        }
    }
}
