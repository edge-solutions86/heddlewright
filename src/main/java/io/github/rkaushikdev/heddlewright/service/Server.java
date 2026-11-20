package io.github.rkaushikdev.heddlewright.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.rkaushikdev.heddlewright.service.Router.Request;
import io.github.rkaushikdev.heddlewright.service.Router.Response;
import io.github.rkaushikdev.heddlewright.util.HeddleException;
import io.github.rkaushikdev.heddlewright.util.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The HTTP adapter, over the JDK's built-in server.
 *
 * <p>Everything above this file is pure: a request is a value and a response is a value, which is
 * why the whole service can be tested without a socket. This is the only place that knows about
 * streams, headers and ports, and it is deliberately thin so there is nothing here worth testing
 * twice. Requests are answered one at a time on the server's own thread, so the journal is never
 * written from two places at once.
 */
public final class Server {

    /** The largest body a request may carry, one mebibyte. */
    public static final int MAX_BODY_BYTES = 1024 * 1024;

    /** A path and its decoded query. */
    public record Url(String path, Map<String, String> query) {
        /** Copies the query, keeping its order. */
        public Url {
            query = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(query));
        }
    }

    private final App app;
    private HttpServer http;

    private Server(App app) {
        this.app = app;
    }

    /** The app this server answers for. */
    public App app() {
        return app;
    }

    /** Build a server over a new app, not yet listening. */
    public static Server createServer() {
        return createServer(App.createApp());
    }

    /** Build a server over an app, not yet listening. */
    public static Server createServer(App app) {
        return new Server(app);
    }

    /** Start listening on any free port, and return the port bound. */
    public static int listen(Server server) throws IOException {
        return listen(server, 0);
    }

    /** Start listening on a port, nought for any free one, and return the port bound. */
    public static int listen(Server server, int port) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
        http.createContext("/", exchange -> {
            try (exchange) {
                answer(server.app, exchange);
            }
        });
        http.start();
        server.http = http;
        return http.getAddress().getPort();
    }

    /** Stop listening and let go of the port. */
    public static void close(Server server) {
        if (server.http != null) {
            server.http.stop(0);
            server.http = null;
        }
    }

    /** Read a request body, refusing anything larger than the cap. */
    public static String readBody(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int size = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            size += read;
            if (size > limit) {
                throw new IOException("request body is too large");
            }
            out.write(buffer, 0, read);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /** Split a url into its path and its query, with no dependency on a base url. */
    public static Url splitUrl(String url) {
        int at = url.indexOf('?');
        if (at == -1) {
            return new Url(url, Map.of());
        }
        Map<String, String> query = new LinkedHashMap<>();
        for (String pair : url.substring(at + 1).split("&", -1)) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String key = equals == -1 ? pair : pair.substring(0, equals);
            String value = equals == -1 ? "" : pair.substring(equals + 1);
            query.put(Router.decodeComponent(key), Router.decodeComponent(value.replace('+', ' ')));
        }
        return new Url(url.substring(0, at), query);
    }

    private static void answer(App app, HttpExchange exchange) throws IOException {
        try {
            Url url = splitUrl(exchange.getRequestURI().getRawPath()
                    + (exchange.getRequestURI().getRawQuery() == null
                            ? ""
                            : "?" + exchange.getRequestURI().getRawQuery()));
            String raw = readBody(exchange.getRequestBody(), MAX_BODY_BYTES);
            Json body = Json.NULL;
            if (!raw.isEmpty()) {
                try {
                    body = Json.parseJson(raw, "body");
                } catch (HeddleException err) {
                    send(exchange, 400, Json.stringify(Json.obj("error", err.getMessage(), "code", err.code())));
                    return;
                }
            }
            Response result = app.request(new Request(exchange.getRequestMethod(), url.path(), url.query(), body));
            send(exchange, result.status(), result.body().isNullish() ? "" : Json.stringify(result.body()));
        } catch (IOException | RuntimeException err) {
            String message = err.getMessage() == null ? String.valueOf(err) : err.getMessage();
            send(exchange, 500, Json.stringify(Json.obj("error", "unhandled", "message", message)));
        }
    }

    private static void send(HttpExchange exchange, int status, String payload) {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("content-type", "application/json");
        try {
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
        } catch (IOException err) {
            throw new UncheckedIOException(err);
        }
    }
}
