package io.github.rkaushikdev.heddlewright.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.HeddleException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DrawdownTest {

    private static final JsonObject PLAIN_DRAFT = Json.obj(
            "threading", Threading.straightDraw(2, 4),
            "tieup", List.of(List.of(1), List.of(2)),
            "treadling", Treadling.straightTreadling(2, 4),
            "shafts", 2,
            "treadles", 2);

    private static JsonObject twill31() {
        return Json.obj(
                "threading", Threading.straightDraw(4),
                "tieup", Tieup.twillTieup(4, 3),
                "treadling", Treadling.straightTreadling(4, 4),
                "shafts", 4,
                "treadles", 4);
    }

    @Nested
    class DrawdownFromLiftplan {

        @Test
        void raisesAnEndWhenItsShaftIsLifted() {
            Matrix plan = Liftplan.liftplanFromShaftLists(List.of(List.of(1), List.of(2)), 2);
            Matrix cloth = Drawdown.drawdownFromLiftplan(List.of(1, 2, 1, 2), plan, 2);
            assertThat(cloth.format()).isEqualTo("X.X.\n.X.X");
        }

        @Test
        void takesTheShaftCountFromTheLiftplanWhenItIsNotGiven() {
            Matrix plan = Liftplan.liftplanFromShaftLists(List.of(List.of(1)), 3);
            assertThat(Drawdown.drawdownFromLiftplan(List.of(1, 2, 3), plan).format())
                    .isEqualTo("X..");
        }

        @Test
        void sinksInsteadOfLiftingOnACounterbalanceLoom() {
            Matrix plan = Liftplan.liftplanFromShaftLists(List.of(List.of(1), List.of(2)), 2);
            Matrix cloth = Drawdown.drawdownFromLiftplan(List.of(1, 2), plan, 2, Shed.SINKING);
            assertThat(cloth.format()).isEqualTo(".X\nX.");
        }

        @Test
        void rejectsAThreadingThatNamesAShaftThePlanHasNoColumnFor() {
            Matrix plan = Liftplan.liftplanFromShaftLists(List.of(List.of(1)), 2);
            assertThatThrownBy(() -> Drawdown.drawdownFromLiftplan(List.of(1, 3), plan, 2))
                    .isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void rejectsAShedItDoesNotKnow() {
            Matrix plan = Liftplan.liftplanFromShaftLists(List.of(List.of(1)), 2);
            assertThatThrownBy(() -> Drawdown.drawdownFromLiftplan(List.of(1), plan, Shed.parse("half")))
                    .hasMessageContaining("rising, sinking");
        }
    }

    @Nested
    class DrawdownFromDraft {

        @Test
        void weavesPlainWeaveFromATwoShaftDraft() {
            assertThat(Drawdown.drawdownFromDraft(PLAIN_DRAFT).format()).isEqualTo("X.X.\n.X.X\nX.X.\n.X.X");
        }

        @Test
        void weavesA22TwillThatStepsOneEndPerPick() {
            Matrix cloth = Drawdown.drawdownFromDraft(Json.obj(
                    "threading", Threading.straightDraw(4, 8),
                    "tieup", Tieup.twillTieup(4, 2),
                    "treadling", Treadling.straightTreadling(4, 4),
                    "shafts", 4,
                    "treadles", 4));
            assertThat(cloth.format()).isEqualTo("XX..XX..\n.XX..XX.\n..XX..XX\nX..XX..X");
        }

        @Test
        void turnsAPointThreadingIntoADiamondWhenTroddenAsWrit() {
            Matrix cloth = Drawdown.drawdownFromDraft(Json.obj(
                    "threading", Threading.pointDraw(4),
                    "tieup", Tieup.directTieup(4),
                    "treadling", Threading.pointDraw(4).stream().map(List::of).toList(),
                    "shafts", 4,
                    "treadles", 4));
            assertThat(cloth.format()).isEqualTo("X.....\n.X...X\n..X.X.\n...X..\n..X.X.\n.X...X");
        }
    }

    @Nested
    class ClothSize {

        @Test
        void namesTheRowsPicksAndTheColumnsEnds() {
            assertThat(Drawdown.clothSize(Drawdown.drawdownFromDraft(PLAIN_DRAFT)))
                    .isEqualTo(new Drawdown.ClothSize(4, 4));
        }
    }

    @Nested
    class EndColumnAndPickRow {

        private final Matrix cloth = Matrix.parse("XX..\n.XX.");

        @Test
        void readsAnEndDownAndAPickAcross1Based() {
            assertThat(Drawdown.endColumn(cloth, 1)).containsExactly(1, 0);
            assertThat(Drawdown.endColumn(cloth, 2)).containsExactly(1, 1);
            assertThat(Drawdown.pickRow(cloth, 2)).containsExactly(0, 1, 1, 0);
        }

        @Test
        void refusesAPositionOffTheCloth() {
            assertThatThrownBy(() -> Drawdown.endColumn(cloth, 5))
                    .isInstanceOf(IndexOutOfBoundsException.class)
                    .isNotInstanceOf(HeddleException.class);
            assertThatThrownBy(() -> Drawdown.pickRow(cloth, 3)).isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> Drawdown.pickRow(cloth, 0)).isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void handsBackACopyOfThePick() {
            assertThatThrownBy(() -> Drawdown.pickRow(cloth, 1).set(0, 0))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(cloth.get(0, 0)).isEqualTo(1);
        }
    }

    @Nested
    class WarpFaceRatio {

        @Test
        void isAHalfForPlainWeave() {
            assertThat(Drawdown.warpFaceRatio(Drawdown.drawdownFromDraft(PLAIN_DRAFT)))
                    .isEqualTo(0.5);
        }

        @Test
        void isThreeQuartersForA31WarpFacedTwill() {
            assertThat(Drawdown.warpFaceRatio(Drawdown.drawdownFromDraft(twill31())))
                    .isEqualTo(0.75);
        }

        @Test
        void isOneWhenEveryCellIsRaised() {
            assertThat(Drawdown.warpFaceRatio(Matrix.parse("XX\nXX"))).isEqualTo(1);
        }
    }

    @Nested
    class UnwovenThreads {

        @Test
        void findsAnEndThatNeverChangesState() {
            assertThat(Drawdown.unwovenEnds(Matrix.parse("X.X\nX.X")))
                    .containsExactly(
                            new Drawdown.UnwovenEnd(1, "always up"),
                            new Drawdown.UnwovenEnd(2, "always down"),
                            new Drawdown.UnwovenEnd(3, "always up"));
        }

        @Test
        void findsAPickThatNeverBinds() {
            assertThat(Drawdown.unwovenPicks(Matrix.parse("ooo\no.o\n...", 'o', '.')))
                    .containsExactly(new Drawdown.UnwovenPick(1, "all warp"), new Drawdown.UnwovenPick(3, "all weft"));
        }

        @Test
        void reportsNothingForASoundCloth() {
            Matrix cloth = Drawdown.drawdownFromDraft(PLAIN_DRAFT);
            assertThat(Drawdown.unwovenEnds(cloth)).isEmpty();
            assertThat(Drawdown.unwovenPicks(cloth)).isEmpty();
        }
    }

    @Nested
    class PairedEnds {

        @Test
        void groupsEndsThatLiftTogetherOnEveryPick() {
            assertThat(Drawdown.pairedEnds(Matrix.parse("X.X.\n.X.X")))
                    .isEqualTo(List.of(List.of(1, 3), List.of(2, 4)));
        }

        @Test
        void reportsNothingWhenEveryEndDiffers() {
            assertThat(Drawdown.pairedEnds(Matrix.parse("XX.\n.XX"))).isEmpty();
        }
    }

    @Nested
    class ReverseFace {

        @Test
        void isTheClothSeenFromTheBack() {
            assertThat(Drawdown.reverseFace(Matrix.parse("XX..\n.XX.")).format())
                    .isEqualTo("..XX\nX..X");
        }

        @Test
        void turnsA31WarpFaceIntoA13WeftFace() {
            Matrix cloth = Drawdown.drawdownFromDraft(twill31());
            assertThat(Drawdown.warpFaceRatio(Drawdown.reverseFace(cloth))).isEqualTo(0.25);
        }
    }

    @Nested
    class DrawdownFromRows {

        @Test
        void readsStringsOfXAndDot() {
            assertThat(Drawdown.drawdownFromRows(List.of("X.", ".X")).format()).isEqualTo("X.\n.X");
        }

        @Test
        void takesRowsThatAreAlreadyBitArrays() {
            assertThat(Drawdown.drawdownFromRows(List.of(List.of(1, 0))).format())
                    .isEqualTo("X.");
        }

        @Test
        void refusesRaggedRows() {
            assertThatThrownBy(() -> Drawdown.drawdownFromRows(List.of("X.", "X")))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }
}
