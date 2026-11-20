package io.github.rkaushikdev.heddlewright.works;

import io.github.rkaushikdev.heddlewright.analysis.Construction;
import io.github.rkaushikdev.heddlewright.analysis.Weight;
import io.github.rkaushikdev.heddlewright.loom.BoxMotion;
import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Costing a cloth.
 *
 * <p>A metre of cloth costs its yarn and its loom time, and which of the two dominates is not a
 * general fact: it turns on the pick density. At twenty picks a centimetre a loom running two
 * hundred picks a minute makes about five metres an hour, so an hour of loom time is spread over
 * very little cloth and the weaving is most of the cost. Open the sett out and the yarn takes over.
 *
 * <p>Nothing here decides anything. It lays the two terms out separately so a designer can see which
 * one their change actually moved.
 *
 * <p>A colour woven cloth pays a third thing that neither term shows: the loom stops for its box to
 * lift on every change of weft, so the same sett on the same loom weaves slower. That is loom time
 * and it is costed as loom time.
 *
 * <p>A costing job is a record, so a variation is the same record with one field changed: a
 * {@code construction}, {@code prices} ({@code warpPricePerKg}, {@code weftPricePerKg}), a
 * {@code loom} ({@code picksMinute}, {@code picksPerCm}, {@code ratePerHour}, and optionally
 * {@code efficiency}, {@code weftPattern} and {@code boxes}), {@code widthMetres}, and optionally
 * {@code wasteShare} and {@code overheads}.
 */
public final class Costing {

    /** The loom efficiency assumed when none is given. */
    public static final double EFFICIENCY = 0.85;

    /** The yarn waste allowed for when none is given, as a share. */
    public static final double WASTE_SHARE = 0.04;

    /** The yarn in a metre of cloth, warp and weft, and the cloth weight it was worked from. */
    public record YarnCost(double warp, double weft, double total, double gsm) {}

    /** The whole cost of a metre, split so the parts can be argued about. */
    public record Cost(
            double yarn,
            double yarnWithWaste,
            double weaving,
            double overheads,
            double total,
            double yarnShare,
            double gsm) {}

    /** Two jobs costed side by side, and which term the difference came from. */
    public record Comparison(
            Cost left, Cost right, double difference, String movedBy, double yarnMoved, double weavingMoved) {}

    private Costing() {}

    /** Yarn cost for a metre of cloth at its full width. */
    public static YarnCost yarnCostPerMetre(Object construction, Object prices, double widthMetres) {
        return yarnCost(construction, prices, widthMetres);
    }

    private static YarnCost yarnCost(Object construction, Object prices, Object widthGiven) {
        JsonObject price = Check.plainObject(prices, "prices");
        double widthMetres = Check.positiveNumber(widthGiven, "widthMetres");
        double warpPrice = Check.positiveNumber(price.get("warpPricePerKg"), "prices.warpPricePerKg");
        double weftPrice = Check.positiveNumber(price.get("weftPricePerKg"), "prices.weftPricePerKg");
        Weight.ClothWeight weight = Weight.clothWeightGsm(Construction.fromRecord(construction));
        double warp = (weight.warp() / 1000) * widthMetres * warpPrice;
        double weft = (weight.weft() / 1000) * widthMetres * weftPrice;
        return new YarnCost(
                Numeric.roundTo(warp, 4), Numeric.roundTo(weft, 4), Numeric.roundTo(warp + weft, 4), weight.total());
    }

    /**
     * The speed a weft order leaves, which is the quoted speed less the box changing. A loom record
     * with no weft pattern never changes and runs at the quoted figure; without a box count, the
     * pattern is given a box for each colour.
     */
    public static double runningSpeed(Object loom) {
        JsonObject record = Check.plainObject(loom, "loom");
        double picksMinute = Check.positiveNumber(record.get("picksMinute"), "picksMinute");
        Json pattern = record.get("weftPattern");
        if (pattern.isMissing()) {
            return picksMinute;
        }
        List<Object> picks = Check.array(pattern, "pattern");
        Json boxes = record.get("boxes");
        int count = boxes.isNullish() ? new LinkedHashSet<>(picks).size() : Check.positiveInteger(boxes, "boxes");
        List<BoxMotion.Allotment> allotment = BoxMotion.allotBoxes(picks, count);
        return BoxMotion.speedWithChanging(BoxMotion.boxChain(picks, allotment), picksMinute);
    }

    /**
     * Loom cost for a metre, from the speed, the sett and an hourly rate. A weft pattern may be
     * given, in which case the speed used is the one the box motion actually leaves rather than the
     * one on the loom's plate.
     */
    public static double loomCostPerMetre(Object loom) {
        JsonObject record = Check.plainObject(loom, "loom");
        double picksPerCm = Check.positiveNumber(record.get("picksPerCm"), "picksPerCm");
        double ratePerHour = Check.positiveNumber(record.get("ratePerHour"), "ratePerHour");
        Json given = record.get("efficiency");
        double efficiency =
                Check.number(given.isMissing() ? Json.of(EFFICIENCY) : given, "efficiency", Bounds.between(0.01, 1));
        double running = runningSpeed(record);
        double metresPerHour = ((running * 60) / picksPerCm / 100) * efficiency;
        return Numeric.roundTo(ratePerHour / metresPerHour, 4);
    }

    /** The usual waste allowance added to a yarn cost. */
    public static double withWaste(double cost) {
        return withWaste(cost, WASTE_SHARE);
    }

    /** Waste, as a share added to the yarn rather than a cost of its own. */
    public static double withWaste(double cost, double wasteShare) {
        Check.number(wasteShare, "wasteShare", Bounds.between(0, 1));
        return Numeric.roundTo(cost * (1 + wasteShare), 4);
    }

    /** The whole cost of a metre of a costing job, split so the parts can be argued about. */
    public static Cost costPerMetre(Object job) {
        JsonObject record = Check.plainObject(job, "job");
        Json overheadsGiven = record.get("overheads");
        double overheads =
                Check.number(overheadsGiven.isMissing() ? Json.of(0) : overheadsGiven, "overheads", Bounds.atLeast(0));
        YarnCost yarn = yarnCost(record.get("construction"), record.get("prices"), record.get("widthMetres"));
        Json waste = record.get("wasteShare");
        double yarnWithWaste =
                withWaste(yarn.total(), waste.isMissing() ? WASTE_SHARE : Check.number(waste, "wasteShare"));
        double weaving = loomCostPerMetre(record.get("loom"));
        double total = yarnWithWaste + weaving + overheads;
        return new Cost(
                yarn.total(),
                yarnWithWaste,
                weaving,
                Numeric.roundTo(overheads, 4),
                Numeric.roundTo(total, 4),
                Numeric.roundTo(yarnWithWaste / total, 4),
                yarn.gsm());
    }

    /** The price a wanted margin needs, on top of the cost. */
    public static double priceForMargin(double cost, double margin) {
        Check.number(margin, "margin", Bounds.between(0, 0.99));
        Check.positiveNumber(cost, "cost");
        return Numeric.roundTo(cost / (1 - margin), 4);
    }

    /** The margin a price actually gives, which is the check on the one above. */
    public static double marginAtPrice(double cost, double price) {
        Check.positiveNumber(cost, "cost");
        Check.positiveNumber(price, "price");
        if (price < cost) {
            throw new InvalidArgumentException("that price is below the cost", Json.obj("cost", cost, "price", price));
        }
        return Numeric.roundTo((price - cost) / price, 4);
    }

    /**
     * Two jobs costed side by side, and which term the difference came from. A change that moved the
     * yarn and a change that moved the loom time cost the same and are not the same decision.
     */
    public static Comparison compare(Object left, Object right) {
        Cost a = costPerMetre(left);
        Cost b = costPerMetre(right);
        double yarnMoved = Math.abs(b.yarnWithWaste() - a.yarnWithWaste());
        double weavingMoved = Math.abs(b.weaving() - a.weaving());
        return new Comparison(
                a,
                b,
                Numeric.roundTo(b.total() - a.total(), 4),
                yarnMoved >= weavingMoved ? "yarn" : "weaving",
                Numeric.roundTo(yarnMoved, 4),
                Numeric.roundTo(weavingMoved, 4));
    }
}
