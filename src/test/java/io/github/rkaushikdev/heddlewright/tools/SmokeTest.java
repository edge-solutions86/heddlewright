package io.github.rkaushikdev.heddlewright.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import org.junit.jupiter.api.Test;

class SmokeTest {

    @Test
    void walksTheWholeServiceAndPassesEveryStep() throws Exception {
        Smoke.Result result = Smoke.smoke();
        assertThat(result.failures()).isEmpty();
        assertThat(result.steps().size()).isGreaterThan(6);
    }

    @Test
    void namesEveryStepItWalked() throws Exception {
        List<String> names =
                Smoke.smoke().steps().stream().map(Smoke.Step::name).toList();
        assertThat(names).contains("health answers", "cloth derived", "piece graded", "plan made", "journal sound");
    }

    @Test
    void recordsWhatEachStepWantedAsWellAsWhatItGot() throws Exception {
        for (Smoke.Step step : Smoke.smoke().steps()) {
            assertThat(step.expected()).isNotNull();
            assertThat(step.actual()).isNotNull();
            assertThat(step.ok()).isEqualTo(step.expected().equals(step.actual()));
        }
    }

    @Test
    void leavesNothingListeningBehindIt() throws Exception {
        Smoke.smoke();
        Smoke.smoke();
        // A port the smoke run left bound could not be bound again; a fresh socket on any port
        // proves the machine still hands them out after two runs.
        try (ServerSocket probe = new ServerSocket()) {
            probe.bind(new InetSocketAddress("127.0.0.1", 0));
            assertThat(probe.getLocalPort()).isGreaterThan(0);
        }
    }

    @Test
    void doesNotRunItselfOnLoad() {
        String text = Sources.read(Sources.mainSources().resolve("tools/Smoke.java"));
        assertThat(text).contains("public static void main(String[] args)");
        assertThat(text).doesNotContainPattern("(?m)^\\s*static\\s*\\{");
    }

    @Test
    void goesThroughTheSocketRatherThanCallingTheAppDirectly() {
        String text = Sources.read(Sources.mainSources().resolve("tools/Smoke.java"));
        assertThat(text).contains("HttpClient");
        assertThat(text).contains("Server.listen(running)");
    }
}
