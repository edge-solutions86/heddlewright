package io.github.rkaushikdev.heddlewright.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.analysis.ColourOrder.ColourCount;
import io.github.rkaushikdev.heddlewright.analysis.ColourOrder.Stripe;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.weave.Plain;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ColourOrderTest {

    private static final Map<String, String> SYMBOLS = Map.of("dark", "#", "light", ".");

    @Nested
    class ColourGrid {

        @Test
        void takesTheWarpColourWhereTheWarpIsUpAndTheWeftColourElsewhere() {
            assertThat(ColourOrder.colourGrid(Plain.plainWeave(), List.of("red"), List.of("blue")))
                    .isEqualTo(List.of(List.of("red", "blue"), List.of("blue", "red")));
        }

        @Test
        void repeatsAShortColourOrderAcrossTheCloth() {
            assertThat(ColourOrder.colourGrid(Matrix.parse("XXXX"), List.of("a", "b"), List.of("c"))
                            .get(0))
                    .containsExactly("a", "b", "a", "b");
        }

        @Test
        void showsNothingButTheWarpColoursWhenTheWarpIsAlwaysUp() {
            List<List<String>> grid = ColourOrder.colourGrid(Matrix.parse("XX\nXX"), List.of("a", "b"), List.of("c"));
            assertThat(ColourOrder.colourCounts(grid).stream().map(ColourCount::colour))
                    .containsExactly("a", "b");
        }
    }

    @Nested
    class EffectRepeat {

        @Test
        void isTheLeastCommonMultipleOfWeaveAndColourOrder() {
            assertThat(ColourOrder.effectRepeat(4, 4, 8, 8)).isEqualTo(new ColourOrder.EffectRepeat(8, 8));
        }

        @Test
        void growsWhenTheTwoDoNotDivide() {
            assertThat(ColourOrder.effectRepeat(4, 4, 6, 6)).isEqualTo(new ColourOrder.EffectRepeat(12, 12));
        }

        @Test
        void refusesARepeatOfNothing() {
            assertThatThrownBy(() -> ColourOrder.effectRepeat(0, 4, 4, 4)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class ExpandStripes {

        @Test
        void runsAStripeListOutOneThreadAtATime() {
            assertThat(ColourOrder.expandStripes(List.of(new Stripe("a", 2), new Stripe("b", 1))))
                    .containsExactly("a", "a", "b");
        }

        @Test
        void refusesAStripeWithNoThreads() {
            assertThatThrownBy(() -> ColourOrder.expandStripes(List.of(new Stripe("a", 0))))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class Houndstooth {

        @Test
        void drawsTheBrokenCheckThatNeitherTheWeaveNorTheColourOrderShows() {
            assertThat(ColourOrder.renderColourGrid(ColourOrder.houndstooth(), SYMBOLS))
                    .isEqualTo(String.join(
                            "\n",
                            "####..##",
                            "#####..#",
                            "######..",
                            "####.##.",
                            "##......",
                            ".##.....",
                            "..##....",
                            "#..#...."));
        }

        @Test
        void isBalancedBetweenTheTwoColours() {
            assertThat(ColourOrder.colourCounts(ColourOrder.houndstooth()))
                    .containsExactly(new ColourCount("dark", 32), new ColourCount("light", 32));
            assertThat(ColourOrder.isBalancedEffect(ColourOrder.houndstooth())).isTrue();
        }

        @Test
        void takesTheColourNamesItIsGiven() {
            List<List<String>> grid = ColourOrder.houndstooth("navy", "oat");
            assertThat(ColourOrder.colourCounts(grid).stream()
                            .map(ColourCount::colour)
                            .sorted())
                    .containsExactly("navy", "oat");
        }

        @Test
        void isNotTheSameClothAsTheWeaveItCameFrom() {
            String plain = ColourOrder.renderColourGrid(
                    ColourOrder.colourGrid(Plain.plainWeave(), List.of("dark"), List.of("light")), SYMBOLS);
            assertThat(ColourOrder.renderColourGrid(ColourOrder.houndstooth(), SYMBOLS))
                    .isNotEqualTo(plain);
        }
    }

    @Nested
    class StepPattern {

        @Test
        void readsAsFineLinesRatherThanACheck() {
            assertThat(ColourOrder.renderColourGrid(ColourOrder.stepPattern(), SYMBOLS))
                    .isEqualTo(String.join(
                            "\n",
                            "#.###.##",
                            "..#...#.",
                            "###.###.",
                            "#...#...",
                            "#.###.##",
                            "..#...#.",
                            "###.###.",
                            "#...#..."));
        }

        @Test
        void repeatsEveryFourPicksUnlikeTheHoundstooth() {
            List<List<String>> grid = ColourOrder.stepPattern();
            assertThat(grid.get(0)).isEqualTo(grid.get(4));
            assertThat(ColourOrder.houndstooth().get(0))
                    .isNotEqualTo(ColourOrder.houndstooth().get(4));
        }
    }

    @Nested
    class RenderColourGrid {

        @Test
        void picksItsOwnSymbolsWhenNoneAreGiven() {
            String text =
                    ColourOrder.renderColourGrid(ColourOrder.houndstooth()).replace("\n", "");
            assertThat(new HashSet<>(text.chars().boxed().toList())).hasSize(2);
        }

        @Test
        void writesAQuestionMarkForAColourItHasNoSymbolFor() {
            assertThat(ColourOrder.renderColourGrid(List.of(List.of("a")), Map.of("b", "#")))
                    .isEqualTo("?");
        }
    }

    @Nested
    class IsBalancedEffect {

        @Test
        void isFalseWhenOneColourDominates() {
            assertThat(ColourOrder.isBalancedEffect(List.of(List.of("a", "a", "b"))))
                    .isFalse();
        }

        @Test
        void isFalseWhenThereAreNotExactlyTwoColours() {
            assertThat(ColourOrder.isBalancedEffect(List.of(List.of("a", "b", "c"))))
                    .isFalse();
            assertThat(ColourOrder.isBalancedEffect(List.of(List.of("a", "a")))).isFalse();
        }
    }
}
