package io.github.rkaushikdev.heddlewright;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.draft.Draft;
import io.github.rkaushikdev.heddlewright.loom.Registry;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The shared fixtures get their own tests because a wrong fixture does not announce itself. It makes
 * some other test fail somewhere else, and the reader of that failure starts by suspecting the class
 * under test.
 */
class FixturesTest {

    private static int size(Json value) {
        return ((JsonArray) value).size();
    }

    private static double number(JsonObject record, String key) {
        return record.get(key).asDouble();
    }

    @Nested
    class TwillRecord {

        @Test
        void threadsAndTreadsTheCountsItWasAskedFor() {
            JsonObject record = Fixtures.twill().ends(16).picks(12).record();
            assertThat(size(record.get("threading"))).isEqualTo(16);
            assertThat(size(record.get("treadling"))).isEqualTo(12);
            assertThat(record.get("shafts")).isEqualTo(Json.of(4));
            assertThat(record.get("treadles")).isEqualTo(Json.of(4));
        }

        @Test
        void buildsOnMoreShaftsWhenAsked() {
            JsonObject record = Fixtures.twill().shafts(8).raise(3).ends(24).record();
            assertThat(record.get("shafts")).isEqualTo(Json.of(8));
            assertThat(size(record.get("tieup"))).isEqualTo(8);
            assertThat(size(record.get("tieup").at(0))).isEqualTo(3);
        }

        @Test
        void handsOutAFreshRecordEveryTime() {
            JsonObject first = Fixtures.twillRecord();
            JsonObject second = Fixtures.twillRecord();
            assertThat(first).isNotSameAs(second);
            assertThat(first.get("threading")).isNotSameAs(second.get("threading"));
            assertThatThrownBy(
                            () -> ((JsonArray) first.get("threading")).items().set(0, Json.of(99)))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(second.get("threading").at(0)).isEqualTo(Json.of(1));
        }

        @Test
        void takesANameForTheTestsThatReadOneBack() {
            assertThat(Fixtures.twill().name("twill").record().get("name")).isEqualTo(Json.of("twill"));
            assertThat(Fixtures.twillRecord().get("name")).isEqualTo(Json.of("four shaft twill"));
        }
    }

    @Nested
    class TwillDraft {

        @Test
        void isTheRecordBuiltSoTheTwoAgree() {
            Draft draft = Fixtures.twill().ends(16).picks(16).draft();
            assertThat(draft.id())
                    .isEqualTo(Draft.create(Fixtures.twill().ends(16).picks(16).record())
                            .id());
            assertThat(draft.ends()).isEqualTo(16);
            assertThat(draft.picks()).isEqualTo(16);
        }
    }

    @Nested
    class TheLooms {

        @Test
        void areBothRegistrableAsTheyStand() {
            assertThatCode(() -> Registry.createLoom(Fixtures.narrowLoom())).doesNotThrowAnyException();
            assertThatCode(() -> Registry.createLoom(Fixtures.wideLoom())).doesNotThrowAnyException();
        }

        @Test
        void differInTheWayTheTestsRelyOnWhichIsWidth() {
            assertThat(number(Fixtures.narrowLoom(), "reedWidthCm"))
                    .isLessThan(number(Fixtures.wideLoom(), "reedWidthCm"));
            assertThat(number(Fixtures.narrowLoom(), "shafts")).isLessThan(number(Fixtures.wideLoom(), "shafts"));
        }

        @Test
        void takesARunningSpeedForTheFilesThatNeedASlowerOne() {
            assertThat(number(Fixtures.wideLoom(180), "picksMinute")).isEqualTo(180);
            assertThat(number(Fixtures.wideLoom(), "picksMinute")).isEqualTo(200);
        }
    }

    @Nested
    class PoplinOrder {

        @Test
        void isNarrowEnoughForEitherLoom() {
            assertThat(number(Fixtures.poplinOrder(), "widthCm"))
                    .isLessThan(number(Fixtures.narrowLoom(), "reedWidthCm"));
        }

        @Test
        void takesOverridesWithoutChangingTheNextCaller() {
            assertThat(number(Fixtures.poplinOrder("metres", 10), "metres")).isEqualTo(10);
            assertThat(number(Fixtures.poplinOrder(), "metres")).isEqualTo(400);
        }
    }

    @Nested
    class ShirtingConstruction {

        @Test
        void isARealCloseSettRatherThanRoundNumbers() {
            JsonObject construction = Fixtures.shirtingConstruction();
            assertThat(number(construction, "endsPerCm")).isGreaterThan(number(construction, "picksPerCm"));
            assertThat(construction.get("system")).isEqualTo(Json.of("tex"));
        }

        @Test
        void takesOverridesToo() {
            assertThat(number(Fixtures.shirtingConstruction("picksPerCm", 8), "picksPerCm"))
                    .isEqualTo(8);
            assertThat(number(Fixtures.shirtingConstruction(), "picksPerCm")).isEqualTo(20);
        }
    }
}
