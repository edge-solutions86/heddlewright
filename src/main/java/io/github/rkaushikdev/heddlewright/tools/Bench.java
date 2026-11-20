package io.github.rkaushikdev.heddlewright.tools;

import io.github.rkaushikdev.heddlewright.analysis.Floats;
import io.github.rkaushikdev.heddlewright.analysis.Interlacement;
import io.github.rkaushikdev.heddlewright.draft.Draft;
import io.github.rkaushikdev.heddlewright.draft.Drawdown;
import io.github.rkaushikdev.heddlewright.draft.Threading;
import io.github.rkaushikdev.heddlewright.draft.Tieup;
import io.github.rkaushikdev.heddlewright.draft.Treadling;
import io.github.rkaushikdev.heddlewright.jacquard.Card;
import io.github.rkaushikdev.heddlewright.jacquard.Deck;
import io.github.rkaushikdev.heddlewright.jacquard.Machine;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.util.Text;
import io.github.rkaushikdev.heddlewright.weave.Twill;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * The benchmarks.
 *
 * <p>These are not a performance target and nothing here fails on a slow machine. They exist so that
 * a change which makes the drawdown quadratic shows up as a number somebody can see, rather than as a
 * suite that has quietly started taking four minutes.
 */
public final class Bench {

    /** A timing: the rounds run, the milliseconds they took, and what the last round gave. */
    public record Timing(int rounds, double milliseconds, Object last) {}

    /** A benchmark: its name and what runs it. */
    public record Benchmark(String name, Supplier<Timing> run) {}

    /** A benchmark's name beside its timing. */
    public record Result(String name, int rounds, double milliseconds, Object last) {}

    private Bench() {}

    /** Time a piece of work over a number of rounds, handing it the round number, in milliseconds. */
    public static Timing time(int rounds, IntFunction<?> work) {
        long started = System.nanoTime();
        Object last = null;
        for (int round = 0; round < rounds; round += 1) {
            last = work.apply(round);
        }
        double elapsed = (System.nanoTime() - started) / 1e6;
        return new Timing(rounds, Numeric.roundTo(elapsed, 2), last);
    }

    /** An eight shaft draft of a given size, for the drawdown benchmarks. */
    public static Draft benchDraft(int ends, int picks) {
        return Draft.create(Json.obj(
                "name",
                "bench " + ends + " by " + picks,
                "shafts",
                8,
                "treadles",
                8,
                "threading",
                Threading.straightDraw(8, ends),
                "tieup",
                Tieup.twillTieup(8, 3),
                "treadling",
                Treadling.straightTreadling(8, picks)));
    }

    private static Matrix bigTwill() {
        return Twill.twillWeave("3/1", Twill.Options.DEFAULT.size(400, 400));
    }

    /** Every benchmark. */
    public static List<Benchmark> benchmarks() {
        Machine machine = Machine.defineMachine("bench", 8, 50);
        return List.of(
                new Benchmark(
                        "drawdown 200 by 200",
                        () -> time(20, round -> Drawdown.drawdownFromDraft(benchDraft(200, 200))
                                .rows())),
                new Benchmark("floats over a 200 by 200 cloth", () -> {
                    Matrix cloth = Drawdown.drawdownFromDraft(benchDraft(200, 200));
                    return time(
                            20,
                            round -> Floats.warpFloats(cloth).size()
                                    + Floats.weftFloats(cloth).size());
                }),
                new Benchmark("interlacement over a 200 by 200 cloth", () -> {
                    Matrix cloth = Drawdown.drawdownFromDraft(benchDraft(200, 200));
                    return time(20, round -> Interlacement.interlacementFactor(cloth));
                }),
                new Benchmark("cutting a 400 card deck", () -> {
                    Matrix plan = bigTwill();
                    return time(5, round -> Deck.deckFromLiftplan(machine, plan).size());
                }),
                new Benchmark("identifying a 400 card deck", () -> {
                    List<Card> deck = Deck.deckFromLiftplan(machine, bigTwill());
                    return time(5, round -> Deck.deckId(deck));
                }));
    }

    /** Run them all and report the timings. */
    public static List<Result> runBenchmarks() {
        return benchmarks().stream()
                .map(benchmark -> {
                    Timing timing = benchmark.run().get();
                    return new Result(benchmark.name(), timing.rounds(), timing.milliseconds(), timing.last());
                })
                .toList();
    }

    /** Run every benchmark and print its timing. */
    public static void main(String[] args) {
        for (Result result : runBenchmarks()) {
            String each = Text.fixed(result.milliseconds() / result.rounds(), 2);
            System.out.println(result.name() + ": " + result.rounds() + " rounds, " + Text.number(result.milliseconds())
                    + " ms, " + each + " each");
        }
    }
}
