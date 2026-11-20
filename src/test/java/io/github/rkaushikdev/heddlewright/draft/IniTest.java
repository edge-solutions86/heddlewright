package io.github.rkaushikdev.heddlewright.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IniTest {

    private static final String FILE = String.join(
            "\n",
            "; a small weaving file",
            "[WIF]",
            "Version=1.1",
            "Source Program=heddlewright",
            "",
            "[WEAVING]",
            "Shafts=4",
            "Treadles=6",
            "Rising Shed=yes",
            "",
            "[THREADING]",
            "1=1",
            "2=2",
            "4=4",
            "",
            "[TIEUP]",
            "1=1,2",
            "2=2,3");

    @Nested
    class ParseIni {

        @Test
        void readsSectionsAndKeys() {
            Map<String, Map<String, String>> sections = Ini.parseIni(FILE);
            assertThat(sections.keySet()).containsExactly("WIF", "WEAVING", "THREADING", "TIEUP");
            assertThat(sections.get("WEAVING").get("SHAFTS")).isEqualTo("4");
        }

        @Test
        void upperCasesSectionNamesAndKeysButKeepsValuesAsTyped() {
            assertThat(Ini.parseIni("[text]\nTitle=Broken Twill\n").get("TEXT").get("TITLE"))
                    .isEqualTo("Broken Twill");
        }

        @Test
        void dropsCommentsAndBlankLines() {
            assertThat(Ini.parseIni("; nothing\n\n[A]\nx=1 ; trailing\n")
                            .get("A")
                            .get("X"))
                    .isEqualTo("1");
        }

        @Test
        void readsAFileWrittenWithCarriageReturns() {
            assertThat(Ini.parseIni("[A]\r\nx=1\r\n").get("A").get("X")).isEqualTo("1");
        }

        @Test
        void keepsAnEqualsSignInsideAValue() {
            assertThat(Ini.parseIni("[A]\nx=a=b\n").get("A").get("X")).isEqualTo("a=b");
        }

        @Test
        void mergesTwoHeadersWithTheSameName() {
            Map<String, Map<String, String>> sections = Ini.parseIni("[A]\nx=1\n[A]\ny=2\n");
            assertThat(sections).hasSize(1);
            assertThat(sections.get("A")).hasSize(2);
        }

        @Test
        void refusesAKeyOutsideAnySectionAndALineThatIsNeither() {
            assertThatThrownBy(() -> Ini.parseIni("x=1\n")).hasMessageContaining("outside any section");
            assertThatThrownBy(() -> Ini.parseIni("[A]\nnonsense\n"))
                    .hasMessageContaining("neither a section nor a key");
        }

        @Test
        void refusesANonString() {
            assertThatThrownBy(() -> Ini.parseIni(null)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class SectionLookups {

        private final Map<String, Map<String, String>> sections = Ini.parseIni(FILE);

        @Test
        void findsSectionsWhateverCaseIsAskedFor() {
            assertThat(Ini.hasSection(sections, "weaving")).isTrue();
            assertThat(Ini.hasSection(sections, "COLOR PALETTE")).isFalse();
        }

        @Test
        void givesAnEmptySectionRatherThanUndefinedForOneThatIsMissing() {
            assertThat(Ini.section(sections, "nothing")).isEmpty();
        }

        @Test
        void readsAValueWithAFallback() {
            assertThat(Ini.value(sections, "wif", "Version")).contains("1.1");
            assertThat(Ini.value(sections, "wif", "Date", "unknown")).isEqualTo("unknown");
        }
    }

    @Nested
    class TypedLookups {

        private final Map<String, Map<String, String>> sections = Ini.parseIni(FILE);

        @Test
        void readsAnIntegerAndFallsBackWhenItIsMissingOrUnreadable() {
            assertThat(Ini.intValue(sections, "weaving", "Shafts")).hasValue(4);
            assertThat(Ini.intValue(sections, "weaving", "Colours", 0)).isEqualTo(0);
            assertThat(Ini.intValue(Ini.parseIni("[A]\nx=four\n"), "A", "x", -1))
                    .isEqualTo(-1);
        }

        @Test
        void readsAYesAsTrueAndAnythingElseAsFalse() {
            assertThat(Ini.boolValue(sections, "weaving", "Rising Shed")).isTrue();
            assertThat(Ini.boolValue(Ini.parseIni("[A]\nx=no\n"), "A", "x")).isFalse();
            assertThat(Ini.boolValue(sections, "weaving", "Sinking Shed", true)).isTrue();
        }

        @Test
        void readsACommaSeparatedList() {
            assertThat(Ini.intListValue(sections, "tieup", "1")).containsExactly(1, 2);
            assertThat(Ini.intListValue(sections, "tieup", "9")).isEmpty();
        }

        @Test
        void refusesAListEntryThatIsNotANumber() {
            assertThatThrownBy(() -> Ini.intListValue(Ini.parseIni("[A]\nx=1,two\n"), "A", "x"))
                    .hasMessageContaining("not a whole number");
        }
    }

    @Nested
    class NumberedList {

        private final Map<String, Map<String, String>> sections = Ini.parseIni(FILE);

        @Test
        void readsANumberedSectionIntoADenseArray() {
            assertThat(Ini.numberedList(sections, "threading", 4))
                    .containsExactly(Optional.of(1), Optional.of(2), Optional.empty(), Optional.of(4));
        }

        @Test
        void fillsTheGapsWithWhateverItIsToldTo() {
            assertThat(Ini.numberedList(sections, "threading", 4, 0)).containsExactly(1, 2, 0, 4);
        }

        @Test
        void readsListValuesWhenAsked() {
            assertThat(Ini.numberedLists(sections, "tieup", 2)).isEqualTo(List.of(List.of(1, 2), List.of(2, 3)));
        }

        @Test
        void ignoresEntriesPastTheCountAndNonNumericKeys() {
            Map<String, Map<String, String>> odd = Ini.parseIni("[A]\n1=5\n9=9\nname=x\n");
            assertThat(Ini.numberedList(odd, "A", 2, 0)).containsExactly(5, 0);
        }
    }

    @Nested
    class FormatIniAndIniFromObject {

        @Test
        void writesSectionsBackOutInOrder() {
            Map<String, Map<String, String>> sections =
                    Ini.iniFromObject(Json.obj("WIF", Json.obj("Version", "1.1"), "WEAVING", Json.obj("Shafts", 4)));
            assertThat(Ini.formatIni(sections)).isEqualTo("[WIF]\nVersion=1.1\n\n[WEAVING]\nShafts=4\n");
        }

        @Test
        void roundTripsThroughTheParser() {
            String written = Ini.formatIni(Ini.iniFromObject(Json.obj("A", Json.obj("x", 1, "y", 2))));
            Map<String, Map<String, String>> read = Ini.parseIni(written);
            assertThat(read.get("A").get("X")).isEqualTo("1");
            assertThat(read.get("A").get("Y")).isEqualTo("2");
        }

        @Test
        void refusesASectionThatIsNotAnObject() {
            assertThatThrownBy(() -> Ini.iniFromObject(Json.obj("A", "x")))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }
}
