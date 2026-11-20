package io.github.rkaushikdev.heddlewright.tools;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.heddlewright.draft.Draft;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BenchTest {

    @Nested
    class Time {

        @Test
        void runsTheWorkTheNumberOfRoundsItIsGiven() {
            int[] seen = {0};
            Bench.Timing result = Bench.time(5, round -> {
                seen[0] += 1;
                return seen[0];
            });
            assertThat(seen[0]).isEqualTo(5);
            assertThat(result.rounds()).isEqualTo(5);
            assertThat(result.last()).isEqualTo(5);
        }

        @Test
        void reportsAMillisecondFigureThatIsNotNegative() {
            assertThat(Bench.time(3, round -> 1).milliseconds()).isGreaterThanOrEqualTo(0);
        }

        @Test
        void passesTheRoundNumberToTheWork() {
            List<Integer> seen = new ArrayList<>();
            Bench.time(3, seen::add);
            assertThat(seen).containsExactly(0, 1, 2);
        }
    }

    @Nested
    class BenchDraft {

        @Test
        void buildsADraftOfTheSizeAskedFor() {
            Draft draft = Bench.benchDraft(64, 32);
            assertThat(draft.ends()).isEqualTo(64);
            assertThat(draft.picks()).isEqualTo(32);
            assertThat(draft.shafts()).isEqualTo(8);
        }
    }

    @Nested
    class Benchmarks {

        @Test
        void namesEveryBenchmarkAndGivesItSomethingToRun() {
            for (Bench.Benchmark benchmark : Bench.benchmarks()) {
                assertThat(benchmark.name()).isNotEmpty();
                assertThat(benchmark.run()).isNotNull();
            }
        }

        @Test
        void coversTheDrawdownTheAnalysisAndTheCardDeck() {
            List<String> names =
                    Bench.benchmarks().stream().map(Bench.Benchmark::name).toList();
            assertThat(names).anyMatch(name -> name.contains("drawdown"));
            assertThat(names).anyMatch(name -> name.contains("floats"));
            assertThat(names).anyMatch(name -> name.contains("deck"));
        }
    }

    @Nested
    class RunBenchmarks {

        // Run once and share the results. Each benchmark weaves a two hundred by two hundred cloth
        // several times over, so running the whole set once per test would be slow for nothing.
        private static final List<Bench.Result> RESULTS = Bench.runBenchmarks();

        @Test
        void runsEveryOneAndReportsATiming() {
            assertThat(RESULTS).hasSameSizeAs(Bench.benchmarks());
            for (Bench.Result result : RESULTS) {
                assertThat(result.rounds()).isGreaterThan(0);
                assertThat(result.milliseconds()).isGreaterThanOrEqualTo(0);
            }
        }

        @Test
        void doesItsWorkRatherThanReportingAnEmptyRound() {
            for (Bench.Result result : RESULTS) {
                assertThat(result.last()).isNotNull();
            }
        }

        @Test
        void namesEveryBenchmarkInItsResult() {
            assertThat(RESULTS.stream().map(Bench.Result::name).toList())
                    .isEqualTo(Bench.benchmarks().stream()
                            .map(Bench.Benchmark::name)
                            .toList());
        }

        @Test
        void doesNotRunItselfOnLoad() {
            String text = Sources.read(Sources.mainSources().resolve("tools/Bench.java"));
            assertThat(text).contains("public static void main(String[] args)");
            assertThat(text).doesNotContainPattern("(?m)^\\s*static\\s*\\{");
        }
    }
}
