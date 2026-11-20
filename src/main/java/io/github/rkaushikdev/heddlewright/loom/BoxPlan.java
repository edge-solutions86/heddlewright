package io.github.rkaushikdev.heddlewright.loom;

import io.github.rkaushikdev.heddlewright.loom.BoxMotion.Allotment;
import io.github.rkaushikdev.heddlewright.loom.BoxMotion.Boxing;
import io.github.rkaushikdev.heddlewright.loom.BoxMotion.WeftRun;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Everything a weaver needs to gait a box pattern up: the allotment, the chain to lace, what it
 * costs in speed, and whether the loom can pick it at all. The speed and the picks lost are given
 * only when the request names the loom's speed.
 */
public record BoxPlan(
        int picks,
        int colours,
        int boxes,
        Boxing boxedAt,
        List<Allotment> allotment,
        List<Integer> chain,
        int changes,
        int longestLift,
        List<WeftRun> runs,
        List<JsonObject> share,
        OptionalDouble picksLost,
        OptionalDouble picksMinute) {

    private static final Set<String> TAKES = Set.of("pattern", "boxes", "boxedAt", "picksMinute");

    /** Copies the lists. */
    public BoxPlan {
        allotment = List.copyOf(allotment);
        chain = List.copyOf(chain);
        runs = List.copyOf(runs);
        share = List.copyOf(share);
    }

    /**
     * Gait a pattern up from a plain request: the {@code pattern}, the {@code boxes} the loom has,
     * how it is {@code boxedAt} and, if known, its {@code picksMinute}. Anything else in the request
     * is refused rather than ignored.
     */
    public static BoxPlan boxMotionPlan(Object request) {
        JsonObject record = Check.plainObject(request, "request");
        List<String> unknown = new ArrayList<>();
        for (String key : record.keys()) {
            if (!TAKES.contains(key)) {
                unknown.add(key);
            }
        }
        if (!unknown.isEmpty()) {
            throw new InvalidArgumentException(
                    "boxMotionPlan does not take " + String.join(", ", unknown), Json.obj("unknown", unknown));
        }
        List<Object> pattern = Check.array(record.get("pattern"), "pattern");
        int boxes = Check.positiveInteger(record.get("boxes"), "boxes");
        Json boxing = record.get("boxedAt");
        Json speed = record.get("picksMinute");
        return boxMotionPlan(
                pattern,
                boxes,
                boxing.isMissing() ? Boxing.BOTH : Boxing.parse(boxing),
                speed.isMissing() ? OptionalDouble.empty() : OptionalDouble.of(Check.number(speed, "picksMinute")));
    }

    /** Gait a pattern up on a loom with this many boxes, boxed this way, at a known speed or not. */
    public static BoxPlan boxMotionPlan(List<?> pattern, int boxes, Boxing boxedAt, OptionalDouble picksMinute) {
        List<Allotment> allotment = BoxMotion.allotBoxes(pattern, boxes);
        BoxMotion.assertShuttlesReturn(pattern, boxedAt);
        List<Integer> chain = BoxMotion.boxChain(pattern, allotment);
        return new BoxPlan(
                chain.size(),
                allotment.size(),
                boxes,
                boxedAt,
                allotment,
                chain,
                BoxMotion.boxChanges(chain),
                BoxMotion.longestLift(chain),
                BoxMotion.weftRuns(pattern),
                BoxMotion.pickShare(allotment),
                picksMinute.isPresent()
                        ? OptionalDouble.of(BoxMotion.picksLostToChanging(chain))
                        : OptionalDouble.empty(),
                picksMinute.isPresent()
                        ? OptionalDouble.of(BoxMotion.speedWithChanging(chain, picksMinute.getAsDouble()))
                        : OptionalDouble.empty());
    }
}
