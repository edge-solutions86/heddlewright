package io.github.rkaushikdev.heddlewright.util;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;

/**
 * The Unicode root collation order (the CLDR root locale), for the lists people read.
 *
 * <p>Every sorted list a weaver reads (the weave catalogue, the fault codes, the loom register) is
 * ordered this way rather than by code unit, so "Twill" and "twill" sit together and "_" does not
 * land after the letters. At the first
 * level white space and punctuation sort before digits and digits before letters, letters compare
 * without their case or accents; at the second level accents count; at the third, lower case comes
 * before upper. {@code java.text.Collator} is not used because it ignores punctuation that the
 * root collation does not.
 */
public final class Collation {

    /** {@link #compare} as a comparator. */
    public static final Comparator<String> ORDER = Collation::compare;

    private static final String PUNCTUATION = "_-,;:!?.'\"()[]{}@*/\\&#%`^+<=>|~$";

    private Collation() {}

    /** Negative, zero or positive as {@code a} sorts before, with or after {@code b}. */
    public static int compare(String a, String b) {
        int primary = byWeight(strip(a), strip(b));
        if (primary != 0) {
            return primary;
        }
        String accentsA = Normalizer.normalize(a.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        String accentsB = Normalizer.normalize(b.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        int secondary = accentsA.compareTo(accentsB);
        if (secondary != 0) {
            return Integer.signum(secondary);
        }
        for (int i = 0; i < Math.min(a.length(), b.length()); i += 1) {
            char x = a.charAt(i);
            char y = b.charAt(i);
            if (x != y) {
                if (Character.isLowerCase(x) != Character.isLowerCase(y)) {
                    return Character.isLowerCase(x) ? -1 : 1;
                }
                return Character.compare(x, y);
            }
        }
        return Integer.compare(a.length(), b.length());
    }

    private static int byWeight(String a, String b) {
        int length = Math.min(a.length(), b.length());
        for (int i = 0; i < length; i += 1) {
            int difference = weight(a.charAt(i)) - weight(b.charAt(i));
            if (difference != 0) {
                return Integer.signum(difference);
            }
        }
        return Integer.compare(a.length(), b.length());
    }

    private static int weight(char c) {
        if (Text.isSpace(c)) {
            return c;
        }
        int punctuation = PUNCTUATION.indexOf(c);
        if (punctuation >= 0) {
            return 0x10000 + punctuation;
        }
        if (c >= '0' && c <= '9') {
            return 0x20000 + c;
        }
        if (Character.isLetter(c)) {
            return 0x40000 + Character.toLowerCase(c);
        }
        return 0x30000 + c;
    }

    /** Lower case with the accents taken off, which is all the first level sees of a letter. */
    private static String strip(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
