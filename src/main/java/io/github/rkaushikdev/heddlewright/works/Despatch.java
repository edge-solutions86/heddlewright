package io.github.rkaushikdev.heddlewright.works;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Collation;
import io.github.rkaushikdev.heddlewright.util.ConflictException;
import io.github.rkaushikdev.heddlewright.util.Group;
import io.github.rkaushikdev.heddlewright.util.Ids;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Despatch.
 *
 * <p>A note lists pieces, not metres. The metres follow from the pieces, and the moment a note is
 * allowed to carry a length of its own the two stop agreeing and nobody can say which is right. So
 * the length is always derived, and a piece can only be on one open note at a time.
 */
public final class Despatch {

    /** A piece as a note carries it. A grade that is not text is kept as its text. */
    public record NotePiece(String reference, double yards, String grade) {}

    /**
     * A despatch note. Its length is the sum of its pieces, and its id is a content id of everything
     * else. A carrier or a date that is not text is kept as its text.
     *
     * @param reference the note number
     * @param customer who it goes to
     * @param carrier who takes it, or empty
     * @param sentOn the day it went, if it has
     * @param pieces what it carries
     * @param yards the length of the pieces
     * @param id the content id
     */
    public record Note(
            String reference,
            String customer,
            String carrier,
            Optional<String> sentOn,
            List<NotePiece> pieces,
            double yards,
            String id)
            implements Json.Writable {

        /** Copies the pieces. */
        public Note {
            pieces = List.copyOf(pieces);
        }

        private JsonObject content() {
            return Json.obj(
                    "reference", reference,
                    "customer", customer,
                    "carrier", carrier,
                    "sentOn", sentOn.<Object>map(day -> day).orElse(Json.NULL),
                    "pieces", pieces,
                    "yards", yards);
        }

        /** The note as it is stored and sent, with a null date until it goes. */
        @Override
        public Json toJson() {
            return content().with("id", id);
        }
    }

    /** What has gone against an order, and how far short it is. */
    public record Coverage(double orderedYards, double sentYards, boolean covered, double shortYards) {}

    private Despatch() {}

    /**
     * Build a despatch note from its record: a reference, a customer and pieces already in the
     * register, with an optional carrier and the day it was sent.
     */
    public static Note createNote(Object input) {
        JsonObject record = Check.plainObject(input, "note");
        String reference = Check.nonEmptyString(record.get("reference"), "reference");
        String customer = Check.nonEmptyString(record.get("customer"), "customer");
        List<Object> given = Check.array(record.get("pieces"), "pieces", 1);
        List<NotePiece> pieces = new ArrayList<>(given.size());
        for (int index = 0; index < given.size(); index += 1) {
            String at = "pieces[" + index + "]";
            JsonObject piece = Check.plainObject(given.get(index), at);
            String ref = Check.nonEmptyString(piece.get("reference"), at + ".reference");
            double yards = Check.positiveNumber(piece.get("yards"), at + ".yards");
            Json grade = piece.get("grade");
            pieces.add(new NotePiece(ref, yards, grade.isNullish() ? "first" : Text.string(grade)));
        }
        Json sent = record.get("sentOn");
        Optional<String> sentOn = sent.isNullish() ? Optional.empty() : Optional.of(Text.string(sent));
        Json carrierGiven = record.get("carrier");
        String carrier = carrierGiven.isMissing() ? "" : Text.string(carrierGiven);
        double yards = Numeric.roundTo(Numeric.sumBy(pieces, NotePiece::yards, "pieces"), 3);
        Note draft = new Note(reference, customer, carrier, sentOn, pieces, yards, "");
        return new Note(reference, customer, carrier, sentOn, pieces, yards, Ids.contentId("note", draft.content()));
    }

    /** The lengths on a note, split by grade, most first. */
    public static List<JsonObject> noteByGrade(Note note) {
        return Group.tally(
                note.pieces(),
                Group.<NotePiece>by(NotePiece::grade)
                        .label("grade")
                        .field("yards", NotePiece::yards)
                        .sortBy("yards"));
    }

    /** Refuse a note that sends a piece twice, or one already on another note. */
    public static Note assertPiecesFree(Note note, List<Note> otherNotes) {
        Check.array(otherNotes, "otherNotes");
        Set<String> seen = new HashSet<>();
        for (NotePiece piece : note.pieces()) {
            if (!seen.add(piece.reference())) {
                throw new ConflictException(
                        piece.reference() + " is on this note twice", Json.obj("piece", piece.reference()));
            }
        }
        for (Note other : otherNotes) {
            for (NotePiece piece : other.pieces()) {
                if (seen.contains(piece.reference())) {
                    throw new ConflictException(
                            piece.reference() + " is already on " + other.reference(),
                            Json.obj("piece", piece.reference(), "note", other.reference()));
                }
            }
        }
        return note;
    }

    /** Mark a note sent on a day, which it cannot be twice. */
    public static Note sendNote(Note note, String on) {
        Check.nonEmptyString(on, "on");
        if (note.sentOn().isPresent()) {
            throw new ConflictException(
                    note.reference() + " went out on " + note.sentOn().get(),
                    Json.obj(
                            "reference",
                            note.reference(),
                            "sentOn",
                            note.sentOn().get()));
        }
        return createNote(((JsonObject) Json.of(note)).with("sentOn", on));
    }

    /** What is still waiting to go. */
    public static List<Note> pending(List<Note> notes) {
        Check.array(notes, "notes");
        return notes.stream().filter(note -> note.sentOn().isEmpty()).toList();
    }

    /** What has gone, most recent first, then by reference. */
    public static List<Note> sent(List<Note> notes) {
        Check.array(notes, "notes");
        return notes.stream()
                .filter(note -> note.sentOn().isPresent())
                .sorted(Comparator.comparing((Note note) -> note.sentOn().get(), Collation.ORDER.reversed())
                        .thenComparing(Note::reference, Collation.ORDER))
                .toList();
    }

    /** Find a note by reference. */
    public static Note noteByReference(List<Note> notes, String reference) {
        return notes.stream()
                .filter(note -> note.reference().equals(reference))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "no note called \"" + reference + "\"", Json.obj("reference", reference)));
    }

    /** What went out to each customer, most first. */
    public static List<JsonObject> byCustomer(List<Note> notes) {
        Check.array(notes, "notes");
        return Group.tally(
                notes,
                Group.<Note>by(Note::customer)
                        .label("customer")
                        .field("yards", Note::yards)
                        .sortBy("yards"));
    }

    /** Whether the notes to a customer cover what an order asked for, and by how much they fall short. */
    public static Coverage coversOrder(List<Note> notes, String order, double orderedYards) {
        Check.positiveNumber(orderedYards, "orderedYards");
        List<Note> mine =
                notes.stream().filter(note -> note.customer().equals(order)).toList();
        double sentYards = Numeric.roundTo(Numeric.sumBy(mine, Note::yards, "notes"), 3);
        if (sentYards < 0) {
            throw new InvalidArgumentException(
                    "a note cannot send a negative length", Json.obj("sentYards", sentYards));
        }
        return new Coverage(
                orderedYards,
                sentYards,
                sentYards >= orderedYards,
                Numeric.roundTo(Math.max(0, orderedYards - sentYards), 3));
    }
}
