package io.github.rkaushikdev.heddlewright.weave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.draft.Drawdown;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BlocksTest {

    @Nested
    class ProfileBlocks {

        @Test
        void listsTheDistinctBlocksInTheOrderTheyFirstAppear() {
            assertThat(Blocks.profileBlocks(List.of("B", "A", "B", "C"))).containsExactly("B", "A", "C");
        }

        @Test
        void refusesAnEmptyOrderAndABlockThatIsNotAName() {
            assertThatThrownBy(() -> Blocks.profileBlocks(List.of())).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Blocks.profileBlocks(List.of("A", "")))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class BlockSizes {

        @Test
        void givesEveryBlockTheSameCountFromANumber() {
            Map<String, Integer> sizes = Blocks.blockSizes(List.of("A", "B"), 4);
            assertThat(sizes.get("A")).isEqualTo(4);
            assertThat(sizes.get("B")).isEqualTo(4);
        }

        @Test
        void takesACountPerBlock() {
            Map<String, Integer> sizes = Blocks.blockSizes(List.of("A", "B"), Json.obj("A", 6, "B", 2));
            assertThat(sizes.get("A")).isEqualTo(6);
            assertThat(sizes.get("B")).isEqualTo(2);
        }

        @Test
        void refusesABlockTheCountObjectDoesNotMention() {
            assertThatThrownBy(() -> Blocks.blockSizes(List.of("A", "B"), Json.obj("A", 4)))
                    .hasMessageContaining("block B has no thread count");
        }

        @Test
        void refusesACountOfZeroThreads() {
            assertThatThrownBy(() -> Blocks.blockSizes(List.of("A"), 0)).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Blocks.blockSizes(List.of("A"), Json.obj("A", 0)))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class ExpandProfileOrder {

        @Test
        void repeatsEachBlockForItsThreadCount() {
            assertThat(Blocks.expandProfileOrder(List.of("A", "B"), 2)).containsExactly("A", "A", "B", "B");
        }

        @Test
        void followsUnevenBlockSizes() {
            assertThat(Blocks.expandProfileOrder(List.of("A", "B"), Json.obj("A", 3, "B", 1)))
                    .containsExactly("A", "A", "A", "B");
        }
    }

    @Nested
    class SubstituteProfile {

        private final Matrix figure = Matrix.parse("X.\n.X");
        private final Matrix ground = Matrix.parse(".X\nX.");

        @Test
        void usesTheFigureWhereABlockMeetsItselfAndTheGroundElsewhere() {
            Matrix cloth = Blocks.substituteProfile(List.of("A", "B"), List.of("A", "B"), figure, ground, 2);
            assertThat(cloth.dimensions()).isEqualTo(new Matrix.Dimensions(4, 4));
            assertThat(cloth.format()).isEqualTo("X..X\n.XX.\n.XX.\nX..X");
        }

        @Test
        void takesDifferentUnitCountsForWarpAndWeft() {
            Matrix cloth = Blocks.substituteProfile(List.of("A", "B"), List.of("A", "B"), figure, ground, 3, 1);
            assertThat(cloth.dimensions()).isEqualTo(new Matrix.Dimensions(2, 6));
        }

        @Test
        void substitutesARealTwillForTheFigure() {
            Matrix cloth = Blocks.substituteProfile(
                    List.of("A"), List.of("A"), Twill.twillWeave("3/1"), Twill.twillWeave("1/3"), 4);
            assertThat(Drawdown.warpFaceRatio(cloth)).isEqualTo(0.75);
        }

        @Test
        void refusesAFigureOrGroundThatDoesNotInterlace() {
            assertThatThrownBy(
                            () -> Blocks.substituteProfile(List.of("A"), List.of("A"), Matrix.parse("XX\nXX"), ground))
                    .isInstanceOf(DraftException.class);
        }
    }

    @Nested
    class BlockAtAndBlockSpans {

        @Test
        void findsTheBlockAThreadFallsIn() {
            assertThat(Blocks.blockAt(List.of("A", "B"), 3, 1)).isEqualTo("A");
            assertThat(Blocks.blockAt(List.of("A", "B"), 3, 3)).isEqualTo("A");
            assertThat(Blocks.blockAt(List.of("A", "B"), 3, 4)).isEqualTo("B");
        }

        @Test
        void refusesAThreadPastTheEndOfTheProfile() {
            assertThatThrownBy(() -> Blocks.blockAt(List.of("A", "B"), 3, 7)).hasMessageContaining("past the end");
        }

        @Test
        void reportsTheSpanEachBlockCovers() {
            assertThat(Blocks.blockSpans(List.of("A", "B", "A"), Json.obj("A", 2, "B", 3)))
                    .containsExactly(
                            new Blocks.Span("A", 1, 2, 2),
                            new Blocks.Span("B", 3, 5, 3),
                            new Blocks.Span("A", 6, 7, 2));
        }

        @Test
        void theSpansCoverEveryThreadTheExpansionProduces() {
            List<Blocks.Span> spans = Blocks.blockSpans(List.of("A", "B", "C"), Json.obj("A", 2, "B", 1, "C", 4));
            assertThat(spans.get(spans.size() - 1).to())
                    .isEqualTo(Blocks.expandProfileOrder(List.of("A", "B", "C"), Json.obj("A", 2, "B", 1, "C", 4))
                            .size());
        }
    }

    @Nested
    class HasAdjacentRepeats {

        @Test
        void spotsABlockSittingNextToItself() {
            assertThat(Blocks.hasAdjacentRepeats(List.of("A", "A", "B"))).isTrue();
            assertThat(Blocks.hasAdjacentRepeats(List.of("A", "B", "A"))).isFalse();
            assertThat(Blocks.hasAdjacentRepeats(List.of("A"))).isFalse();
        }
    }

    @Nested
    class ProfileMatrix {

        @Test
        void drawsTheBlockDiagramItself() {
            assertThat(Blocks.profileMatrix(List.of("A", "B", "A"), List.of("A", "B"))
                            .format())
                    .isEqualTo("X.X\n.X.");
        }

        @Test
        void isSquareAndDiagonalWhenTheTwoOrdersMatch() {
            assertThat(Blocks.profileMatrix(List.of("A", "B", "C"), List.of("A", "B", "C"))
                            .format())
                    .isEqualTo("X..\n.X.\n..X");
        }
    }
}
