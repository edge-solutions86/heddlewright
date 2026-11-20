package io.github.rkaushikdev.heddlewright.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.heddlewright.cli.Parse.Option;
import io.github.rkaushikdev.heddlewright.cli.Parse.Parsed;
import io.github.rkaushikdev.heddlewright.cli.Parse.Type;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ParseTest {

    private static Parsed parse(String... argv) {
        return Parse.parseArgs(List.of(argv));
    }

    @Nested
    class ParseArgs {

        @Test
        void takesTheCommandFirstAndTheRestAsPositional() {
            assertThat(parse("draft", "twill", "plain"))
                    .isEqualTo(new Parsed(Optional.of("draft"), List.of("twill", "plain"), Map.of()));
        }

        @Test
        void readsAnOptionWrittenWithASpaceOrAnEquals() {
            assertThat(parse("x", "--shafts", "4").options()).isEqualTo(Map.of("shafts", "4"));
            assertThat(parse("x", "--shafts=4").options()).isEqualTo(Map.of("shafts", "4"));
        }

        @Test
        void readsAnOptionWithNoValueAsAFlag() {
            assertThat(parse("x", "--verbose").options()).isEqualTo(Map.of("verbose", Boolean.TRUE));
            assertThat(parse("x", "--verbose", "--shafts", "4").options().get("verbose"))
                    .isEqualTo(Boolean.TRUE);
        }

        @Test
        void keepsAnEmptyValueFromTheEqualsForm() {
            assertThat(parse("x", "--name=").options()).isEqualTo(Map.of("name", ""));
        }

        @Test
        void stopsReadingOptionsAfterABareDoubleDash() {
            Parsed parsed = parse("x", "--", "--not-an-option");
            assertThat(parsed.positional()).containsExactly("--not-an-option");
            assertThat(parsed.options()).isEmpty();
        }

        @Test
        void hasANullCommandWhenThereIsNothingToDo() {
            assertThat(parse().command()).isEmpty();
        }

        @Test
        void refusesAnOptionWithNoName() {
            assertThatThrownBy(() -> parse("x", "--=1")).hasMessageContaining("an option needs a name");
        }

        @Test
        void stillTreatsABareDoubleDashAsTheSeparatorNotAsABadName() {
            assertThat(parse("x", "--", "--=1").positional()).containsExactly("--=1");
        }
    }

    @Nested
    class CheckOptions {

        private final Map<String, Option> spec = Draw.options(
                "shafts", Option.required(Type.INTEGER),
                "name", Option.of(Type.STRING, "untitled"),
                "ratio", Option.of(Type.NUMBER),
                "colours", Option.of(Type.LIST),
                "quiet", Option.of(Type.FLAG));

        @Test
        void coercesEachOptionToItsDeclaredType() {
            Parsed parsed = parse("x", "--shafts", "4", "--ratio", "0.5", "--colours", "a,b");
            assertThat(Parse.checkOptions(parsed, spec))
                    .isEqualTo(Json.obj(
                            "shafts",
                            4,
                            "name",
                            "untitled",
                            "ratio",
                            0.5,
                            "colours",
                            List.of("a", "b"),
                            "quiet",
                            Json.NULL));
        }

        @Test
        void readsAFlagAsTrueWhenItIsPresent() {
            assertThat(Parse.checkOptions(parse("x", "--shafts", "4", "--quiet"), spec)
                            .get("quiet"))
                    .isEqualTo(Json.of(true));
        }

        @Test
        void refusesAMissingRequiredOption() {
            assertThatThrownBy(() -> Parse.checkOptions(parse("x"), spec)).hasMessageContaining("--shafts is required");
        }

        @Test
        void refusesAnOptionTheCommandDoesNotDeclareAndListsTheOnesItDoes() {
            InvalidArgumentException err = catchThrowableOfType(
                    InvalidArgumentException.class,
                    () -> Parse.checkOptions(parse("x", "--shafts", "4", "--shaftz", "4"), spec));
            assertThat(err).isNotNull();
            assertThat(((JsonArray) err.detail().get("known")).items()).contains(Json.of("shafts"));
        }

        @Test
        void refusesAValueThatIsNotTheDeclaredType() {
            assertThatThrownBy(() -> Parse.checkOptions(parse("x", "--shafts", "four"), spec))
                    .hasMessageContaining("must be a whole number");
        }

        @Test
        void refusesAValueOptionWrittenAsABareFlag() {
            assertThatThrownBy(() -> Parse.checkOptions(parse("x", "--shafts"), spec))
                    .hasMessageContaining("needs a value");
        }

        @Test
        void dropsEmptyEntriesOutOfAList() {
            assertThat(Parse.checkOptions(parse("x", "--shafts", "4", "--colours", "a,,b, "), spec)
                            .get("colours"))
                    .isEqualTo(Json.arr("a", "b"));
        }
    }

    @Nested
    class UsageFor {

        @Test
        void describesTheOptionsACommandTakes() {
            Map<String, Option> spec = Draw.options(
                    "shafts", Option.required(Type.INTEGER).describe("how many shafts"), "quiet", Option.of(Type.FLAG));
            String text = Parse.usageFor("draft", spec, "build a draft");
            assertThat(text.split("\n", -1)[0]).isEqualTo("heddle draft - build a draft");
            assertThat(text).contains("--shafts <integer> (required)  how many shafts");
            assertThat(text).contains("--quiet");
        }

        @Test
        void leavesTheSummaryOffWhenThereIsNone() {
            assertThat(Parse.usageFor("draft", Map.of()).split("\n", -1)[0]).isEqualTo("heddle draft");
        }
    }
}
