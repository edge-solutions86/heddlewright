package io.github.rkaushikdev.heddlewright.jacquard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.jacquard.Design.Placement;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.OptionalInt;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DesignTest {

    private static final Matrix FIGURE = Matrix.parse("X.\n.X");

    private static Design.Fitted fit(int hooks, int scale) {
        return Design.fitDesign(FIGURE, hooks, OptionalInt.empty(), Placement.CENTRE, OptionalInt.of(scale));
    }

    @Nested
    class ScaleDesign {

        @Test
        void repeatsEachCellByWholeFactors() {
            assertThat(Design.scaleDesign(FIGURE, 2).format()).isEqualTo("XX..\nXX..\n..XX\n..XX");
        }

        @Test
        void scalesTheTwoDirectionsSeparately() {
            assertThat(Design.scaleDesign(FIGURE, 3, 1).dimensions()).isEqualTo(new Matrix.Dimensions(2, 6));
        }

        @Test
        void leavesTheDesignAloneAtAScaleOfOne() {
            assertThat(Design.scaleDesign(FIGURE).format()).isEqualTo(FIGURE.format());
        }

        @Test
        void refusesAFractionalScaleWhichWouldNotLandOnWholeCells() {
            assertThatThrownBy(() -> Design.scaleDesign(FIGURE, 1.5)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class LargestScale {

        @Test
        void isTheWholeNumberOfTimesTheDesignFitsTheHooks() {
            assertThat(Design.largestScale(FIGURE, 8)).isEqualTo(4);
            assertThat(Design.largestScale(FIGURE, 9)).isEqualTo(4);
        }

        @Test
        void refusesADesignWiderThanTheMachine() {
            assertThatThrownBy(() -> Design.largestScale(Matrix.parse("XXXX"), 2))
                    .isInstanceOf(CapacityException.class);
        }
    }

    @Nested
    class PlaceDesign {

        @Test
        void centresADesignByDefault() {
            Design.Placed placed = Design.placeDesign(FIGURE, 6);
            assertThat(placed.left()).isEqualTo(2);
            assertThat(placed.right()).isEqualTo(2);
            assertThat(placed.plan().format()).isEqualTo("..X...\n...X..");
        }

        @Test
        void placesLeftAndRightWhenToldTo() {
            assertThat(Design.PLACEMENTS).extracting(Object::toString).containsExactly("left", "centre", "right");
            assertThat(Design.placeDesign(FIGURE, 6, Placement.LEFT).left()).isEqualTo(0);
            assertThat(Design.placeDesign(FIGURE, 6, Placement.RIGHT).left()).isEqualTo(4);
        }

        @Test
        void putsTheOddHookOnTheRightWhenTheSpareWillNotDivide() {
            Design.Placed placed = Design.placeDesign(FIGURE, 5);
            assertThat(placed.left()).isEqualTo(1);
            assertThat(placed.right()).isEqualTo(2);
        }

        @Test
        void refusesADesignWiderThanTheMachine() {
            assertThatThrownBy(() -> Design.placeDesign(Matrix.parse("XXXX"), 2))
                    .isInstanceOf(CapacityException.class);
        }
    }

    @Nested
    class RepeatDown {

        @Test
        void repeatsTheDesignToThePickCount() {
            assertThat(Design.repeatDown(FIGURE, 5).dimensions()).isEqualTo(new Matrix.Dimensions(5, 2));
            assertThat(Design.repeatDown(FIGURE, 5).format()).isEqualTo("X.\n.X\nX.\n.X\nX.");
        }

        @Test
        void givesEachRowItsOwnArray() {
            // A Matrix cannot be written to, so the edit goes to the grid it hands out.
            int[][] repeated = Design.repeatDown(FIGURE, 4).toArray();
            repeated[0][0] = 0;
            assertThat(repeated[2][0]).isEqualTo(1);
        }
    }

    @Nested
    class FitDesign {

        @Test
        void scalesTheFigureAsLargeAsTheHooksAllowAndCentresIt() {
            Design.Fitted fitted = Design.fitDesign(FIGURE, 9);
            assertThat(fitted.scale()).isEqualTo(4);
            assertThat(fitted.designEnds()).isEqualTo(8);
            assertThat(fitted.spareHooks()).isEqualTo(1);
        }

        @Test
        void takesAScaleTheDesignerAskedForInstead() {
            Design.Fitted fitted = fit(9, 2);
            assertThat(fitted.designEnds()).isEqualTo(4);
            assertThat(fitted.spareHooks()).isEqualTo(5);
        }

        @Test
        void repeatsDownWhenAPickCountIsGiven() {
            Design.Fitted fitted =
                    Design.fitDesign(FIGURE, 8, OptionalInt.of(20), Placement.CENTRE, OptionalInt.empty());
            assertThat(fitted.plan().dimensions()).isEqualTo(new Matrix.Dimensions(20, 8));
        }

        @Test
        void leavesThePlanAtTheDesignHeightWhenNoPickCountIsGiven() {
            assertThat(Design.fitDesign(FIGURE, 8).plan().rows()).isEqualTo(8);
        }
    }

    @Nested
    class HookUtilisation {

        @Test
        void isTheShareOfTheMachineTheFigureTakes() {
            assertThat(Design.hookUtilisation(fit(10, 4), 10)).isEqualTo(0.8);
        }

        @Test
        void isOneWhenTheFigureFillsTheMachine() {
            assertThat(Design.hookUtilisation(Design.fitDesign(FIGURE, 8), 8)).isEqualTo(1);
        }
    }

    @Nested
    class RepeatsInLength {

        @Test
        void countsTheWholeRepeatsAndWhatIsLeft() {
            assertThat(Design.repeatsInLength(Design.fitDesign(FIGURE, 8), 20))
                    .isEqualTo(new Design.InLength(2, 4, false));
        }

        @Test
        void readsALengthThatDividesExactlyAsWhole() {
            assertThat(Design.repeatsInLength(Design.fitDesign(FIGURE, 8), 16).whole())
                    .isTrue();
        }
    }

    @Nested
    class AssertCentred {

        @Test
        void passesAFigureCentredOnTheMachine() {
            Design.Fitted fitted = fit(9, 2);
            assertThat(Design.assertCentred(fitted)).isSameAs(fitted);
        }

        @Test
        void refusesOnePushedToTheSide() {
            Design.Fitted fitted = Design.fitDesign(FIGURE, 10, OptionalInt.empty(), Placement.LEFT, OptionalInt.of(2));
            assertThatThrownBy(() -> Design.assertCentred(fitted)).isInstanceOf(DraftException.class);
        }
    }
}
