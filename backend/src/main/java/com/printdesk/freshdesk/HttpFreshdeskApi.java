package com.printdesk.freshdesk;

import com.printdesk.json.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Freshdesk REST API v2 over the JDK HttpClient.
 * Auth is HTTP Basic with the API key as user name and "X" as password, as Freshdesk documents it.
 * On 429 it waits for {@code Retry-After} (at most 30 s) and tries once more.
 */
public final class HttpFreshdeskApi implements FreshdeskApi {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final String baseUrl;
    private final String auth;

    /** @param baseUrl e.g. {@code https://yourshop.freshdesk.com} */
    public HttpFreshdeskApi(String baseUrl, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("FRESHDESK_API_KEY is not set");
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.auth = "Basic " + Base64.getEncoder().encodeToString((apiKey + ":X").getBytes(StandardCharsets.UTF_8));
    }

    public static String baseUrlFor(String domain) {
        return domain.startsWith("http") ? domain : "https://" + domain + ".freshdesk.com";
    }

    @Override
    public Object get(String path) throws FreshdeskException {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> post(String path, Map<String, Object> body) throws FreshdeskException {
        return (Map<String, Object>) send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> put(String path, Map<String, Object> body) throws FreshdeskException {
        return (Map<String, Object>) send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("content-type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8)));
    }

    private Object send(HttpRequest.Builder b) throws FreshdeskException {
        HttpRequest req = b.timeout(Duration.ofSeconds(30)).header("authorization", auth).build();
        for (int attempt = 0; ; attempt++) {
            HttpResponse<String> res;
            try {
                res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new FreshdeskException(0, "Freshdesk unreachable: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FreshdeskException(0, "interrupted");
            }
            int s = res.statusCode();
            if (s == 429 && attempt == 0) {
                long wait = res.headers().firstValue("retry-after").map(HttpFreshdeskApi::seconds).orElse(5L);
                sleep(Math.min(30, wait) * 1000);
                continue;
            }
            if (s >= 200 && s < 300) return res.body().isBlank() ? Map.of() : Json.parse(res.body());
            throw new FreshdeskException(s, "Freshdesk " + s + ": " + res.body().substring(0, Math.min(300, res.body().length())));
        }
    }

    private static long seconds(String v) {
        try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return 5; }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
