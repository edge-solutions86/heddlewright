package io.github.rkaushikdev.heddlewright.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The JavaScript text semantics the port reproduces, pinned against what node prints. */
class TextTest {

    @ParameterizedTest
    @CsvSource({
        "3, 3",
        "0.5, 0.5",
        "-2.25, -2.25",
        "0.1, 0.1",
        "0.30000000000000004, 0.30000000000000004",
        "123456789012, 123456789012",
        "1e21, 1e+21",
        "1.5e22, 1.5e+22",
        "0.000001, 0.000001",
        "0.0000001, 1e-7",
        "1.25e-9, 1.25e-9",
        "100, 100",
        "1e20, 100000000000000000000"
    })
    void writesANumberTheWayStringDoes(double value, String expected) {
        assertThat(Text.number(value)).isEqualTo(expected);
    }

    @Test
    void writesTheSpecialNumbers() {
        assertThat(Text.number(-0.0)).isEqualTo("0");
        assertThat(Text.number(Double.NaN)).isEqualTo("NaN");
        assertThat(Text.number(Double.NEGATIVE_INFINITY)).isEqualTo("-Infinity");
    }

    @Test
    void roundsToFixedOnTheBinaryValue() {
        assertThat(Text.fixed(1.005, 2)).isEqualTo("1.00");
        assertThat(Text.fixed(2.5, 0)).isEqualTo("3");
        assertThat(Text.fixed(-2.5, 0)).isEqualTo("-3");
        assertThat(Text.fixed(0.125, 2)).isEqualTo("0.13");
        assertThat(Text.fixed(-0.001, 2)).isEqualTo("-0.00");
        assertThat(Text.fixed(12, 1)).isEqualTo("12.0");
    }

    @Test
    void stringsAValueTheWayStringDoes() {
        assertThat(Text.string(Json.arr(1, "a", Json.NULL))).isEqualTo("1,a,");
        assertThat(Text.string(Json.obj())).isEqualTo("[object Object]");
        assertThat(Text.string(Json.MISSING)).isEqualTo("undefined");
        assertThat(Text.string(true)).isEqualTo("true");
    }

    @Test
    void readsANumberTheWayNumberDoes() {
        assertThat(Text.toNumber(" 12 ")).isEqualTo(12);
        assertThat(Text.toNumber("")).isEqualTo(0);
        assertThat(Text.toNumber("1e3")).isEqualTo(1000);
        assertThat(Text.toNumber("0x10")).isEqualTo(16);
        assertThat(Text.toNumber("12px")).isNaN();
        assertThat(Text.toNumber("-Infinity")).isEqualTo(Double.NEGATIVE_INFINITY);
        assertThat(Text.parseInt("12px")).isEqualTo(12);
        assertThat(Text.parseInt("px")).isNaN();
    }

    @Test
    void splitsTrimsAndPadsTheWayJavaScriptDoes() {
        assertThat(Text.split("a,,b", ",")).isEqualTo(List.of("a", "", "b"));
        assertThat(Text.split("ab", "")).isEqualTo(List.of("a", "b"));
        assertThat(Text.trim((char) 0xa0 + " x " + (char) 0xfeff)).isEqualTo("x");
        assertThat(Text.trimEnd("x  ")).isEqualTo("x");
        assertThat(Text.words("  one   two ")).isEqualTo(List.of("one", "two"));
        assertThat(Text.padStart("7", 3, '0')).isEqualTo("007");
        assertThat(Text.padEnd("ab", 4)).isEqualTo("ab  ");
        assertThat(Text.padStart("abcd", 2)).isEqualTo("abcd");
    }

    @Test
    void quotesStringsTheWayStringifyDoes() {
        assertThat(Json.stringify("a\"b\\\n\u0001")).isEqualTo("\"a\\\"b\\\\\\n\\u0001\"");
        assertThat(Json.stringify(Json.obj("a", 1, "b", Json.arr()), 2)).isEqualTo("{\n  \"a\": 1,\n  \"b\": []\n}");
        assertThat(Json.obj("b", 1, "2", 2, "1", 3).keys()).containsExactly("1", "2", "b");
    }
}
