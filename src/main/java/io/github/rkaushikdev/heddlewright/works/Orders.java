package io.github.rkaushikdev.heddlewright.works;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Collation;
import io.github.rkaushikdev.heddlewright.util.ConflictException;
import io.github.rkaushikdev.heddlewright.util.Group;
import io.github.rkaushikdev.heddlewright.util.Ids;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Orders and the states they move through.
 *
 * <p>An order is a length of a named style for a named customer, wanted by a date. It moves forward
 * through a small set of states and never backwards, because a shed that can quietly un-weave an
 * order loses the ability to say what it has actually made.
 */
public final class Orders {

    /** Where an order stands. The running states are in order; cancelled stands apart from them. */
    public enum State {
        /** Priced for the customer. */
        QUOTED,
        /** Accepted. */
        CONFIRMED,
        /** The warp is made. */
        WARPED,
        /** On the loom. */
        WEAVING,
        /** Off the loom. */
        FINISHED,
        /** Sent out. */
        DESPATCHED,
        /** Called off. */
        CANCELLED;

        /** Read a state by its name, refusing anything else. */
        public static State parse(Object text, String field) {
            return Check.member(text, field, values());
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The running states, quoted to despatched. */
    public static final List<State> ORDER_STATES =
            List.of(State.QUOTED, State.CONFIRMED, State.WARPED, State.WEAVING, State.FINISHED, State.DESPATCHED);

    /** The state an order leaves the running states for. */
    public static final State CANCELLED = State.CANCELLED;

    /**
     * An order. The value is the length at the price, and the id is a content id of everything
     * else, so an order that moves on is a new record. A date that is not text is kept as its text.
     *
     * @param reference the order number
     * @param customer who it is for
     * @param style the cloth
     * @param metres the length ordered
     * @param widthCm the width ordered
     * @param pricePerMetre the agreed price
     * @param wantedBy the day it is wanted, if one was given
     * @param state where it stands
     * @param value the length at the price
     * @param id the content id
     */
    public record Order(
            String reference,
            String customer,
            String style,
            double metres,
            double widthCm,
            double pricePerMetre,
            Optional<String> wantedBy,
            State state,
            double value,
            String id)
            implements Json.Writable {

        private JsonObject content() {
            return Json.obj(
                    "reference", reference,
                    "customer", customer,
                    "style", style,
                    "metres", metres,
                    "widthCm", widthCm,
                    "pricePerMetre", pricePerMetre,
                    "wantedBy", wantedBy.<Object>map(day -> day).orElse(Json.NULL),
                    "state", state,
                    "value", value);
        }

        /** The order as it is stored and sent, with a null date when none was given. */
        @Override
        public Json toJson() {
            return content().with("id", id);
        }
    }

    /** One state's line in the order book. */
    public record StateTotal(State state, int orders, double metres, double value) {}

    /** The order book: how many orders, the open metres, and the totals by state. */
    public record Book(int orders, double openMetres, List<StateTotal> byState) {
        /** Copies the totals. */
        public Book {
            byState = List.copyOf(byState);
        }
    }

    private Orders() {}

    /** Open an order from its record, in the quoted state unless the record says otherwise. */
    public static Order createOrder(Object input) {
        JsonObject record = Check.plainObject(input, "order");
        String reference = Check.nonEmptyString(record.get("reference"), "reference");
        String customer = Check.nonEmptyString(record.get("customer"), "customer");
        String style = Check.nonEmptyString(record.get("style"), "style");
        double metres = Check.positiveNumber(record.get("metres"), "metres");
        double widthCm = Check.positiveNumber(record.get("widthCm"), "widthCm");
        double pricePerMetre = Check.positiveNumber(record.get("pricePerMetre"), "pricePerMetre");
        Json wanted = record.get("wantedBy");
        Optional<String> wantedBy = wanted.isNullish() ? Optional.empty() : Optional.of(Text.string(wanted));
        Json given = record.get("state");
        State state = State.parse(given.isNullish() ? Json.of("quoted") : given, "state");
        double value = Numeric.roundTo(metres * pricePerMetre, 2);
        Order draft = new Order(reference, customer, style, metres, widthCm, pricePerMetre, wantedBy, state, value, "");
        return new Order(
                reference,
                customer,
                style,
                metres,
                widthCm,
                pricePerMetre,
                wantedBy,
                state,
                value,
                Ids.contentId("order", draft.content()));
    }

    /** The state an order moves to next, or nothing when it is despatched or cancelled. */
    public static Optional<State> nextState(Object state) {
        State at = State.parse(state, "state");
        if (at == State.CANCELLED) {
            return Optional.empty();
        }
        int index = ORDER_STATES.indexOf(at);
        return index + 1 < ORDER_STATES.size() ? Optional.of(ORDER_STATES.get(index + 1)) : Optional.empty();
    }

    /** Move an order on one step. */
    public static Order advanceOrder(Order order) {
        return advanceOrder(order, null);
    }

    /**
     * Move an order on to the state named, or one step when none is named. It never goes backwards,
     * and an order can be cancelled from any running state.
     */
    public static Order advanceOrder(Order order, Object to) {
        boolean named = to != null && !(to instanceof Json json && json.isNullish());
        Optional<State> wanted = named ? Optional.of(State.parse(to, "to")) : nextState(order.state());
        if (wanted.isEmpty()) {
            throw new ConflictException(
                    order.reference() + " is already " + order.state(),
                    Json.obj("reference", order.reference(), "state", order.state()));
        }
        State next = wanted.get();
        if (next != State.CANCELLED && stateIndex(next) <= stateIndex(order.state())) {
            throw new ConflictException(
                    "an order cannot go backwards",
                    Json.obj("reference", order.reference(), "from", order.state(), "to", next));
        }
        return createOrder(((JsonObject) Json.of(order)).with("state", next));
    }

    /** Cancel an order, which is allowed until it has been despatched. */
    public static Order cancelOrder(Order order) {
        if (order.state() == State.DESPATCHED) {
            throw new ConflictException(
                    "a despatched order cannot be cancelled", Json.obj("reference", order.reference()));
        }
        return createOrder(((JsonObject) Json.of(order)).with("state", State.CANCELLED));
    }

    private static int stateIndex(State state) {
        int at = ORDER_STATES.indexOf(state);
        if (at == -1) {
            throw new InvalidArgumentException(state + " is not a running state", Json.obj("state", state));
        }
        return at;
    }

    /** True when the order still has work to do on the floor. */
    public static boolean isOpen(Order order) {
        return order.state() != State.DESPATCHED && order.state() != State.CANCELLED;
    }

    /** The orders still open, in wanted-by order with the undated last, then by reference. */
    public static List<Order> openOrders(List<Order> orders) {
        Comparator<Order> byDate = (a, b) -> {
            if (a.wantedBy().equals(b.wantedBy())) {
                return Collation.compare(a.reference(), b.reference());
            }
            if (a.wantedBy().isEmpty()) {
                return 1;
            }
            if (b.wantedBy().isEmpty()) {
                return -1;
            }
            return Collation.compare(a.wantedBy().get(), b.wantedBy().get());
        };
        return orders.stream().filter(Orders::isOpen).sorted(byDate).toList();
    }

    /** The order book, totalled by state, every state listed even at nothing. */
    public static Book orderBook(List<Order> orders) {
        List<State> states = List.of(State.values());
        List<StateTotal> byState = Group.tallyOver(
                        states,
                        orders,
                        Group.<Order>by(Order::state)
                                .label("state")
                                .field("metres", Order::metres)
                                .field("value", Order::value))
                .stream()
                .map(row -> new StateTotal(
                        State.parse(row.get("state"), "state"),
                        row.get("count").asInt(),
                        row.get("metres").asDouble(),
                        Numeric.roundTo(row.get("value").asDouble(), 2)))
                .toList();
        double open = Numeric.sumBy(orders.stream().filter(Orders::isOpen).toList(), Order::metres, "orders");
        return new Book(orders.size(), Numeric.roundTo(open, 3), byState);
    }

    /** Every order for one style, whatever state it is in. */
    public static List<Order> ordersForStyle(List<Order> orders, String style) {
        Check.nonEmptyString(style, "style");
        return orders.stream().filter(order -> order.style().equals(style)).toList();
    }
}
