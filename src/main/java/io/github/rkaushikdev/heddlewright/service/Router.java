package io.github.rkaushikdev.heddlewright.service;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.HeddleException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * A small router.
 *
 * <p>Routes are declared as a method and a path with {@code :name} segments. Matching is exact on
 * segment count, so {@code /drafts/:id} never swallows {@code /drafts/:id/cloth}, and the first route
 * declared wins, which makes the table readable top to bottom rather than by specificity rules
 * nobody can hold in their head.
 *
 * <p>A request is a value and a response is a value, so the whole service runs without a socket.
 */
public final class Router {

    /** The methods a route may be declared for. */
    public static final List<String> METHODS = List.of("GET", "POST", "PUT", "DELETE");

    /** What a handler is given: the captured path parameters, the query, and the body or null. */
    public record Call(Map<String, String> params, Map<String, String> query, Json body) {
        /** Copies the maps. */
        public Call {
            params = Map.copyOf(params);
            query = Map.copyOf(query);
            body = body == null ? Json.NULL : body;
        }

        /** A call with no parameters, no query and no body. */
        public static Call empty() {
            return new Call(Map.of(), Map.of(), Json.NULL);
        }

        /** A call carrying only a body. */
        public static Call withBody(Object body) {
            return new Call(Map.of(), Map.of(), Json.of(body));
        }

        /** A call carrying only a query. */
        public static Call withQuery(Map<String, String> query) {
            return new Call(Map.of(), query, Json.NULL);
        }
    }

    /**
     * What a route does with a call. It returns the body of a two hundred, a {@link Response} to set
     * its own status, null for a two hundred and four, or a {@link CompletionStage} of any of those.
     */
    @FunctionalInterface
    public interface Handler {
        /** Answer a call. */
        Object handle(Call call) throws Exception;
    }

    /** A request as the router takes it. A null method is GET and a null query is empty. */
    public record Request(String method, String path, Map<String, String> query, Json body) {
        /** Fills in the empty query and the null body. */
        public Request {
            query = query == null ? Map.of() : Map.copyOf(query);
            body = body == null ? Json.NULL : body;
        }

        /** A GET for a path. */
        public static Request get(String path) {
            return new Request("GET", path, Map.of(), Json.NULL);
        }

        /** A GET for a path with a query. */
        public static Request get(String path, Map<String, String> query) {
            return new Request("GET", path, query, Json.NULL);
        }

        /** A request with no body. */
        public static Request of(String method, String path) {
            return new Request(method, path, Map.of(), Json.NULL);
        }

        /** A request with a body, which is read as JSON. */
        public static Request of(String method, String path, Object body) {
            return new Request(method, path, Map.of(), Json.of(body));
        }
    }

    /** A status and a body; a null body is {@link Json#NULL}. */
    public record Response(int status, Json body) {
        /** Fills in the null body. */
        public Response {
            body = body == null ? Json.NULL : body;
        }

        /** A response whose body is any value, read as JSON. */
        public static Response of(int status, Object body) {
            return new Response(status, Json.of(body));
        }
    }

    /** One declared route. */
    public record Route(String method, String path, List<String> segments, Handler handler) {
        /** Copies the segments. */
        public Route {
            segments = List.copyOf(segments);
        }
    }

    /** The route a request matched and the parameters it captured. */
    public record Match(Route route, Map<String, String> params) {
        /** Copies the parameters. */
        public Match {
            params = Map.copyOf(params);
        }
    }

    private static final Map<String, Integer> STATUS_BY_CODE = Map.of(
            "invalid_argument", 400,
            "draft_error", 422,
            "capacity_error", 409,
            "conflict", 409,
            "not_found", 404);

    private final List<Route> routes = new ArrayList<>();

    private Router() {}

    /** Build an empty routing table. */
    public static Router createRouter() {
        return new Router();
    }

    /** The declared routes, in declaration order. */
    public List<Route> routes() {
        return List.copyOf(routes);
    }

    /** Add a route. */
    public static Router route(Router router, String method, String path, Handler handler) {
        Check.oneOf(method, "method", METHODS);
        Check.nonEmptyString(path, "path");
        if (handler == null) {
            throw new InvalidArgumentException("a route needs a handler function", Json.obj("path", path));
        }
        if (!path.startsWith("/")) {
            throw new InvalidArgumentException("a route path starts with a slash", Json.obj("path", path));
        }
        router.routes.add(new Route(method, path, splitPath(path), handler));
        return router;
    }

    private static List<String> splitPath(String path) {
        return Text.split(path, "/").stream().filter(part -> !part.isEmpty()).toList();
    }

    /** Find the route for a method and path, with the parameters it captured. */
    public static Optional<Match> match(Router router, String method, String path) {
        List<String> wanted = splitPath(path);
        for (Route entry : router.routes) {
            if (!entry.method().equals(method) || entry.segments().size() != wanted.size()) {
                continue;
            }
            Map<String, String> params = new LinkedHashMap<>();
            boolean matched = true;
            for (int index = 0; index < entry.segments().size() && matched; index += 1) {
                String segment = entry.segments().get(index);
                if (segment.startsWith(":")) {
                    params.put(segment.substring(1), decodeComponent(wanted.get(index)));
                } else if (!segment.equals(wanted.get(index))) {
                    matched = false;
                }
            }
            if (matched) {
                return Optional.of(new Match(entry, params));
            }
        }
        return Optional.empty();
    }

    /**
     * Decode a percent-escaped URI component as UTF-8. A plus stays a plus, and a broken escape is
     * refused with "URI malformed".
     */
    static String decodeComponent(String text) {
        if (text.indexOf('%') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c != '%') {
                out.append(c);
                i += 1;
                continue;
            }
            bytes.reset();
            while (i < text.length() && text.charAt(i) == '%') {
                if (i + 2 >= text.length()) {
                    throw new IllegalArgumentException("URI malformed");
                }
                int high = Character.digit(text.charAt(i + 1), 16);
                int low = Character.digit(text.charAt(i + 2), 16);
                if (high < 0 || low < 0) {
                    throw new IllegalArgumentException("URI malformed");
                }
                bytes.write(high * 16 + low);
                i += 3;
            }
            out.append(utf8(bytes.toByteArray()));
        }
        return out.toString();
    }

    private static String utf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
        } catch (java.nio.charset.CharacterCodingException err) {
            throw new IllegalArgumentException("URI malformed", err);
        }
    }

    /**
     * The methods that would have matched this path, which is how a four hundred and five is told
     * apart from a four hundred and four. A path nobody declared is missing; a path declared for
     * another method is a wrong method, and the two want different things from the caller.
     */
    public static List<String> methodsFor(Router router, String path) {
        List<String> wanted = splitPath(path);
        TreeSet<String> found = new TreeSet<>();
        for (Route entry : router.routes) {
            if (entry.segments().size() != wanted.size()) {
                continue;
            }
            boolean fits = true;
            for (int index = 0; index < wanted.size() && fits; index += 1) {
                String segment = entry.segments().get(index);
                fits = segment.startsWith(":") || segment.equals(wanted.get(index));
            }
            if (fits) {
                found.add(entry.method());
            }
        }
        return List.copyOf(found);
    }

    /** The paths a method can reach, sorted, for a not-found body worth reading. */
    public static List<String> pathsFor(Router router, String method) {
        return router.routes.stream()
                .filter(entry -> entry.method().equals(method))
                .map(Route::path)
                .sorted()
                .toList();
    }

    /**
     * Run a request through the table. Anything a handler throws that carries one of our codes
     * becomes the matching status rather than a five hundred.
     */
    public static Response handle(Router router, Request request) {
        Check.object(request, "request");
        String method = Check.oneOf(request.method() == null ? "GET" : request.method(), "request.method", METHODS);
        String path = Check.nonEmptyString(request.path(), "request.path");
        Optional<Match> found = match(router, method, path);
        if (found.isEmpty()) {
            List<String> allowed = methodsFor(router, path);
            if (!allowed.isEmpty()) {
                return new Response(
                        405, Json.obj("error", "wrong method", "method", method, "path", path, "allowed", allowed));
            }
            return new Response(
                    404,
                    Json.obj("error", "no route", "method", method, "path", path, "known", pathsFor(router, method)));
        }
        try {
            Object result = found.get()
                    .route()
                    .handler()
                    .handle(new Call(found.get().params(), request.query(), request.body()));
            return normalise(settle(result));
        } catch (Exception err) {
            return errorResponse(err);
        }
    }

    /** Wait for a result that is still on its way, handing back what it settled to. */
    static Object settle(Object result) throws Exception {
        if (!(result instanceof CompletionStage<?> stage)) {
            return result;
        }
        try {
            return stage.toCompletableFuture().join();
        } catch (CompletionException err) {
            if (err.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw err;
        }
    }

    private static Response normalise(Object result) {
        if (result == null || result instanceof Json json && json.isNullish()) {
            return new Response(204, Json.NULL);
        }
        if (result instanceof Response response) {
            return response;
        }
        return new Response(200, Json.of(result));
    }

    /** Turn an error into a response, keeping its code and detail. */
    public static Response errorResponse(Throwable err) {
        if (err instanceof NotFoundException found) {
            return new Response(
                    404, Json.obj("error", found.getMessage(), "code", found.code(), "detail", found.detail()));
        }
        Integer status = err instanceof HeddleException ours ? STATUS_BY_CODE.get(ours.code()) : null;
        if (status == null) {
            String message = err.getMessage() == null ? String.valueOf(err) : err.getMessage();
            return new Response(500, Json.obj("error", "unhandled", "message", message));
        }
        HeddleException ours = (HeddleException) err;
        return new Response(status, Json.obj("error", ours.getMessage(), "code", ours.code(), "detail", ours.detail()));
    }

    /** Every route in the table, for a service description page. */
    public static List<String> describeRoutes(Router router) {
        return router.routes.stream()
                .map(entry -> entry.method() + " " + entry.path())
                .toList();
    }
}
