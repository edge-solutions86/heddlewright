package io.github.rkaushikdev.heddlewright.works;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.heddlewright.util.ConflictException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.works.Orders.Order;
import io.github.rkaushikdev.heddlewright.works.Orders.State;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrdersTest {

    private static final JsonObject RECORD = Json.obj(
            "reference", "SO-1001",
            "customer", "Ravensward",
            "style", "gabardine",
            "metres", 500,
            "widthCm", 150,
            "pricePerMetre", 6.4,
            "wantedBy", "2025-09-01");

    private static final Order ORDER = Orders.createOrder(RECORD);

    private static Order with(Object... fields) {
        return Orders.createOrder(RECORD.with(fields));
    }

    @Nested
    class CreateOrder {

        @Test
        void pricesTheOrderAndFreezesIt() {
            assertThat(ORDER.value()).isEqualTo(3200);
            assertThat(ORDER.state()).isEqualTo(State.QUOTED);
            // A record has no setters, so nothing can change an order once it is opened.
            assertThat(Order.class.isRecord()).isTrue();
        }

        @Test
        void givesItAContentId() {
            assertThat(ORDER.id()).matches("order-[0-9a-f]{8}");
        }

        @Test
        void refusesAnOrderWithNoLengthOrNoCustomer() {
            assertThatThrownBy(() -> with("metres", 0)).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> with("customer", "")).isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void refusesAStateItDoesNotKnow() {
            assertThatThrownBy(() -> with("state", "lost")).isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class NextState {

        @Test
        void walksTheStatesInOrder() {
            assertThat(Orders.nextState("quoted")).contains(State.CONFIRMED);
            assertThat(Orders.nextState("weaving")).contains(State.FINISHED);
            assertThat(Orders.nextState("despatched")).isEmpty();
            assertThat(Orders.nextState(Orders.CANCELLED)).isEmpty();
        }

        @Test
        void namesEveryStateItMovesThrough() {
            assertThat(Orders.ORDER_STATES)
                    .extracting(Object::toString)
                    .containsExactly("quoted", "confirmed", "warped", "weaving", "finished", "despatched");
        }
    }

    @Nested
    class AdvanceOrder {

        @Test
        void movesAnOrderOnOneStep() {
            assertThat(Orders.advanceOrder(ORDER).state()).isEqualTo(State.CONFIRMED);
        }

        @Test
        void jumpsToALaterStateWhenToldTo() {
            assertThat(Orders.advanceOrder(ORDER, "weaving").state()).isEqualTo(State.WEAVING);
        }

        @Test
        void refusesToGoBackwards() {
            Order weaving = Orders.advanceOrder(ORDER, "weaving");
            assertThatThrownBy(() -> Orders.advanceOrder(weaving, "confirmed"))
                    .hasMessageContaining("cannot go backwards");
            assertThatThrownBy(() -> Orders.advanceOrder(weaving, "weaving")).isInstanceOf(ConflictException.class);
        }

        @Test
        void refusesToMoveADespatchedOrder() {
            Order done = Orders.advanceOrder(ORDER, "despatched");
            assertThatThrownBy(() -> Orders.advanceOrder(done)).hasMessageContaining("already despatched");
        }

        @Test
        void givesTheMovedOrderANewId() {
            assertThat(Orders.advanceOrder(ORDER).id()).isNotEqualTo(ORDER.id());
        }

        @Test
        void walksTheWholeRunThroughToDespatch() {
            Order order = ORDER;
            for (State state : Orders.ORDER_STATES.subList(1, Orders.ORDER_STATES.size())) {
                order = Orders.advanceOrder(order);
                assertThat(order.state()).isEqualTo(state);
            }
            assertThat(order.state()).isEqualTo(State.DESPATCHED);
        }
    }

    @Nested
    class CancelOrder {

        @Test
        void cancelsAnOpenOrder() {
            assertThat(Orders.cancelOrder(ORDER).state()).isEqualTo(Orders.CANCELLED);
            assertThat(Orders.cancelOrder(Orders.advanceOrder(ORDER, "weaving")).state())
                    .isEqualTo(Orders.CANCELLED);
        }

        @Test
        void refusesToCancelOneAlreadyDespatched() {
            Order done = Orders.advanceOrder(ORDER, "despatched");
            assertThatThrownBy(() -> Orders.cancelOrder(done)).isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class IsOpenAndOpenOrders {

        private final Order other = with("reference", "SO-1002", "wantedBy", "2025-08-01");
        private final Order undated = with("reference", "SO-1003", "wantedBy", Json.NULL);
        private final Order done = Orders.advanceOrder(ORDER, "despatched");

        @Test
        void countsAnythingNotDespatchedOrCancelledAsOpen() {
            assertThat(Orders.isOpen(ORDER)).isTrue();
            assertThat(Orders.isOpen(done)).isFalse();
            assertThat(Orders.isOpen(Orders.cancelOrder(ORDER))).isFalse();
        }

        @Test
        void sortsOpenOrdersByWantedDateUndatedLast() {
            List<Order> sorted = Orders.openOrders(new ArrayList<>(List.of(undated, ORDER, other, done)));
            assertThat(sorted).extracting(Order::reference).containsExactly("SO-1002", "SO-1001", "SO-1003");
        }
    }

    @Nested
    class OrderBook {

        @Test
        void totalsByStateListingEveryStateEvenAtZero() {
            Orders.Book book = Orders.orderBook(List.of(ORDER, Orders.advanceOrder(ORDER, "weaving")));
            assertThat(book.orders()).isEqualTo(2);
            List<State> every = new ArrayList<>(Orders.ORDER_STATES);
            every.add(Orders.CANCELLED);
            assertThat(book.byState()).extracting(Orders.StateTotal::state).isEqualTo(every);
            assertThat(book.byState().stream()
                            .filter(entry -> entry.state() == State.WEAVING)
                            .findFirst()
                            .orElseThrow()
                            .metres())
                    .isEqualTo(500);
        }

        @Test
        void countsOnlyOpenOrdersInTheOpenMetres() {
            Orders.Book book = Orders.orderBook(List.of(ORDER, Orders.advanceOrder(ORDER, "despatched")));
            assertThat(book.openMetres()).isEqualTo(500);
        }
    }

    @Nested
    class OrdersForStyle {

        @Test
        void picksOutOneStyle() {
            Order other = with("reference", "SO-2", "style", "poplin");
            assertThat(Orders.ordersForStyle(List.of(ORDER, other), "poplin")).hasSize(1);
            assertThat(Orders.ordersForStyle(List.of(ORDER, other), "nothing")).isEmpty();
        }
    }
}
