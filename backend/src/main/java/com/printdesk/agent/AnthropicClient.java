package com.printdesk.agent;

import com.printdesk.json.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Thin client for {@code POST /v1/messages} using the JDK HttpClient.
 * The API key stays on the server (env {@code ANTHROPIC_API_KEY}); the browser never sees it.
 * Retries once on 429/5xx/529 with a short backoff.
 */
public final class AnthropicClient implements LlmClient {
    private static final URI ENDPOINT = URI.create(baseUrl() + "/v1/messages");
    private static final String API_VERSION = "2023-06-01";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final String apiKey;

    public AnthropicClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("ANTHROPIC_API_KEY is not set");
        this.apiKey = apiKey;
    }

    @Override
    public Map<String, Object> createMessage(Map<String, Object> request) throws LlmException {
        String body = Json.write(request);
        for (int attempt = 0; ; attempt++) {
            HttpRequest req = HttpRequest.newBuilder(ENDPOINT)
                    .timeout(Duration.ofSeconds(120))
                    .header("content-type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            try {
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int st = res.statusCode();
                if (st == 200) return Json.parseObject(res.body());
                boolean retryable = st == 429 || st == 529 || st >= 500;
                if (retryable && attempt == 0) {
                    Thread.sleep(1500);
                    continue;
                }
                throw new LlmException("Claude API returned " + st + ": " + truncate(res.body()), st);
            } catch (IOException e) {
                if (attempt == 0) continue;
                throw new LlmException("Claude API not reachable: " + e.getMessage(), 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("interrupted", 0);
            }
        }
    }

    /** ANTHROPIC_BASE_URL lets you route through a gateway or a local mock in tests. */
    private static String baseUrl() {
        String b = System.getenv("ANTHROPIC_BASE_URL");
        return (b == null || b.isBlank() ? "https://api.anthropic.com" : b).replaceAll("/+$", "");
    }

    private static String truncate(String s) { return s == null ? "" : s.length() > 300 ? s.substring(0, 300) + "…" : s; }
}
