package net.codeverse.updater;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * A minimal HTTP server standing in for GitHub, so the tests drive the real
 * HttpClient path rather than a mock. Routes are registered as literal paths
 * returning canned bodies, which is enough to serve a releases listing, a
 * checksum sidecar and a jar download.
 */
final class FakeGitHub implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, byte[]> routes = new HashMap<>();
    private final Map<String, Integer> statuses = new HashMap<>();

    FakeGitHub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] body = routes.get(path);
            int status = statuses.getOrDefault(path, body != null ? 200 : 404);
            byte[] payload = body != null ? body : "not found".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
    }

    void route(String path, String body) {
        routes.put(path, body.getBytes(StandardCharsets.UTF_8));
    }

    void route(String path, byte[] body) {
        routes.put(path, body);
    }

    void status(String path, int status) {
        statuses.put(path, status);
    }

    String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    HttpClient client() {
        return HttpClient.newHttpClient();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
