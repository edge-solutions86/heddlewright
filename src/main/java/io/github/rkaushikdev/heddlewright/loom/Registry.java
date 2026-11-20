package io.github.rkaushikdev.heddlewright.loom;

import io.github.rkaushikdev.heddlewright.loom.BoxMotion.Boxing;
import io.github.rkaushikdev.heddlewright.loom.Loom.Picking;
import io.github.rkaushikdev.heddlewright.loom.Loom.Shedding;
import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.util.Problem;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The loom register.
 *
 * <p>A shed floor is a list of machines, each with a width, a shedding motion and a speed, and a
 * style can only be put on a loom that can actually hold it. Matching a style to a loom is a
 * capacity question with several parts, and this is where they are all asked at once.
 */
public final class Registry {

    /** The references of the looms with one shedding motion. */
    public record SheddingGroup(Shedding shedding, List<String> references) {
        /** Copies the references. */
        public SheddingGroup {
            references = List.copyOf(references);
        }
    }

    private Registry() {}

    /** Register a loom from its plain record. */
    public static Loom createLoom(Object input) {
        JsonObject record = Check.plainObject(input instanceof Loom loom ? loom.toRecord() : input, "loom");
        String reference = Check.nonEmptyString(record.get("reference"), "reference");
        Shedding shedding = Check.member(record.get("shedding"), "shedding", Shedding.values());
        Json picking = record.get("picking");
        Picking picks = picking.isNullish() ? Picking.SHUTTLE : Check.member(picking, "picking", Picking.values());
        double reedWidthCm = Check.positiveNumber(record.get("reedWidthCm"), "reedWidthCm");
        boolean jacquard = shedding == Shedding.JACQUARD;
        int shafts = jacquard ? 0 : Check.positiveInteger(record.get("shafts"), "shafts");
        int hooks = jacquard ? Check.positiveInteger(record.get("hooks"), "hooks") : 0;
        // A dobby or jacquard has no treadles at all, so zero is a real answer here rather than a
        // missing one.
        Json treadlesGiven = record.get("treadles");
        int treadles =
                Check.integer(treadlesGiven.isNullish() ? Json.of(0) : treadlesGiven, "treadles", Bounds.atLeast(0));
        double picksMinute = Check.positiveNumber(record.get("picksMinute"), "picksMinute");
        Json boxes = record.get("shuttleBoxes");
        int shuttleBoxes = boxes.isMissing() ? 1 : Check.positiveInteger(boxes, "shuttleBoxes");
        // Which ends of the sley are boxed. A loom with one box has nothing to be boxed at, so it reads
        // as boxed at one end and the weft rule never bites.
        Json boxing = record.get("boxedAt");
        Boxing boxedAt = boxing.isNullish() ? Boxing.ONE : Boxing.parse(boxing);
        Json installed = record.get("installed");
        return new Loom(
                reference,
                shedding,
                picks,
                reedWidthCm,
                shafts,
                hooks,
                treadles,
                picksMinute,
                shuttleBoxes,
                boxedAt,
                installed.isMissing() ? Json.NULL : installed);
    }

    /** Find a loom by reference. */
    public static Loom loomByReference(List<Loom> looms, String reference) {
        return looms.stream()
                .filter(loom -> loom.reference().equals(reference))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "no loom called \"" + reference + "\"", Json.obj("reference", reference)));
    }

    /**
     * What a style asks of a loom, read from its plain record: a width, a shedding capacity, and a
     * box for every weft colour.
     *
     * <p>A weft pattern may be given instead of a colour count. It carries more than the count does,
     * because the order the colours are picked in decides whether a loom boxed at one end can pick
     * them at all, and a bare count cannot say.
     */
    public static Requirements styleRequirements(Object input) {
        JsonObject record = Check.plainObject(input, "requirements");
        double widthCm = Check.positiveNumber(record.get("widthCm"), "widthCm");
        double shafts = Check.number(orZero(record.get("shafts")), "shafts", Bounds.atLeast(0));
        double hooks = Check.number(orZero(record.get("hooks")), "hooks", Bounds.atLeast(0));
        Json colours = record.get("weftColours");
        Json pattern = record.get("weftPattern");
        if (pattern.isMissing()) {
            int weftColours = Check.positiveInteger(colours.isNullish() ? Json.of(1) : colours, "weftColours");
            return new Requirements(widthCm, shafts, hooks, weftColours, Optional.empty());
        }
        List<Object> picks = Check.array(pattern, "weftPattern");
        List<String> named = BoxMotion.coloursIn(picks);
        if (!colours.isMissing() && !(colours.isNumber() && colours.asDouble() == named.size())) {
            throw new InvalidArgumentException(
                    "weftColours does not match the pattern given",
                    Json.obj("weftColours", colours, "counted", named.size()));
        }
        return new Requirements(widthCm, shafts, hooks, named.size(), Optional.of(BoxMotion.colours(picks)));
    }

    private static Json orZero(Json value) {
        return value.isMissing() ? Json.of(0) : value;
    }

    /** Every reason a loom cannot take a style, or an empty list when it can. */
    public static List<Problem> shortfalls(Loom loom, Requirements requirements) {
        List<Problem> problems = new ArrayList<>();
        boolean jacquard = loom.shedding() == Shedding.JACQUARD;
        if (requirements.widthCm() > loom.reedWidthCm()) {
            problems.add(new Problem(
                    "too_narrow",
                    loom.reference() + " is " + Text.number(loom.reedWidthCm()) + " cm in the reed",
                    Json.obj("needed", requirements.widthCm(), "available", loom.reedWidthCm())));
        }
        if (requirements.hooks() > 0 && !jacquard) {
            problems.add(new Problem(
                    "not_jacquard", loom.reference() + " has no jacquard head", Json.obj("shedding", loom.shedding())));
        }
        if (requirements.hooks() > loom.hooks() && jacquard) {
            problems.add(new Problem(
                    "too_few_hooks",
                    loom.reference() + " carries " + loom.hooks() + " hooks",
                    Json.obj("needed", requirements.hooks(), "available", loom.hooks())));
        }
        if (requirements.shafts() > loom.shafts() && !jacquard) {
            problems.add(new Problem(
                    "too_few_shafts",
                    loom.reference() + " carries " + loom.shafts() + " shafts",
                    Json.obj("needed", requirements.shafts(), "available", loom.shafts())));
        }
        if (requirements.weftColours() > loom.shuttleBoxes()) {
            problems.add(new Problem(
                    "too_few_boxes",
                    loom.reference() + " has " + loom.shuttleBoxes() + " shuttle boxes",
                    Json.obj("needed", requirements.weftColours(), "available", loom.shuttleBoxes())));
        }
        // Only a pattern can fail this one. A style quoted as a colour count says nothing about the
        // order, so there is nothing here to fail on.
        if (requirements.weftPattern().isPresent() && loom.boxedAt() == Boxing.ONE) {
            List<BoxMotion.WeftRun> odd =
                    BoxMotion.oddRuns(requirements.weftPattern().get());
            if (!odd.isEmpty()) {
                problems.add(new Problem(
                        "shuttle_strands",
                        loom.reference() + " is boxed at one end and the weft is not picked in pairs",
                        Json.obj("runs", odd)));
            }
        }
        return List.copyOf(problems);
    }

    /** Whether a loom can take a style at all. */
    public static boolean canWeave(Loom loom, Requirements requirements) {
        return shortfalls(loom, requirements).isEmpty();
    }

    /** Refuse a loom that cannot take the style, naming every reason. */
    public static Loom assertCanWeave(Loom loom, Requirements requirements) {
        List<Problem> problems = shortfalls(loom, requirements);
        if (!problems.isEmpty()) {
            throw new CapacityException(
                    loom.reference() + " cannot weave that style",
                    Json.obj("loom", loom.reference(), "problems", problems));
        }
        return loom;
    }

    /** Every loom that can take a style, fastest first. */
    public static List<Loom> loomsFor(List<Loom> looms, Requirements requirements) {
        List<Loom> found = new ArrayList<>();
        for (Loom loom : looms) {
            if (canWeave(loom, requirements)) {
                found.add(loom);
            }
        }
        found.sort(Comparator.comparingDouble(Loom::picksMinute).reversed());
        return List.copyOf(found);
    }

    /** How much of a loom's width a style uses, which is what idle reed costs. */
    public static double widthUtilisation(Loom loom, Requirements requirements) {
        return Numeric.roundTo(requirements.widthCm() / loom.reedWidthCm(), 4);
    }

    /** The register grouped by shedding motion, every motion listed even when it has no loom. */
    public static List<SheddingGroup> byShedding(List<Loom> looms) {
        List<SheddingGroup> out = new ArrayList<>();
        for (Shedding shedding : Shedding.values()) {
            out.add(new SheddingGroup(
                    shedding,
                    looms.stream()
                            .filter(loom -> loom.shedding() == shedding)
                            .map(Loom::reference)
                            .toList()));
        }
        return List.copyOf(out);
    }
}
