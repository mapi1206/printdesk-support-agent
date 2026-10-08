package com.printdesk.api;

import com.printdesk.agent.LlmClient;
import com.printdesk.agent.Models;
import com.printdesk.agent.TriageAgent;
import com.printdesk.catalog.Catalog;
import com.printdesk.json.Json;
import com.printdesk.json.Records;
import com.printdesk.knowledge.Learning;
import com.printdesk.parts.PartRanker;
import com.printdesk.triage.PreFilter;
import com.printdesk.triage.Priority;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static com.printdesk.json.Records.*;

/**
 * REST API + static file server (JDK {@code HttpServer}, virtual threads).
 *
 * <pre>
 * GET  /api/health                      → {ok, llm}
 * POST /api/triage                      → full triage of one email (agent + tools + priority)
 * GET  /api/troubleshoot?problem&printer → ranked steps with fix rates
 * GET  /api/parts?printer&cat&prio       → best-offer ranking
 * POST /api/complete                    → plain model completion for the UI (key stays server-side)
 * /api/db/{collection}[/{id}]           → GET / PUT / DELETE documents for the local UI
 * GET  /data.json                      → the catalog file the backend loaded
 * everything else                       → files from the frontend directory
 * </pre>
 */
public final class Server {
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9_.~:@+-]{1,200}");

    private final Catalog catalog;
    private final Learning learning;
    private final PartRanker ranker;
    private final TriageAgent agent;     // null when no API key is configured
    private final LlmClient llm;         // null when no API key is configured
    private final LocalStore store;
    private final Path staticRoot;
    private final Path dataFile;
    private final Clock clock;
    private final DemoMailbox mail;

    public Server(Catalog catalog, Learning learning, PartRanker ranker, TriageAgent agent, LlmClient llm,
                  LocalStore store, Path staticRoot, Path dataFile, Clock clock) {
        this.catalog = catalog;
        this.learning = learning;
        this.ranker = ranker;
        this.agent = agent;
        this.llm = llm;
        this.store = store;
        this.staticRoot = staticRoot.toAbsolutePath().normalize();
        this.dataFile = dataFile;
        this.clock = clock;
        this.mail = new DemoMailbox(clock);
    }

    public HttpServer start(int port) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        s.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        s.createContext("/api/", this::api);
        s.createContext("/", this::files);
        s.start();
        return s;
    }

    // ------------------------------------------------------------------ API

    private void api(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            Map<String, String> q = query(ex.getRequestURI().getRawQuery());
            if (path.equals("/api/health")) {
                json(ex, 200, Map.of("ok", true, "llm", llm != null));
            } else if (path.equals("/api/troubleshoot") && method.equals("GET")) {
                troubleshoot(ex, q);
            } else if (path.equals("/api/parts") && method.equals("GET")) {
                parts(ex, q);
            } else if (path.equals("/api/triage") && method.equals("POST")) {
                triage(ex);
            } else if (path.equals("/api/complete") && method.equals("POST")) {
                complete(ex);
            } else if (path.startsWith("/api/mail")) {
                mail(ex, path, method, q);
            } else if (path.startsWith("/api/db/")) {
                db(ex, path.substring("/api/db/".length()), method);
            } else {
                json(ex, 404, Map.of("error", "not found"));
            }
        } catch (IllegalArgumentException e) {
            json(ex, 400, Map.of("error", e.getMessage()));
        } catch (LlmClient.LlmException e) {
            json(ex, e.status == 429 ? 429 : 502, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            json(ex, 500, Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    private void troubleshoot(HttpExchange ex, Map<String, String> q) throws IOException {
        String pid = require(q, "problem");
        List<Map<String, Object>> steps = new ArrayList<>();
        for (Learning.StepStat s : learning.rankedSteps(pid, q.get("printer"))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.step().id());
            m.put("en", s.step().en());
            m.put("de", s.step().de());
            m.put("part_cat", s.step().partCat());
            m.put("reached", s.reached());
            m.put("fixed", s.fixed());
            m.put("fix_rate", s.rate());
            steps.add(m);
        }
        json(ex, 200, Map.of("problem", pid, "steps", steps));
    }

    private void parts(HttpExchange ex, Map<String, String> q) throws IOException {
        PartRanker.Priority prio = PartRanker.Priority.valueOf(q.getOrDefault("prio", "balanced").toUpperCase());
        List<Map<String, Object>> out = new ArrayList<>();
        for (PartRanker.Ranked r : ranker.rank(q.get("printer"), q.get("cat"), q.get("hint"), prio)) {
            Map<String, Object> m = Records.toMap(r.part());
            m.put("total_cost", Math.round(r.totalCost() * 100) / 100.0);
            m.put("score", Math.round(r.score() * 100) / 100.0);
            out.add(m);
        }
        json(ex, 200, out);
    }

    private void triage(HttpExchange ex) throws Exception {
        Map<String, Object> in = Json.parseObject(body(ex));
        String from = str(in, "from_email"), subject = str(in, "subject"), text = str(in, "body");
        if (text == null || text.isBlank()) throw new IllegalArgumentException("body is required");
        Optional<PreFilter.Reason> filtered = PreFilter.classify(from, subject, text);
        if (filtered.isPresent()) {
            json(ex, 200, Map.of("filtered", true, "reason", filtered.get().name().toLowerCase()));
            return;
        }
        if (agent == null) {
            json(ex, 503, Map.of("error", "ANTHROPIC_API_KEY is not set on the server"));
            return;
        }
        TriageAgent.Result r = agent.triage(new TriageAgent.Email(str(in, "from_name"), from, subject, text));
        Priority.Score p = Priority.score(new Priority.Signals(r.safety(), r.urgency(), r.sentiment(), r.printerDown(),
                r.deadline(), r.warranty(), false, 0, null), Instant.now(clock), Instant.now(clock));
        Map<String, Object> out = new LinkedHashMap<>(Records.toMap(r));
        out.put("priority", Records.toMap(p));
        json(ex, 200, out);
    }

    /** Plain completion for the UI's own prompts. Input: a string or [{role, content}]. */
    private void complete(HttpExchange ex) throws Exception {
        if (llm == null) {
            json(ex, 503, Map.of("error", "ANTHROPIC_API_KEY is not set on the server"));
            return;
        }
        Map<String, Object> in = Json.parseObject(body(ex));
        Object input = in.get("input");
        List<Map<String, Object>> messages = new ArrayList<>();
        if (input instanceof String s) messages.add(Map.of("role", "user", "content", s));
        else if (input instanceof List<?> l) {
            for (Object o : l) {
                @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
                String role = "assistant".equals(str(m, "role")) ? "assistant" : "user";
                if (!messages.isEmpty() && messages.getLast().get("role").equals(role)) {   // merge consecutive turns
                    Map<String, Object> last = messages.removeLast();
                    messages.add(Map.of("role", role, "content", last.get("content") + "\n\n" + str(m, "content", "")));
                } else messages.add(Map.of("role", role, "content", str(m, "content", "")));
            }
        } else throw new IllegalArgumentException("input must be a string or a list of turns");
        String model = "quick".equals(str(in, "tier")) ? Models.quick() : Models.triage();
        Map<String, Object> res = llm.createMessage(Map.of("model", model, "max_tokens", 4096, "messages", messages));
        StringBuilder text = new StringBuilder();
        for (Object o : list(res, "content")) if (o instanceof Map<?, ?> m && "text".equals(m.get("type"))) text.append(m.get("text"));
        json(ex, 200, Map.of("text", text.toString(), "truncated", "max_tokens".equals(str(res, "stop_reason"))));
    }

    private void db(HttpExchange ex, String rest, String method) throws IOException {
        String[] seg = rest.split("/");
        for (String s : seg) if (!SAFE_SEGMENT.matcher(s).matches()) throw new IllegalArgumentException("bad path");
        if (seg.length == 1 && method.equals("GET")) {
            json(ex, 200, store.list(seg[0]));
        } else if (seg.length == 2) {
            switch (method) {
                case "GET" -> {
                    Object d = store.get(seg[0], seg[1]);
                    json(ex, 200, d == null ? Map.of("exists", false) : Map.of("exists", true, "data", d));
                }
                case "PUT" -> {
                    store.put(seg[0], seg[1], Json.parse(body(ex)));
                    json(ex, 200, Map.of("ok", true));
                }
                case "DELETE" -> json(ex, 200, Map.of("ok", store.delete(seg[0], seg[1])));
                default -> json(ex, 405, Map.of("error", "method not allowed"));
            }
        } else json(ex, 400, Map.of("error", "use /api/db/{collection} or /api/db/{collection}/{id}"));
    }

    /**
     * Local demo mailbox (stands in for Gmail):
     * GET  /api/mail/threads?q=to:addr      → {threads:[{id, messageCount, …}]}   (search_threads)
     * GET  /api/mail/threads/{id}           → {messages:[…]}                      (get_thread)
     * POST /api/mail/reply {messageId, body, to}                                  (reply)
     * POST /api/mail/send {to:[…], subject, body, from}                           (send_message)
     * POST /api/mail/customer {from_name, from, to, subject, body, thread_id?}    (a customer writes)
     * GET  /api/mail/box?addr=…             → the customer's own threads
     * DELETE /api/mail                      → empty the mailbox
     */
    @SuppressWarnings("unchecked")
    private void mail(HttpExchange ex, String path, String method, Map<String, String> q) throws IOException {
        String rest = path.equals("/api/mail") ? "" : path.substring("/api/mail/".length());
        switch (method + " " + (rest.startsWith("threads/") ? "threads/*" : rest)) {
            case "GET threads" -> json(ex, 200, Map.of("threads", mail.searchThreads(q.get("q"))));
            case "GET threads/*" -> json(ex, 200, Map.of("messages", mail.getThread(rest.substring("threads/".length()))));
            case "GET box" -> json(ex, 200, Map.of("threads", mail.mailboxOf(require(q, "addr"))));
            case "POST reply" -> {
                Map<String, Object> in = Json.parseObject(body(ex));
                json(ex, 200, mail.reply(str(in, "messageId"), str(in, "body"), (List<String>) (List<?>) list(in, "to")));
            }
            case "POST send" -> {
                Map<String, Object> in = Json.parseObject(body(ex));
                json(ex, 200, mail.send((List<String>) (List<?>) list(in, "to"), str(in, "subject"), str(in, "body"),
                        str(in, "from", "support@printdesk-demo.example")));
            }
            case "POST customer" -> {
                Map<String, Object> in = Json.parseObject(body(ex));
                json(ex, 200, mail.customerSend(str(in, "from_name"), str(in, "from"), str(in, "to"), str(in, "subject"),
                        str(in, "body"), str(in, "thread_id")));
            }
            case "DELETE " -> {
                mail.clear();
                json(ex, 200, Map.of("ok", true));
            }
            default -> json(ex, 404, Map.of("error", "not found"));
        }
    }

    // ------------------------------------------------------------------ static files

    private void files(HttpExchange ex) throws IOException {
        String p = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
        if (p.equals("/")) p = "/index.html";
        // The UI loads the catalog from ./data.json – serve the same file the backend uses.
        Path f = p.equals("/data.json") ? dataFile : staticRoot.resolve(p.substring(1)).normalize();
        if (p.equals("/data.json") && Files.isRegularFile(f)) {
            send(ex, 200, "application/json; charset=utf-8", Files.readAllBytes(f));
            return;
        }
        if (!f.startsWith(staticRoot) || !Files.isRegularFile(f)) {
            send(ex, 404, "text/plain; charset=utf-8", "not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String type = p.endsWith(".html") ? "text/html; charset=utf-8" : p.endsWith(".js") ? "text/javascript; charset=utf-8"
                : p.endsWith(".json") ? "application/json; charset=utf-8" : p.endsWith(".css") ? "text/css; charset=utf-8" : "application/octet-stream";
        send(ex, 200, type, Files.readAllBytes(f));
    }

    // ------------------------------------------------------------------ helpers

    private static String body(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] b = in.readNBytes(2_000_001);
            if (b.length > 2_000_000) throw new IllegalArgumentException("request too large");
            return new String(b, StandardCharsets.UTF_8);
        }
    }

    private static void json(HttpExchange ex, int status, Object body) throws IOException {
        send(ex, status, "application/json; charset=utf-8", Json.write(body).getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int status, String type, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("content-type", type);
        ex.getResponseHeaders().set("x-content-type-options", "nosniff");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> m = new LinkedHashMap<>();
        if (raw == null) return m;
        for (String kv : raw.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) m.put(URLDecoder.decode(kv.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    private static String require(Map<String, String> q, String k) {
        String v = q.get(k);
        if (v == null || v.isBlank()) throw new IllegalArgumentException(k + " is required");
        return v;
    }
}
