package io.github.rkaushikdev.heddlewright.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.heddlewright.service.Middleware.Page;
import io.github.rkaushikdev.heddlewright.service.Middleware.Paging;
import io.github.rkaushikdev.heddlewright.service.Middleware.Wrapper;
import io.github.rkaushikdev.heddlewright.service.Router.Call;
import io.github.rkaushikdev.heddlewright.service.Router.Handler;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MiddlewareTest {

    private static final List<Integer> ROWS =
            IntStream.rangeClosed(1, 10).boxed().toList();

    private static Handler gone() {
        return call -> {
            throw new NotFoundException("gone");
        };
    }

    @Nested
    class Compose {

        @Test
        void runsTheWrappersWithTheFirstOneOutermost() throws Exception {
            List<String> order = new ArrayList<>();
            java.util.function.Function<String, Wrapper> mark = name -> next -> call -> {
                order.add("in " + name);
                Object result = next.handle(call);
                order.add("out " + name);
                return result;
            };
            Handler handler = Middleware.compose(call -> "done", List.of(mark.apply("a"), mark.apply("b")));
            assertThat(handler.handle(Call.empty())).isEqualTo("done");
            assertThat(order).containsExactly("in a", "in b", "out b", "out a");
        }

        @Test
        void returnsTheHandlerUntouchedWithNoWrappers() throws Exception {
            assertThat(Middleware.compose(call -> 1).handle(Call.empty())).isEqualTo(1);
        }

        @Test
        void refusesSomethingThatIsNotAHandler() {
            assertThatThrownBy(() -> Middleware.compose(null)).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class WithTiming {

        @Test
        void countsTheCallsAndAddsUpTheTime() throws Exception {
            Middleware.Timing record = new Middleware.Timing();
            Handler handler = Middleware.compose(call -> 1, List.of(Middleware.withTiming(record)));
            handler.handle(Call.empty());
            handler.handle(Call.empty());
            assertThat(record.calls()).isEqualTo(2);
            assertThat(record.milliseconds()).isGreaterThanOrEqualTo(0);
        }

        @Test
        void countsACallThatThrew() {
            Middleware.Timing record = new Middleware.Timing();
            Handler handler = Middleware.compose(gone(), List.of(Middleware.withTiming(record)));
            assertThatThrownBy(() -> handler.handle(Call.empty())).isInstanceOf(NotFoundException.class);
            assertThat(record.calls()).isEqualTo(1);
        }
    }

    @Nested
    class RequireBodyAndRequireFields {

        @Test
        void refusesARequestWithNoBody() throws Exception {
            Handler handler = Middleware.compose(call -> 1, List.of(Middleware.requireBody()));
            assertThatThrownBy(() -> handler.handle(Call.withBody(null))).hasMessageContaining("needs a body");
            assertThat(handler.handle(Call.withBody(Json.obj()))).isEqualTo(1);
        }

        @Test
        void namesTheFieldsABodyIsMissing() {
            Handler handler =
                    Middleware.compose(call -> 1, List.of(Middleware.requireFields(List.of("name", "shafts"))));
            InvalidArgumentException err = catchThrowableOfType(
                    InvalidArgumentException.class, () -> handler.handle(Call.withBody(Json.obj("name", "twill"))));
            assertThat(err).isNotNull();
            assertThat(err.detail().get("missing")).isEqualTo(Json.arr("shafts"));
        }

        @Test
        void letsACompleteBodyThrough() throws Exception {
            Handler handler = Middleware.compose(call -> 1, List.of(Middleware.requireFields(List.of("name"))));
            assertThat(handler.handle(Call.withBody(Json.obj("name", "twill")))).isEqualTo(1);
        }

        @Test
        void refusesAWrapperDeclaredWithNoFields() {
            assertThatThrownBy(() -> Middleware.requireFields(List.of())).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class ReadPaging {

        @Test
        void fillsInTheDefaults() {
            assertThat(Middleware.readPaging(Map.of())).isEqualTo(new Paging(50, 0));
        }

        @Test
        void readsTheQueryAndFloorsItToWholeRows() {
            assertThat(Middleware.readPaging(Map.of("limit", "10.7", "offset", "5.2")))
                    .isEqualTo(new Paging(10, 5));
        }

        @Test
        void refusesALimitPastTheCapAndANegativeOffset() {
            assertThatThrownBy(() -> Middleware.readPaging(Map.of("limit", "10000")))
                    .isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Middleware.readPaging(Map.of("offset", "-1")))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class WithPaging {

        @Test
        void cutsAListToThePageAskedFor() throws Exception {
            Handler handler = Middleware.compose(call -> ROWS, List.of(Middleware.withPaging()));
            assertThat(handler.handle(Call.withQuery(Map.of("limit", "3", "offset", "2"))))
                    .isEqualTo(new Page(List.of(3, 4, 5), 10, 2, 3));
        }

        @Test
        void leavesSomethingThatIsNotAListAlone() throws Exception {
            Handler handler = Middleware.compose(call -> Json.obj("ok", true), List.of(Middleware.withPaging()));
            assertThat(handler.handle(Call.withQuery(Map.of()))).isEqualTo(Json.obj("ok", true));
        }

        @Test
        void reportsTheWholeTotalEvenWhenThePageIsShort() throws Exception {
            Handler handler = Middleware.compose(call -> ROWS, List.of(Middleware.withPaging()));
            Page result = (Page) handler.handle(Call.withQuery(Map.of("offset", "8")));
            assertThat(result.items()).hasSize(2);
            assertThat(result.total()).isEqualTo(10);
        }
    }

    @Nested
    class WithStamp {

        @Test
        void addsAFieldToAnObjectBody() throws Exception {
            Handler handler =
                    Middleware.compose(call -> Json.obj("ok", true), List.of(Middleware.withStamp("version", "1.0")));
            assertThat(handler.handle(Call.empty())).isEqualTo(Json.obj("ok", true, "version", "1.0"));
        }

        @Test
        void leavesAListAndABareValueAlone() throws Exception {
            assertThat(Middleware.compose(call -> List.of(1), List.of(Middleware.withStamp("v", 1)))
                            .handle(Call.empty()))
                    .isEqualTo(List.of(1));
            assertThat(Middleware.compose(call -> "text", List.of(Middleware.withStamp("v", 1)))
                            .handle(Call.empty()))
                    .isEqualTo("text");
            assertThat(Middleware.compose(call -> null, List.of(Middleware.withStamp("v", 1)))
                            .handle(Call.empty()))
                    .isNull();
        }
    }

    @Nested
    class WithErrorFloor {

        @Test
        void letsOneOfOurErrorsThroughUntouched() {
            Handler handler = Middleware.compose(gone(), List.of(Middleware.withErrorFloor()));
            assertThatThrownBy(() -> handler.handle(Call.empty())).isInstanceOf(NotFoundException.class);
        }

        @Test
        void turnsAnythingElseIntoAnInvalidArgument() {
            Handler handler = Middleware.compose(
                    call -> {
                        throw new IllegalStateException("no handler for that");
                    },
                    List.of(Middleware.withErrorFloor()));
            assertThatThrownBy(() -> handler.handle(Call.empty())).isInstanceOf(InvalidArgumentException.class);
        }
    }
}
