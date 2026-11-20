package io.github.rkaushikdev.heddlewright.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.Fixtures;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WifWriteTest {

    private static final Draft DRAFT = Fixtures.twillDraft();

    @Nested
    class WriteWif {

        @Test
        void writesAFileTheReaderTakesBackToTheSameCloth() {
            Draft back = Wif.draftFromWif(WifWrite.writeWif(DRAFT));
            assertThat(Drawdown.drawdownFromDraft(back).format())
                    .isEqualTo(Drawdown.drawdownFromDraft(DRAFT).format());
            assertThat(back.id()).isEqualTo(DRAFT.id());
        }

        @Test
        void keepsTheNameAndTheCounts() {
            Wif.Read read = Wif.readWif(WifWrite.writeWif(DRAFT));
            assertThat(read.title()).isEqualTo("four shaft twill");
            assertThat(read.shafts()).isEqualTo(4);
            assertThat(read.warpThreads()).isEqualTo(8);
            assertThat(read.weftThreads()).isEqualTo(8);
        }

        @Test
        void writesAnAuthorWhenOneIsGiven() {
            String text = WifWrite.writeWif(DRAFT, WifWrite.Options.DEFAULT.author("A Weaver"));
            assertThat(Wif.readWif(text).author()).isEqualTo("A Weaver");
        }

        @Test
        void recordsASinkingShedAsSuch() {
            Draft sinking = Draft.create(DRAFT.toRecord().with("shed", "sinking"));
            assertThat(Wif.readWif(WifWrite.writeWif(sinking)).risingShed()).isFalse();
        }

        @Test
        void declaresEverySectionItActuallyWrites() {
            String text = WifWrite.writeWif(DRAFT);
            for (String name : List.of("TEXT", "WEAVING", "WARP", "WEFT", "THREADING", "TIEUP", "TREADLING")) {
                assertThat(Wif.declaresAndCarries(text, name)).isTrue();
            }
        }

        @Test
        void endsWithANewlineAndHasNoBlankLineInsideASection() {
            String text = WifWrite.writeWif(DRAFT);
            assertThat(text).endsWith("\n");
            assertThat(text).doesNotContain("\n\n\n");
        }

        @Test
        void refusesSomethingThatIsNotADraft() {
            assertThatThrownBy(() -> WifWrite.writeWif(null)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class WriteDobbyWif {

        private final Matrix liftplan = Liftplan.liftplanFromTreadling(DRAFT.tieup(), DRAFT.treadling(), 4, 4);

        @Test
        void writesALiftplanInsteadOfATieupAndTreadling() {
            Map<String, Map<String, String>> sections = Ini.parseIni(WifWrite.writeDobbyWif(DRAFT, liftplan));
            assertThat(sections).containsKey("LIFTPLAN");
            assertThat(sections).doesNotContainKey("TIEUP");
            assertThat(sections).doesNotContainKey("TREADLING");
        }

        @Test
        void declaresTheLiftplanInTheContents() {
            assertThat(Wif.declaresAndCarries(WifWrite.writeDobbyWif(DRAFT, liftplan), "LIFTPLAN"))
                    .isTrue();
        }

        @Test
        void readsBackToTheSameCloth() {
            Draft back = Wif.draftFromWif(WifWrite.writeDobbyWif(DRAFT, liftplan));
            assertThat(Drawdown.drawdownFromDraft(back).format())
                    .isEqualTo(Drawdown.drawdownFromDraft(DRAFT).format());
        }

        @Test
        void writesOneLinePerPick() {
            Map<String, Map<String, String>> sections = Ini.parseIni(WifWrite.writeDobbyWif(DRAFT, liftplan));
            assertThat(sections.get("LIFTPLAN")).hasSize(8);
            assertThat(sections.get("LIFTPLAN").get("1")).isEqualTo("1,2");
        }
    }

    @Nested
    class ContentsFor {

        @Test
        void namesTheSectionsADraftWillFill() {
            assertThat(WifWrite.contentsFor(DRAFT))
                    .containsExactly("TEXT", "WEAVING", "WARP", "WEFT", "THREADING", "TIEUP", "TREADLING");
        }

        @Test
        void refusesSomethingThatIsNotADraft() {
            assertThatThrownBy(() -> WifWrite.contentsFor(null)).isInstanceOf(InvalidArgumentException.class);
        }
    }
}
