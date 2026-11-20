package io.github.rkaushikdev.heddlewright.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.heddlewright.Fixtures;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Problem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DraftTest {

    private static final JsonObject TWILL_INPUT = Fixtures.twillRecord();

    @Nested
    class CreateDraft {

        @Test
        void fillsInTheCountsACallerDoesNotHaveToRepeat() {
            Draft draft = Draft.create(TWILL_INPUT);
            assertThat(draft.ends()).isEqualTo(8);
            assertThat(draft.picks()).isEqualTo(8);
            assertThat(draft.shed()).isEqualTo(Shed.RISING);
            assertThat(draft.notes()).isEqualTo("");
        }

        @Test
        void normalisesTheTreadlingIntoPickLists() {
            Draft draft = Draft.create(TWILL_INPUT.with("treadling", List.of(1, 2, 3, 4)));
            assertThat(draft.treadling()).isEqualTo(List.of(List.of(1), List.of(2), List.of(3), List.of(4)));
        }

        @Test
        void defaultsTheNameRatherThanRefusingAnUnnamedDraft() {
            Draft draft = Draft.create(TWILL_INPUT.without("name"));
            assertThat(draft.name()).isEqualTo("untitled");
        }

        @Test
        void freezesTheResult() {
            Draft draft = Draft.create(TWILL_INPUT);
            assertThatThrownBy(() -> draft.threading().set(0, 2)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> draft.tieup().get(0).add(3)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void copiesTheThreadingSoTheCallerCannotEditItAfterwards() {
            List<Integer> threading = new ArrayList<>(Threading.straightDraw(4, 8));
            Draft draft = Draft.create(TWILL_INPUT.with("threading", threading));
            threading.set(0, 3);
            assertThat(draft.threading().get(0)).isEqualTo(1);
        }

        @Test
        void refusesATieupThatDoesNotMatchTheTreadleCount() {
            assertThatThrownBy(() -> Draft.create(TWILL_INPUT.with("treadles", 6)))
                    .isInstanceOf(DraftException.class);
        }

        @Test
        void refusesAMissingShaftCountAndAShedItDoesNotKnow() {
            assertThatThrownBy(() -> Draft.create(TWILL_INPUT.without("shafts")))
                    .isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Draft.create(TWILL_INPUT.with("shed", "half")))
                    .hasMessageContaining("rising, sinking");
        }
    }

    @Nested
    class DraftId {

        @Test
        void isStableForTheSameDraft() {
            assertThat(Draft.draftId(Draft.create(TWILL_INPUT))).isEqualTo(Draft.draftId(Draft.create(TWILL_INPUT)));
        }

        @Test
        void ignoresTheNameAndTheNotesWhichDoNotChangeTheCloth() {
            Draft a = Draft.create(TWILL_INPUT);
            Draft b = Draft.create(TWILL_INPUT.with("name", "something else", "notes", "for the fair"));
            assertThat(a.id()).isEqualTo(b.id());
        }

        @Test
        void changesWhenTheShedChanges() {
            Draft a = Draft.create(TWILL_INPUT);
            Draft b = Draft.create(TWILL_INPUT.with("shed", "sinking"));
            assertThat(a.id()).isNotEqualTo(b.id());
        }

        @Test
        void readsAsADraftPrefixAndEightHexDigits() {
            assertThat(Draft.create(TWILL_INPUT).id()).matches("^draft-[0-9a-f]{8}$");
        }
    }

    @Nested
    class DraftSummary {

        @Test
        void reportsTheShapeAndTheFace() {
            Draft.Summary summary = Draft.create(TWILL_INPUT).summary();
            assertThat(summary.name()).isEqualTo("four shaft twill");
            assertThat(summary.shafts()).isEqualTo(4);
            assertThat(summary.treadles()).isEqualTo(4);
            assertThat(summary.ends()).isEqualTo(8);
            assertThat(summary.picks()).isEqualTo(8);
            assertThat(summary.shaftsUsed()).isEqualTo(4);
            assertThat(summary.warpFaceRatio()).isEqualTo(0.5);
        }

        @Test
        void countsOnlyTheShaftsTheThreadingUses() {
            Draft draft = Draft.create(TWILL_INPUT.with("threading", List.of(1, 2, 1, 2, 1, 2, 1, 2)));
            assertThat(draft.summary().shaftsUsed()).isEqualTo(2);
        }
    }

    @Nested
    class InspectDraft {

        @Test
        void isQuietOnASoundDraft() {
            assertThat(Draft.create(TWILL_INPUT).inspect()).isEmpty();
        }

        @Test
        void reportsAShaftWithNothingThreadedOnIt() {
            Draft draft = Draft.create(TWILL_INPUT.with("threading", List.of(1, 2, 3, 1, 2, 3, 1, 2)));
            assertThat(draft.inspect().stream().map(Problem::code)).contains("idle_shaft");
        }

        @Test
        void reportsAnUntiedAndAnUnpressedTreadleSeparately() {
            Draft draft = Draft.create(TWILL_INPUT.with(
                    "tieup",
                    List.of(List.of(1, 2), List.of(2, 3), List.of(3, 4), List.of()),
                    "treadling",
                    Treadling.straightTreadling(3, 6)));
            List<String> codes = draft.inspect().stream().map(Problem::code).toList();
            assertThat(codes).contains("untied_treadle", "unused_treadle");
        }

        @Test
        void reportsAnEndThatNeverBinds() {
            Draft draft = Draft.create(Json.obj(
                    "name",
                    "floating end",
                    "shafts",
                    3,
                    "treadles",
                    3,
                    "threading",
                    List.of(1, 2, 3),
                    "tieup",
                    List.of(List.of(1), List.of(2), List.of(1, 2)),
                    "treadling",
                    List.of(1, 2, 3)));
            Problem problem = draft.inspect().stream()
                    .filter(entry -> entry.code().equals("unwoven_end"))
                    .findFirst()
                    .orElseThrow();
            assertThat(problem.detail()).isEqualTo(Json.obj("end", 3, "state", "always down"));
        }
    }

    @Nested
    class AssertDraftSound {

        @Test
        void returnsTheDraftWhenNothingIsWrong() {
            Draft draft = Draft.create(TWILL_INPUT);
            assertThat(draft.assertSound()).isSameAs(draft);
        }

        @Test
        void throwsWithTheWholeProblemListAttached() {
            Draft draft = Draft.create(TWILL_INPUT.with("threading", List.of(1, 2, 3, 1, 2, 3, 1, 2)));
            DraftException err = catchThrowableOfType(DraftException.class, draft::assertSound);
            assertThat(err).isNotNull();
            assertThat(((JsonArray) err.detail().get("problems")).size()).isGreaterThan(0);
        }
    }

    @Nested
    class ReviseDraft {

        @Test
        void replacesAFieldAndRenormalisesTheRest() {
            Draft draft = Draft.create(TWILL_INPUT);
            Draft revised = draft.revise(Json.obj("name", "renamed"));
            assertThat(revised.name()).isEqualTo("renamed");
            assertThat(revised.id()).isEqualTo(draft.id());
        }

        @Test
        void givesANewIdWhenTheClothChanges() {
            Draft draft = Draft.create(TWILL_INPUT);
            Draft revised = draft.revise(Json.obj("tieup", Tieup.twillTieup(4, 3)));
            assertThat(revised.id()).isNotEqualTo(draft.id());
        }

        @Test
        void checksTheChangeLikeAnyOtherDraftInput() {
            Draft draft = Draft.create(TWILL_INPUT);
            assertThatThrownBy(() -> draft.revise(Json.obj("shafts", 0))).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class SameDraft {

        @Test
        void isTrueForTwoDraftsWeavingTheSameCloth() {
            assertThat(Draft.sameDraft(Draft.create(TWILL_INPUT), Draft.create(TWILL_INPUT.with("name", "x"))))
                    .isTrue();
        }

        @Test
        void isFalseOnceATreadlingDiffers() {
            Draft other = Draft.create(TWILL_INPUT.with("treadling", Treadling.straightTreadling(4, 4)));
            assertThat(Draft.sameDraft(Draft.create(TWILL_INPUT), other)).isFalse();
        }
    }
}
