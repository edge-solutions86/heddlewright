package io.github.rkaushikdev.heddlewright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IdsTest {

    @Nested
    class Slug {

        @Test
        void lowercasesAndJoinsWithHyphens() {
            assertThat(Ids.slug("Broken Twill 2/2")).isEqualTo("broken-twill-2-2");
            assertThat(Ids.slug("  Warp  Beam  ")).isEqualTo("warp-beam");
        }

        @Test
        void stripsAccentsRatherThanDroppingTheLetter() {
            assertThat(Ids.slug("Gobelin Fauteuil")).isEqualTo("gobelin-fauteuil");
            assertThat(Ids.slug("E" + (char) 0x301 + "cru")).isEqualTo("ecru");
            assertThat(Ids.slug((char) 0xc9 + "cru")).isEqualTo("ecru");
        }

        @Test
        void neverStartsOrEndsWithAHyphen() {
            assertThat(Ids.slug("--satin--")).isEqualTo("satin");
            assertThat(Ids.slug("5/3 satin!")).isEqualTo("5-3-satin");
        }

        @Test
        void truncatesWithoutLeavingATrailingHyphen() {
            assertThat(Ids.slug("abcdefgh ijkl", 9)).isEqualTo("abcdefgh");
            assertThat(Ids.slug("abcdefghij", 4)).isEqualTo("abcd");
        }

        @Test
        void refusesTextWithNothingUsableInIt() {
            assertThatThrownBy(() -> Ids.slug("///")).hasMessageContaining("no slug-able");
            assertThatThrownBy(() -> Ids.slug("")).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class StableHash {

        @Test
        void isEightHexDigits() {
            assertThat(Ids.stableHash("plain weave")).matches("^[0-9a-f]{8}$");
        }

        @Test
        void isStableForTheSameInputAndDifferentForASmallChange() {
            assertThat(Ids.stableHash("twill")).isEqualTo(Ids.stableHash("twill"));
            assertThat(Ids.stableHash("twill")).isNotEqualTo(Ids.stableHash("twil"));
            assertThat(Ids.stableHash("")).matches("^[0-9a-f]{8}$");
        }

        @Test
        void holdsThePublishedFnv1aValuesForTheStandardVectors() {
            assertThat(Ids.stableHash("a")).isEqualTo("e40c292c");
            assertThat(Ids.stableHash("foobar")).isEqualTo("bf9cf968");
        }

        @Test
        void seesMultiByteCharactersAsTheirUtf8Bytes() {
            assertThat(Ids.stableHash(String.valueOf((char) 0xe9))).isNotEqualTo(Ids.stableHash("e"));
        }

        @Test
        void refusesANonStringRatherThanStringifyingIt() {
            // stableHash(42) cannot be written against a String parameter; null is the value that is not one.
            assertThatThrownBy(() -> Ids.stableHash(null)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class ContentId {

        @Test
        void prefixesAHashOfTheContent() {
            String id = Ids.contentId("draft", Json.obj("shafts", 4));
            assertThat(id).matches("^draft-[0-9a-f]{8}$");
            assertThat(Ids.contentId("draft", Json.obj("shafts", 4))).isEqualTo(id);
        }

        @Test
        void separatesDifferentContentUnderTheSamePrefix() {
            assertThat(Ids.contentId("draft", Json.obj("shafts", 4)))
                    .isNotEqualTo(Ids.contentId("draft", Json.obj("shafts", 8)));
        }

        @Test
        void slugsThePrefix() {
            assertThat(Ids.contentId("Card Deck", "x")).startsWith("card-deck-");
        }
    }

    @Nested
    class UniqueId {

        @Test
        void handsBackThePlainSlugWhenItIsFree() {
            assertThat(Ids.uniqueId("Plain Weave", Set.of())).isEqualTo("plain-weave");
            assertThat(Ids.uniqueId("Plain Weave", List.of())).isEqualTo("plain-weave");
        }

        @Test
        void suffixesFromTwoUpwardSkippingTheOnesAlreadyTaken() {
            assertThat(Ids.uniqueId("twill", Set.of("twill", "twill-2", "twill-3")))
                    .isEqualTo("twill-4");
        }

        @Test
        void takesAnArrayAsWellAsASet() {
            assertThat(Ids.uniqueId("satin", List.of("satin"))).isEqualTo("satin-2");
        }

        @Test
        void treatsAMissingCollectionAsEmpty() {
            assertThat(Ids.uniqueId("satin")).isEqualTo("satin");
            assertThat(Ids.uniqueId("satin", null)).isEqualTo("satin");
        }
    }

    @Nested
    class ReferenceCounterTests {

        @Test
        void padsToTheRequestedWidthAndCountsUp() {
            ReferenceCounter counter = Ids.referenceCounter("CD");
            assertThat(counter.next()).isEqualTo("CD0001");
            assertThat(counter.next()).isEqualTo("CD0002");
            assertThat(counter.count()).isEqualTo(2);
        }

        @Test
        void peekDoesNotAdvance() {
            ReferenceCounter counter = Ids.referenceCounter("B", 2);
            assertThat(counter.peek()).isEqualTo("B01");
            assertThat(counter.peek()).isEqualTo("B01");
            assertThat(counter.next()).isEqualTo("B01");
            assertThat(counter.peek()).isEqualTo("B02");
        }

        @Test
        void startsWhereItIsTold() {
            ReferenceCounter counter = Ids.referenceCounter("W", 3, 42);
            assertThat(counter.next()).isEqualTo("W042");
            assertThat(counter.count()).isEqualTo(1);
        }

        @Test
        void doesNotTruncateANumberWiderThanThePad() {
            assertThat(Ids.referenceCounter("X", 2, 1234).next()).isEqualTo("X1234");
        }

        @Test
        void checksItsArguments() {
            assertThatThrownBy(() -> Ids.referenceCounter("")).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Ids.referenceCounter("X", 0)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class ParseReference {

        @Test
        void splitsAReferenceIntoPrefixNumberAndWidth() {
            assertThat(Ids.parseReference("CD0007")).contains(new Ids.Reference("CD", 7, 4));
            assertThat(Ids.parseReference("warp-beam-12")).contains(new Ids.Reference("warp-beam-", 12, 2));
        }

        @Test
        void isNullWhenThereIsNoTrailingNumber() {
            assertThat(Ids.parseReference("CD")).isEmpty();
            assertThat(Ids.parseReference("0007")).isEmpty();
        }

        @Test
        void roundTripsWithReferenceCounter() {
            ReferenceCounter counter = Ids.referenceCounter("CD", 5, 9);
            assertThat(Ids.parseReference(counter.next())).contains(new Ids.Reference("CD", 9, 5));
        }
    }
}
