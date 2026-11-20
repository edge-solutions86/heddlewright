package io.github.rkaushikdev.heddlewright.draft;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Ids;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Problem;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.util.ArrayList;
import java.util.List;

/**
 * A draft is the whole instruction for a piece of cloth on a treadle loom: threading, tie-up,
 * treadling, and which way the shed opens.
 *
 * <p>Building one normalises every part, so nothing downstream has to guess at a shorthand, and
 * gives it an id derived from the cloth it weaves.
 */
public record Draft(
        String name,
        int shafts,
        int treadles,
        Shed shed,
        List<Integer> threading,
        List<List<Integer>> tieup,
        List<List<Integer>> treadling,
        int ends,
        int picks,
        String notes,
        String id) {

    /** Everything a review wants to know without opening the drawdown. */
    public record Summary(
            String id,
            String name,
            int shafts,
            int treadles,
            int ends,
            int picks,
            Shed shed,
            int shaftsUsed,
            double warpFaceRatio) {}

    /** Copies the lists. */
    public Draft {
        threading = List.copyOf(threading);
        tieup = List.copyOf(tieup);
        treadling = List.copyOf(treadling);
    }

    /** Build a normalised draft from a plain record, or throw naming what is wrong with it. */
    public static Draft create(Object input) {
        JsonObject record = Check.plainObject(input instanceof Draft draft ? draft.toRecord() : input, "draft");
        Json given = record.get("name");
        String name = Check.nonEmptyString(given.isNullish() ? Json.of("untitled") : given, "name");
        int shafts = Check.positiveInteger(record.get("shafts"), "shafts");
        int treadles = Check.positiveInteger(record.get("treadles"), "treadles");
        Shed shed = record.get("shed").isNullish() ? Shed.RISING : Shed.parse(record.get("shed"));
        List<Integer> threading = Threading.assertThreading(record.get("threading"), shafts);
        List<List<Integer>> tieup = Tieup.normaliseTieup(record.get("tieup"), shafts);
        List<List<Integer>> treadling = Treadling.normaliseTreadling(record.get("treadling"), treadles);

        if (tieup.size() != treadles) {
            throw new DraftException(
                    "the tie-up does not have one entry per treadle",
                    Json.obj("treadles", treadles, "tied", tieup.size()));
        }
        Json notes = record.get("notes");
        String text = notes.isNullish() ? "" : notes instanceof Json.Str str ? str.value() : Text.string(notes);
        String id = draftId(shafts, treadles, shed, threading, tieup, treadling);
        return new Draft(
                name,
                shafts,
                treadles,
                shed,
                threading,
                tieup,
                treadling,
                threading.size(),
                treadling.size(),
                text,
                id);
    }

    /** A content-derived id, stable across machines and key order. */
    public static String draftId(Draft draft) {
        return draftId(draft.shafts, draft.treadles, draft.shed, draft.threading, draft.tieup, draft.treadling);
    }

    private static String draftId(
            int shafts,
            int treadles,
            Shed shed,
            List<Integer> threading,
            List<List<Integer>> tieup,
            List<List<Integer>> treadling) {
        return Ids.contentId(
                "draft",
                Json.canonicalStringify(Json.obj(
                        "shafts", shafts,
                        "treadles", treadles,
                        "shed", shed,
                        "threading", threading,
                        "tieup", tieup,
                        "treadling", treadling)));
    }

    /** The plain record this draft reads back from, with every field. */
    public JsonObject toRecord() {
        return (JsonObject) Json.of(this);
    }

    /** The shape and the face of the draft. */
    public Summary summary() {
        Matrix cloth = Drawdown.drawdownFromDraft(this);
        return new Summary(
                id,
                name,
                shafts,
                treadles,
                ends,
                picks,
                shed,
                Threading.shaftsUsed(threading),
                Drawdown.warpFaceRatio(cloth));
    }

    /**
     * Every problem a draft can have that does not stop it being a draft: idle shafts, spare
     * treadles, threads that never bind. Returned as a list so a report can group them.
     */
    public List<Problem> inspect() {
        List<Problem> problems = new ArrayList<>();
        for (int shaft : Threading.idleShafts(threading, shafts)) {
            problems.add(new Problem("idle_shaft", "shaft " + shaft + " carries no ends", Json.obj("shaft", shaft)));
        }
        for (int treadle : Tieup.untiedTreadles(tieup, shafts)) {
            problems.add(new Problem(
                    "untied_treadle", "treadle " + treadle + " lifts nothing", Json.obj("treadle", treadle)));
        }
        for (int treadle : Treadling.unusedTreadles(treadling, treadles)) {
            problems.add(new Problem(
                    "unused_treadle", "treadle " + treadle + " is never pressed", Json.obj("treadle", treadle)));
        }
        Matrix cloth = Drawdown.drawdownFromDraft(this);
        for (Drawdown.UnwovenEnd end : Drawdown.unwovenEnds(cloth)) {
            problems.add(new Problem(
                    "unwoven_end",
                    "end " + end.end() + " is " + end.state() + " through the repeat",
                    Json.obj("end", end.end(), "state", end.state())));
        }
        for (Drawdown.UnwovenPick pick : Drawdown.unwovenPicks(cloth)) {
            problems.add(new Problem(
                    "unwoven_pick",
                    "pick " + pick.pick() + " is " + pick.state(),
                    Json.obj("pick", pick.pick(), "state", pick.state())));
        }
        return List.copyOf(problems);
    }

    /** Throw on the first problem, for callers that want a draft or nothing. */
    public Draft assertSound() {
        List<Problem> problems = inspect();
        if (!problems.isEmpty()) {
            throw new DraftException(problems.get(0).message(), Json.obj("problems", problems));
        }
        return this;
    }

    /** A copy with some fields replaced, renormalised and given a fresh id. */
    public Draft revise(Object changes) {
        return create(toRecord().merge(Check.plainObject(changes, "changes")));
    }

    /** Two drafts weave the same cloth when their ids agree. */
    public static boolean sameDraft(Draft a, Draft b) {
        return draftId(a).equals(draftId(b));
    }
}
