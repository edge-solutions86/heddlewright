package io.github.rkaushikdev.heddlewright;

import io.github.rkaushikdev.heddlewright.analysis.ColourOrder;
import io.github.rkaushikdev.heddlewright.analysis.Construction;
import io.github.rkaushikdev.heddlewright.analysis.Crimp;
import io.github.rkaushikdev.heddlewright.analysis.Diff;
import io.github.rkaushikdev.heddlewright.analysis.Floats;
import io.github.rkaushikdev.heddlewright.analysis.Interlacement;
import io.github.rkaushikdev.heddlewright.analysis.Motif;
import io.github.rkaushikdev.heddlewright.analysis.Report;
import io.github.rkaushikdev.heddlewright.analysis.Sett;
import io.github.rkaushikdev.heddlewright.analysis.WarpWeft;
import io.github.rkaushikdev.heddlewright.analysis.Weight;
import io.github.rkaushikdev.heddlewright.cloth.CutPlan;
import io.github.rkaushikdev.heddlewright.cloth.Finishing;
import io.github.rkaushikdev.heddlewright.cloth.FourPoint;
import io.github.rkaushikdev.heddlewright.cloth.Inspection;
import io.github.rkaushikdev.heddlewright.cloth.Lot;
import io.github.rkaushikdev.heddlewright.cloth.Roll;
import io.github.rkaushikdev.heddlewright.draft.Draft;
import io.github.rkaushikdev.heddlewright.draft.Drawdown;
import io.github.rkaushikdev.heddlewright.draft.Equivalence;
import io.github.rkaushikdev.heddlewright.draft.Ini;
import io.github.rkaushikdev.heddlewright.draft.Liftplan;
import io.github.rkaushikdev.heddlewright.draft.Shed;
import io.github.rkaushikdev.heddlewright.draft.Threading;
import io.github.rkaushikdev.heddlewright.draft.Tieup;
import io.github.rkaushikdev.heddlewright.draft.Transform;
import io.github.rkaushikdev.heddlewright.draft.Treadling;
import io.github.rkaushikdev.heddlewright.draft.Wif;
import io.github.rkaushikdev.heddlewright.draft.WifWrite;
import io.github.rkaushikdev.heddlewright.jacquard.Card;
import io.github.rkaushikdev.heddlewright.jacquard.Cylinder;
import io.github.rkaushikdev.heddlewright.jacquard.Deck;
import io.github.rkaushikdev.heddlewright.jacquard.Design;
import io.github.rkaushikdev.heddlewright.jacquard.Estimate;
import io.github.rkaushikdev.heddlewright.jacquard.Harness;
import io.github.rkaushikdev.heddlewright.jacquard.Lacing;
import io.github.rkaushikdev.heddlewright.jacquard.Machine;
import io.github.rkaushikdev.heddlewright.jacquard.Punch;
import io.github.rkaushikdev.heddlewright.jacquard.Verify;
import io.github.rkaushikdev.heddlewright.loom.BoxMotion;
import io.github.rkaushikdev.heddlewright.loom.BoxPlan;
import io.github.rkaushikdev.heddlewright.loom.Dobby;
import io.github.rkaushikdev.heddlewright.loom.Efficiency;
import io.github.rkaushikdev.heddlewright.loom.Letoff;
import io.github.rkaushikdev.heddlewright.loom.Loom;
import io.github.rkaushikdev.heddlewright.loom.Registry;
import io.github.rkaushikdev.heddlewright.loom.Requirements;
import io.github.rkaushikdev.heddlewright.loom.ShedTiming;
import io.github.rkaushikdev.heddlewright.loom.Speed;
import io.github.rkaushikdev.heddlewright.loom.Takeup;
import io.github.rkaushikdev.heddlewright.loom.Temple;
import io.github.rkaushikdev.heddlewright.service.App;
import io.github.rkaushikdev.heddlewright.service.Middleware;
import io.github.rkaushikdev.heddlewright.service.Router;
import io.github.rkaushikdev.heddlewright.store.Journal;
import io.github.rkaushikdev.heddlewright.store.Repository;
import io.github.rkaushikdev.heddlewright.store.Snapshot;
import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Collation;
import io.github.rkaushikdev.heddlewright.util.ConflictException;
import io.github.rkaushikdev.heddlewright.util.Cycle;
import io.github.rkaushikdev.heddlewright.util.Dates;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Errors;
import io.github.rkaushikdev.heddlewright.util.Group;
import io.github.rkaushikdev.heddlewright.util.HeddleException;
import io.github.rkaushikdev.heddlewright.util.Ids;
import io.github.rkaushikdev.heddlewright.util.Indexing;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Limits;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.util.Problem;
import io.github.rkaushikdev.heddlewright.util.Range;
import io.github.rkaushikdev.heddlewright.util.ReferenceCounter;
import io.github.rkaushikdev.heddlewright.util.Runs;
import io.github.rkaushikdev.heddlewright.util.Table;
import io.github.rkaushikdev.heddlewright.util.Text;
import io.github.rkaushikdev.heddlewright.util.Units;
import io.github.rkaushikdev.heddlewright.warp.Beam;
import io.github.rkaushikdev.heddlewright.warp.Creel;
import io.github.rkaushikdev.heddlewright.warp.Heddles;
import io.github.rkaushikdev.heddlewright.warp.Reed;
import io.github.rkaushikdev.heddlewright.warp.Warping;
import io.github.rkaushikdev.heddlewright.weave.Blocks;
import io.github.rkaushikdev.heddlewright.weave.Catalogue;
import io.github.rkaushikdev.heddlewright.weave.Combine;
import io.github.rkaushikdev.heddlewright.weave.Crepe;
import io.github.rkaushikdev.heddlewright.weave.DoubleCloth;
import io.github.rkaushikdev.heddlewright.weave.Honeycomb;
import io.github.rkaushikdev.heddlewright.weave.Leno;
import io.github.rkaushikdev.heddlewright.weave.Overshot;
import io.github.rkaushikdev.heddlewright.weave.Plain;
import io.github.rkaushikdev.heddlewright.weave.Satin;
import io.github.rkaushikdev.heddlewright.weave.SummerWinter;
import io.github.rkaushikdev.heddlewright.weave.Twill;
import io.github.rkaushikdev.heddlewright.works.Costing;
import io.github.rkaushikdev.heddlewright.works.Despatch;
import io.github.rkaushikdev.heddlewright.works.Orders;
import io.github.rkaushikdev.heddlewright.works.Production;
import io.github.rkaushikdev.heddlewright.works.Schedule;
import io.github.rkaushikdev.heddlewright.works.Stock;
import io.github.rkaushikdev.heddlewright.yarn.Blend;
import io.github.rkaushikdev.heddlewright.yarn.Count;
import io.github.rkaushikdev.heddlewright.yarn.Twist;
import io.github.rkaushikdev.heddlewright.yarn.YarnPackage;
import java.util.List;
import java.util.Optional;

/**
 * The public surface.
 *
 * <p>Everything a caller outside the package should reach for is listed here under a namespace per
 * area, the class that holds that area's functions, rather than as one flat pile of names. The
 * namespaces match the packages, so a name found in the docs can be traced to the class it lives in
 * without a search. The value types those functions take and hand back are listed beside them.
 *
 * <p>The command line and the HTTP adapter are wiring rather than surface, and are reached through
 * {@code cli.Heddle} and {@code service.Server}.
 */
public final class Heddlewright {

    /** The library version, kept beside the surface it describes. */
    public static final String VERSION = App.VERSION;

    /** One namespace: the name a caller knows it by and the class that holds it. */
    public record Namespace(String name, Class<?> type) {}

    /** Every namespace, area by area, lowest layer first. */
    public static final List<Namespace> NAMESPACES = List.of(
            new Namespace("errors", Errors.class),
            new Namespace("check", Check.class),
            new Namespace("matrix", Matrix.class),
            new Namespace("numeric", Numeric.class),
            new Namespace("runs", Runs.class),
            new Namespace("cycle", Cycle.class),
            new Namespace("ids", Ids.class),
            new Namespace("json", Json.class),
            new Namespace("table", Table.class),
            new Namespace("range", Range.class),
            new Namespace("indexing", Indexing.class),
            new Namespace("group", Group.class),
            new Namespace("dates", Dates.class),
            new Namespace("units", Units.class),
            new Namespace("limits", Limits.class),
            new Namespace("threading", Threading.class),
            new Namespace("tieup", Tieup.class),
            new Namespace("treadling", Treadling.class),
            new Namespace("liftplan", Liftplan.class),
            new Namespace("drawdown", Drawdown.class),
            new Namespace("draft", Draft.class),
            new Namespace("ini", Ini.class),
            new Namespace("wif", Wif.class),
            new Namespace("wifWrite", WifWrite.class),
            new Namespace("transform", Transform.class),
            new Namespace("equivalence", Equivalence.class),
            new Namespace("plain", Plain.class),
            new Namespace("twill", Twill.class),
            new Namespace("satin", Satin.class),
            new Namespace("honeycomb", Honeycomb.class),
            new Namespace("combine", Combine.class),
            new Namespace("blocks", Blocks.class),
            new Namespace("overshot", Overshot.class),
            new Namespace("summerWinter", SummerWinter.class),
            new Namespace("leno", Leno.class),
            new Namespace("doublecloth", DoubleCloth.class),
            new Namespace("crepe", Crepe.class),
            new Namespace("catalogue", Catalogue.class),
            new Namespace("floats", Floats.class),
            new Namespace("interlacement", Interlacement.class),
            new Namespace("sett", Sett.class),
            new Namespace("crimp", Crimp.class),
            new Namespace("weight", Weight.class),
            new Namespace("colourorder", ColourOrder.class),
            new Namespace("diff", Diff.class),
            new Namespace("motif", Motif.class),
            new Namespace("report", Report.class),
            new Namespace("machine", Machine.class),
            new Namespace("harness", Harness.class),
            new Namespace("card", Card.class),
            new Namespace("deck", Deck.class),
            new Namespace("lacing", Lacing.class),
            new Namespace("cylinder", Cylinder.class),
            new Namespace("punch", Punch.class),
            new Namespace("estimate", Estimate.class),
            new Namespace("deckVerify", Verify.class),
            new Namespace("design", Design.class),
            new Namespace("dobby", Dobby.class),
            new Namespace("shed", ShedTiming.class),
            new Namespace("takeup", Takeup.class),
            new Namespace("speed", Speed.class),
            new Namespace("efficiency", Efficiency.class),
            new Namespace("registry", Registry.class),
            new Namespace("letoff", Letoff.class),
            new Namespace("temple", Temple.class),
            new Namespace("boxmotion", BoxMotion.class),
            new Namespace("count", Count.class),
            new Namespace("yarnPackage", YarnPackage.class),
            new Namespace("blend", Blend.class),
            new Namespace("twist", Twist.class),
            new Namespace("reed", Reed.class),
            new Namespace("warping", Warping.class),
            new Namespace("creel", Creel.class),
            new Namespace("beam", Beam.class),
            new Namespace("heddles", Heddles.class),
            new Namespace("fourpoint", FourPoint.class),
            new Namespace("inspection", Inspection.class),
            new Namespace("roll", Roll.class),
            new Namespace("cutplan", CutPlan.class),
            new Namespace("lot", Lot.class),
            new Namespace("finishing", Finishing.class),
            new Namespace("orders", Orders.class),
            new Namespace("schedule", Schedule.class),
            new Namespace("production", Production.class),
            new Namespace("costing", Costing.class),
            new Namespace("despatch", Despatch.class),
            new Namespace("stock", Stock.class),
            new Namespace("journal", Journal.class),
            new Namespace("repository", Repository.class),
            new Namespace("snapshot", Snapshot.class),
            new Namespace("router", Router.class),
            new Namespace("app", App.class),
            new Namespace("middleware", Middleware.class));

    /** The value types the namespaces take and hand back, which are reached through them. */
    public static final List<Class<?>> TYPES = List.of(
            Bounds.class,
            Collation.class,
            HeddleException.class,
            InvalidArgumentException.class,
            DraftException.class,
            CapacityException.class,
            NotFoundException.class,
            ConflictException.class,
            JsonArray.class,
            JsonObject.class,
            Problem.class,
            ReferenceCounter.class,
            Text.class,
            Shed.class,
            WarpWeft.class,
            Construction.class,
            Loom.class,
            BoxPlan.class,
            Requirements.class);

    private Heddlewright() {}

    /** The namespace a caller knows by a name, if there is one. */
    public static Optional<Class<?>> namespace(String name) {
        return NAMESPACES.stream()
                .filter(entry -> entry.name().equals(name))
                .<Class<?>>map(Namespace::type)
                .findFirst();
    }
}
