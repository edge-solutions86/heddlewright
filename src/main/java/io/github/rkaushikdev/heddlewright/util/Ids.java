package io.github.rkaushikdev.heddlewright.util;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identifiers.
 *
 * <p>Nothing here reaches for randomness: a draft, a card deck and a warp beam all get an id
 * derived from what they are, so replaying a journal on a second machine produces the same
 * references.
 */
public final class Ids {

    /** A reference split into its prefix, its number and the width the number was padded to. */
    public record Reference(String prefix, int number, int width) {}

    private static final Pattern REFERENCE = Pattern.compile("^([A-Za-z][A-Za-z0-9-]*?)(\\d+)$");
    private static final Pattern MARKS = Pattern.compile("[\\x{0300}-\\x{036f}]");
    private static final Pattern STRIP = Pattern.compile("[^a-z0-9]+");

    private Ids() {}

    /** Lowercase, hyphen-joined, safe in a filename and in a URL path segment, at most 64 long. */
    public static String slug(String text) {
        return slug(text, 64);
    }

    /** {@link #slug(String)} cut to a chosen length. */
    public static String slug(String text, int maxLength) {
        Check.nonEmptyString(text, "text");
        Check.integer(maxLength, "maxLength", Bounds.between(1, 256));
        String folded =
                MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFKD)).replaceAll("");
        String cleaned =
                STRIP.matcher(folded.toLowerCase(Locale.ROOT)).replaceAll("-").replaceAll("^-+|-+$", "");
        if (cleaned.isEmpty()) {
            throw new InvalidArgumentException("text has no slug-able characters", Json.obj("text", text));
        }
        return cleaned.substring(0, Math.min(maxLength, cleaned.length())).replaceAll("-+$", "");
    }

    /**
     * FNV-1a over the UTF-8 bytes, returned as eight lowercase hex digits. Short enough to read off a
     * card label and stable across machines.
     */
    public static String stableHash(String text) {
        if (text == null) {
            throw new InvalidArgumentException("stableHash needs a string", Json.obj("got", "object"));
        }
        int hash = 0x811c9dc5;
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xff;
            hash *= 0x01000193;
        }
        return String.format("%08x", hash);
    }

    /** A prefixed, content-derived id such as {@code draft-8f21ac03}. Content that is not text is hashed as JSON. */
    public static String contentId(String prefix, Object content) {
        Check.nonEmptyString(prefix, "prefix");
        String text = content instanceof String plain ? plain : Json.stringify(content);
        return slug(prefix) + "-" + stableHash(text);
    }

    /** The slug of {@code base}, which nothing has taken yet. */
    public static String uniqueId(String base) {
        return uniqueId(base, Set.of());
    }

    /** Hand out {@code name}, {@code name-2}, {@code name-3} and so on against the ids already taken. */
    public static String uniqueId(String base, Collection<String> taken) {
        String wanted = slug(base);
        Set<String> seen = new HashSet<>(taken == null ? Set.of() : taken);
        if (!seen.contains(wanted)) {
            return wanted;
        }
        int n = 2;
        while (seen.contains(wanted + "-" + n)) {
            n += 1;
        }
        return wanted + "-" + n;
    }

    /** A counter padded to four digits, starting at one. */
    public static ReferenceCounter referenceCounter(String prefix) {
        return new ReferenceCounter(prefix, 4, 1);
    }

    /** A counter padded to a chosen width, starting at one. */
    public static ReferenceCounter referenceCounter(String prefix, int width) {
        return new ReferenceCounter(prefix, width, 1);
    }

    /**
     * A counter that formats as a zero-padded reference, the way a shed numbers card decks and warp
     * beams.
     */
    public static ReferenceCounter referenceCounter(String prefix, int width, int start) {
        return new ReferenceCounter(prefix, width, start);
    }

    /** Split a reference back into its prefix and number, or empty when it does not fit. */
    public static Optional<Reference> parseReference(String reference) {
        Check.nonEmptyString(reference, "reference");
        Matcher match = REFERENCE.matcher(reference);
        if (!match.matches()) {
            return Optional.empty();
        }
        return Optional.of(new Reference(
                match.group(1), Integer.parseInt(match.group(2)), match.group(2).length()));
    }
}
