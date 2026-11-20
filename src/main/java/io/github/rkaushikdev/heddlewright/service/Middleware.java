package io.github.rkaushikdev.heddlewright.service;

import io.github.rkaushikdev.heddlewright.service.Router.Handler;
import io.github.rkaushikdev.heddlewright.service.Router.Response;
import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.HeddleException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wrapping a request.
 *
 * <p>Everything here takes a handler and returns a handler, so the router never has to know about
 * any of it. They compose left to right, which is the order they run in, because a list that reads
 * in the opposite order to the thing it describes is a list nobody gets right.
 */
public final class Middleware {

    /** Something that wraps a handler in another. */
    @FunctionalInterface
    public interface Wrapper {
        /** The handler that runs around {@code next}. */
        Handler wrap(Handler next);
    }

    /** A running count of calls and the time they took, which {@link #withTiming} adds to. */
    public static final class Timing {
        private int calls;
        private double milliseconds;

        /** How many calls have finished, thrown or not. */
        public int calls() {
            return calls;
        }

        /** The time those calls took, in milliseconds. */
        public double milliseconds() {
            return milliseconds;
        }
    }

    /** The paging a query asks for. */
    public record Paging(int limit, int offset) {}

    /** One page of a list, with the size of the whole list. */
    public record Page(List<?> items, int total, int offset, int limit) {
        /** Copies the items. */
        public Page {
            items = List.copyOf(items);
        }
    }

    /** The page size when a query names none. */
    public static final int DEFAULT_LIMIT = 50;

    /** The largest page a query may ask for. */
    public static final int MAX_LIMIT = 500;

    private Middleware() {}

    /** Wrap a handler in a list of wrappers, the first running outermost. */
    public static Handler compose(Handler handler, List<Wrapper> wrappers) {
        if (handler == null) {
            throw new InvalidArgumentException("compose needs a handler function", JsonObject.EMPTY);
        }
        Handler next = handler;
        for (int index = wrappers.size() - 1; index >= 0; index -= 1) {
            next = wrappers.get(index).wrap(next);
        }
        return next;
    }

    /** A handler with no wrappers, which is the handler itself. */
    public static Handler compose(Handler handler) {
        return compose(handler, List.of());
    }

    /** Count how many times a handler was called and how long it took. */
    public static Wrapper withTiming(Timing record) {
        Check.object(record, "record");
        return handler -> call -> {
            long started = System.nanoTime();
            try {
                return Router.settle(handler.handle(call));
            } finally {
                record.calls += 1;
                record.milliseconds += (System.nanoTime() - started) / 1e6;
            }
        };
    }

    private static boolean absent(Json body) {
        return body == null || body.isNullish();
    }

    /** Refuse a request whose body is missing when the route needs one. */
    public static Wrapper requireBody() {
        return handler -> call -> {
            if (absent(call.body())) {
                throw new InvalidArgumentException("this route needs a body", JsonObject.EMPTY);
            }
            return handler.handle(call);
        };
    }

    /** Refuse a body missing any of the fields a route needs. */
    public static Wrapper requireFields(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new InvalidArgumentException("requireFields needs at least one field", JsonObject.EMPTY);
        }
        List<String> needed = List.copyOf(fields);
        return handler -> call -> {
            JsonObject body = Check.plainObject(call.body(), "body");
            List<String> missing =
                    needed.stream().filter(field -> body.get(field).isMissing()).toList();
            if (!missing.isEmpty()) {
                throw new InvalidArgumentException(
                        "the body is missing " + String.join(", ", missing),
                        Json.obj("missing", missing, "fields", needed));
            }
            return handler.handle(call);
        };
    }

    /** Page a list result fifty rows at a time, up to five hundred. */
    public static Wrapper withPaging() {
        return withPaging(DEFAULT_LIMIT, MAX_LIMIT);
    }

    /**
     * Read paging out of the query and apply it to whatever the handler returns, when that is a list.
     * A handler that returns something else is left alone.
     */
    public static Wrapper withPaging(int defaultLimit, int maxLimit) {
        Check.integer(defaultLimit, "defaultLimit", Bounds.atLeast(1));
        Check.integer(maxLimit, "maxLimit", Bounds.atLeast(1));
        return handler -> call -> {
            Paging page = readPaging(call.query(), defaultLimit, maxLimit);
            Object result = Router.settle(handler.handle(call));
            List<?> rows =
                    result instanceof List<?> list ? list : result instanceof JsonArray array ? array.items() : null;
            if (rows == null) {
                return result;
            }
            int from = Math.min(page.offset(), rows.size());
            int to = (int) Math.min((long) page.offset() + page.limit(), rows.size());
            return new Page(
                    new ArrayList<>(rows.subList(from, Math.max(from, to))), rows.size(), page.offset(), page.limit());
        };
    }

    /** The paging a query asks for, with the usual limits. */
    public static Paging readPaging(Map<String, String> query) {
        return readPaging(query, DEFAULT_LIMIT, MAX_LIMIT);
    }

    /** The paging a query asks for, read as numbers, checked against the limits and floored to whole rows. */
    public static Paging readPaging(Map<String, String> query, int defaultLimit, int maxLimit) {
        String limitText = query.get("limit");
        String offsetText = query.get("offset");
        double limit = limitText == null ? defaultLimit : Text.toNumber(limitText);
        double offset = offsetText == null ? 0 : Text.toNumber(offsetText);
        Check.number(limit, "limit", Bounds.between(1, maxLimit));
        Check.number(offset, "offset", Bounds.atLeast(0));
        return new Paging((int) Math.floor(limit), (int) Math.floor(offset));
    }

    /** Put a fixed field into every object body a handler returns, leaving lists and bare values alone. */
    public static Wrapper withStamp(String field, Object value) {
        Check.nonEmptyString(field, "field");
        return handler -> call -> {
            Object result = Router.settle(handler.handle(call));
            if (result == null || result instanceof List<?> || result instanceof Response) {
                return result;
            }
            if (Json.of(result) instanceof JsonObject object) {
                return object.with(field, value);
            }
            return result;
        };
    }

    /** Turn anything a handler throws that is not ours into an invalid argument. */
    public static Wrapper withErrorFloor() {
        return handler -> call -> {
            try {
                return Router.settle(handler.handle(call));
            } catch (HeddleException err) {
                throw err;
            } catch (Exception err) {
                throw new InvalidArgumentException(
                        err.getMessage() == null ? "the request could not be handled" : err.getMessage(),
                        JsonObject.EMPTY);
            }
        };
    }
}
