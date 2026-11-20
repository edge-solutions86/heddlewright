package io.github.rkaushikdev.heddlewright.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The root collation order the JavaScript sorted by, pinned against what node prints. */
class CollationTest {

    private static List<String> sorted(String... values) {
        List<String> list = new ArrayList<>(List.of(values));
        list.sort(Collation.ORDER);
        return list;
    }

    @Test
    void putsPunctuationBeforeDigitsAndDigitsBeforeLetters() {
        assertThat(sorted("b", "2", "_x", "a")).containsExactly("_x", "2", "a", "b");
    }

    @Test
    void comparesLettersWithoutTheirCaseFirst() {
        assertThat(sorted("Banana", "apple", "cherry")).containsExactly("apple", "Banana", "cherry");
    }

    @Test
    void putsLowerCaseBeforeUpperWhenTheLettersAgree() {
        assertThat(sorted("A", "a")).containsExactly("a", "A");
        assertThat(Collation.compare("L1", "L1")).isZero();
    }

    @Test
    void readsDigitsOneAtATimeAsTheRootOrderDoes() {
        assertThat(sorted("L10", "L2", "L1")).containsExactly("L1", "L10", "L2");
    }

    @Test
    void ordersTheWeaveCatalogueTheWayItWasPrinted() {
        assertThat(sorted("twill 2/2", "twill 2/1/1/2", "sateen 5", "satin 5", "summer and winter 121"))
                .containsExactly("sateen 5", "satin 5", "summer and winter 121", "twill 2/1/1/2", "twill 2/2");
    }

    @Test
    void letsAnAccentCountOnlyAfterTheLetters() {
        String accented = (char) 0xe9 + "cru";
        assertThat(sorted(accented, "ecrv", "ecru")).containsExactly("ecru", accented, "ecrv");
    }
}
