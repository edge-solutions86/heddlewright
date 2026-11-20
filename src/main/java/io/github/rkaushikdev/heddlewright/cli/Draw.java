package io.github.rkaushikdev.heddlewright.cli;

import io.github.rkaushikdev.heddlewright.analysis.Floats;
import io.github.rkaushikdev.heddlewright.analysis.Report;
import io.github.rkaushikdev.heddlewright.cli.Parse.Option;
import io.github.rkaushikdev.heddlewright.cli.Parse.Type;
import io.github.rkaushikdev.heddlewright.draft.Draft;
import io.github.rkaushikdev.heddlewright.draft.Drawdown;
import io.github.rkaushikdev.heddlewright.draft.Threading;
import io.github.rkaushikdev.heddlewright.draft.Tieup;
import io.github.rkaushikdev.heddlewright.draft.Treadling;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Collation;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Problem;
import io.github.rkaushikdev.heddlewright.util.Table;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The commands that draw a draft and read the cloth it makes.
 *
 * <p>They all take the same five options, declared once below, because a default that differs
 * between two commands drawing the same thing is a bug nobody notices until the two answers
 * disagree.
 */
public final class Draw {

    /** What this class puts in the register. */
    public static final List<String> DRAW_COMMANDS = List.of("draft", "floats", "check", "report");

    /** The options every drawing command takes. */
    public static final Map<String, Option> DRAW_OPTIONS = options(
            "style", Option.of(Type.STRING, "twill"),
            "shafts", Option.of(Type.INTEGER, 4),
            "ends", Option.of(Type.INTEGER, 16),
            "picks", Option.of(Type.INTEGER, 16),
            "notation", Option.of(Type.STRING, "2/2"));

    private record Row(String direction, int length, int count) {}

    private Draw() {}

    /** An ordered option spec from alternating names and options. */
    static Map<String, Option> options(Object... namesAndOptions) {
        Map<String, Option> out = new LinkedHashMap<>();
        for (int i = 0; i < namesAndOptions.length; i += 2) {
            out.put((String) namesAndOptions[i], (Option) namesAndOptions[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    /** The drawing options with more laid after them. */
    static Map<String, Option> withDrawOptions(Object... more) {
        Map<String, Option> out = new LinkedHashMap<>(DRAW_OPTIONS);
        out.putAll(options(more));
        return java.util.Collections.unmodifiableMap(out);
    }

    /**
     * Build the draft every command here draws, from the options record: a point draft for the point
     * style, otherwise a twill raising the first figure of the notation, kept clear of lifting every
     * shaft.
     */
    public static Draft buildDraft(Object options) {
        JsonObject record = Check.plainObject(options, "options");
        String style = Text.string(record.get("style"));
        int shafts = record.get("shafts").asInt();
        int ends = record.get("ends").asInt();
        int picks = record.get("picks").asInt();
        if (style.equals("point")) {
            return Draft.create(Json.obj(
                    "name",
                    "point on " + shafts,
                    "shafts",
                    shafts,
                    "treadles",
                    shafts,
                    "threading",
                    Threading.pointDraw(shafts, ends),
                    "tieup",
                    Tieup.directTieup(shafts),
                    "treadling",
                    Treadling.trompAsWrit(Threading.pointDraw(shafts, picks))));
        }
        String notation = Text.string(record.get("notation"));
        double raise = Text.parseInt(Text.split(notation, "/").get(0));
        double clamped = Math.min(Math.max(raise, 1), shafts - 1);
        return Draft.create(Json.obj(
                "name",
                notation + " twill on " + shafts,
                "shafts",
                shafts,
                "treadles",
                shafts,
                "threading",
                Threading.straightDraw(shafts, ends),
                "tieup",
                Tieup.twillTieup(shafts, Check.positiveInteger((Object) clamped, "raise")),
                "treadling",
                Treadling.straightTreadling(shafts, picks)));
    }

    /** Put the drawing commands in the register. */
    static void define() {
        Registry.define("draft", "draw a named draft and print its cloth", DRAW_OPTIONS, parsed -> {
            Draft draft = buildDraft(Parse.checkOptions(parsed, Registry.specOf("draft")));
            return String.join(
                    "\n",
                    Table.renderPairs(draft.summary()),
                    "",
                    Drawdown.drawdownFromDraft(draft).format());
        });

        Registry.define("floats", "measure the floats in a drawn draft", DRAW_OPTIONS, parsed -> {
            Matrix cloth =
                    Drawdown.drawdownFromDraft(buildDraft(Parse.checkOptions(parsed, Registry.specOf("floats"))));
            Floats.Histogram histogram = Floats.floatHistogram(cloth);
            List<Row> rows = new ArrayList<>();
            histogram.warp().forEach((length, count) -> rows.add(new Row("warp", length, count)));
            histogram.weft().forEach((length, count) -> rows.add(new Row("weft", length, count)));
            rows.sort(Comparator.comparing(Row::direction, Collation.ORDER).thenComparingInt(Row::length));
            List<Table.Column> columns =
                    List.of(Table.Column.of("direction"), Table.Column.of("length"), Table.Column.of("count"));
            return String.join(
                    "\n", Table.renderPairs(Floats.longestFloat(cloth)), "", Table.renderTable(rows, columns));
        });

        Registry.define("check", "report anything wrong with a drawn draft", DRAW_OPTIONS, parsed -> {
            List<Problem> problems = buildDraft(Parse.checkOptions(parsed, Registry.specOf("check")))
                    .inspect();
            if (problems.isEmpty()) {
                return "nothing wrong with it";
            }
            return Table.renderTable(problems, List.of(Table.Column.of("code"), Table.Column.of("message")));
        });

        Registry.define(
                "report",
                "measure a drawn draft every way the package can",
                withDrawOptions(
                        "count", Option.of(Type.NUMBER, 30),
                        "system", Option.of(Type.STRING, "ne"),
                        "floatLimit", Option.of(Type.INTEGER, 3)),
                parsed -> {
                    JsonObject options = Parse.checkOptions(parsed, Registry.specOf("report"));
                    return Report.renderReport(
                            Report.clothReport(buildDraft(options), Report.Options.fromRecord(options)));
                });
    }
}
