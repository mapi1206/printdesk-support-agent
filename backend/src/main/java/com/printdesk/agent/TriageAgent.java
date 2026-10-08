package com.printdesk.agent;

import com.printdesk.catalog.Catalog;
import com.printdesk.catalog.Catalog.Order;
import com.printdesk.json.Json;
import com.printdesk.orders.OrderService;
import com.printdesk.triage.InjectionGuard;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.printdesk.json.Records.*;

/**
 * Reads one customer email and works it up for a colleague: language, printer, problem,
 * priority signals, ranked steps, parts with order links, and a reply draft in the customer's
 * language. Runs the Claude tool-use loop: the model calls {@link Tools} until it can answer,
 * then returns one JSON object, which is validated against the catalog (unknown ids are dropped).
 */
public final class TriageAgent {
    static final int MAX_ROUNDS = 6;

    public record Email(String fromName, String fromEmail, String subject, String body) {}

    public record StepRef(String id, String text) {}

    public record PartRef(String id, String why) {}

    public record Result(boolean isSupport, String lang, String printerId, String problemId, String urgency, String sentiment,
                         boolean safety, boolean printerDown, String deadline, String warranty, String orderNo,
                         boolean escalate, String escalateReason, String summary, String customerName,
                         List<StepRef> steps, List<PartRef> parts, String emailSubject, String reply,
                         boolean suspicious, List<String> injectionHits, List<String> foreignLinks,
                         List<String> toolTrace) {}

    private final Catalog catalog;
    private final Tools tools;
    private final OrderService orders;
    private final LlmClient llm;
    private final Clock clock;
    private String style = "";

    public TriageAgent(Catalog catalog, Tools tools, OrderService orders, LlmClient llm, Clock clock) {
        this.catalog = catalog;
        this.tools = tools;
        this.orders = orders;
        this.llm = llm;
        this.clock = clock;
    }

    /** Shop reply style (tone, signature, phrases) appended to the instructions. */
    public void setStyle(String style) { this.style = style == null ? "" : style; }

    public Result triage(Email email) throws LlmClient.LlmException {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", Prompts.emailBlock(email)));
        List<String> trace = new ArrayList<>();
        String finalText = null;

        for (int round = 0; round < MAX_ROUNDS && finalText == null; round++) {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("model", Models.triage());
            req.put("max_tokens", 4096);
            req.put("system", Prompts.system(catalog, LocalDate.now(clock)) + style);
            req.put("tools", tools.definitions());
            req.put("messages", List.copyOf(messages));
            if (round == MAX_ROUNDS - 1) req.put("tool_choice", Map.of("type", "none"));

            Map<String, Object> res = llm.createMessage(req);
            List<Object> content = list(res, "content");
            messages.add(Map.of("role", "assistant", "content", content));

            if ("tool_use".equals(str(res, "stop_reason"))) {
                List<Map<String, Object>> results = new ArrayList<>();
                for (Object o : content) {
                    @SuppressWarnings("unchecked") Map<String, Object> block = (Map<String, Object>) o;
                    if (!"tool_use".equals(str(block, "type"))) continue;
                    String name = str(block, "name");
                    Map<String, Object> input = obj(block, "input");
                    Map<String, Object> tr = new LinkedHashMap<>();
                    tr.put("type", "tool_result");
                    tr.put("tool_use_id", str(block, "id"));
                    try {
                        Object out = tools.run(name, input);
                        tr.put("content", Json.write(out));
                        trace.add(name + "(" + Json.write(input) + ")");
                    } catch (RuntimeException e) {
                        tr.put("content", "Error: " + e.getMessage());
                        tr.put("is_error", true);
                        trace.add(name + " → error: " + e.getMessage());
                    }
                    results.add(tr);
                }
                messages.add(Map.of("role", "user", "content", results));
            } else {
                finalText = content.stream()
                        .filter(o -> o instanceof Map<?, ?> m && "text".equals(m.get("type")))
                        .map(o -> String.valueOf(((Map<?, ?>) o).get("text")))
                        .collect(Collectors.joining("\n"));
            }
        }
        if (finalText == null) throw new LlmClient.LlmException("No answer after " + MAX_ROUNDS + " rounds", 0);
        return clean(extractJson(finalText), email, trace);
    }

    // ------------------------------------------------------------------ output validation

    /** Accepts a bare JSON object, a fenced block, or text with one object inside. */
    static Map<String, Object> extractJson(String text) {
        String t = text.trim();
        Matcher fence = Pattern.compile("```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.DOTALL).matcher(t);
        if (fence.find()) t = fence.group(1);
        int a = t.indexOf('{'), b = t.lastIndexOf('}');
        if (a < 0 || b <= a) throw new IllegalArgumentException("No JSON object in model answer");
        return Json.parseObject(t.substring(a, b + 1));
    }

    private static final Pattern LINK = Pattern.compile("\\[\\[LINK:([A-Za-z0-9-]+)]]");

    @SuppressWarnings("unchecked")
    Result clean(Map<String, Object> a, Email email, List<String> trace) {
        String printer = catalog.printer(str(a, "printer_id")).map(Catalog.Printer::id).orElse(null);
        String problem = catalog.problem(str(a, "problem_id")).map(Catalog.Problem::id).orElse(null);
        String warranty = pick(str(a, "warranty"), List.of("in", "out", "unknown"), "unknown");
        String orderNo = str(a, "order_no");

        // Facts from the shop system win over the model and the customer.
        Optional<Order> order = orders.find(orderNo, email.fromEmail());
        if (order.isPresent()) {
            orderNo = order.get().orderNo();
            warranty = orders.warranty(order.get()).name().toLowerCase(Locale.ROOT);
            if (printer == null) printer = order.get().printerId().orElse(null);
        }

        List<StepRef> steps = new ArrayList<>();
        for (Object o : list(a, "steps")) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) o;
            String id = str(m, "id");
            if (catalog.step(id).isPresent() && steps.size() < 5) steps.add(new StepRef(id, cap(str(m, "text", ""), 400)));
        }
        List<PartRef> parts = new ArrayList<>();
        for (Object o : list(a, "parts")) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) o;
            String id = str(m, "id");
            if (catalog.part(id).isPresent() && parts.size() < 4) parts.add(new PartRef(id, cap(str(m, "why", ""), 300)));
        }
        String reply = cap(str(a, "reply", ""), 8000);
        Matcher lm = LINK.matcher(reply);
        StringBuilder sb = new StringBuilder();
        while (lm.find()) lm.appendReplacement(sb, Matcher.quoteReplacement(catalog.part(lm.group(1)).map(TriageAgent::orderUrl).orElse("")));
        lm.appendTail(sb);
        reply = sb.toString();

        List<String> hits = InjectionGuard.scan((email.subject() == null ? "" : email.subject()) + "\n" + email.body());
        boolean suspicious = bool(a, "suspicious", false) || !hits.isEmpty();

        return new Result(bool(a, "is_support", true), cap(str(a, "lang", "en"), 8).toLowerCase(Locale.ROOT), printer, problem,
                pick(str(a, "urgency"), List.of("low", "medium", "high"), "low"),
                pick(str(a, "sentiment"), List.of("positive", "neutral", "frustrated", "angry"), "neutral"),
                bool(a, "safety", false), bool(a, "printer_down", false), cap(str(a, "deadline"), 80), warranty, orderNo,
                bool(a, "escalate", false), cap(str(a, "escalate_reason", ""), 300), cap(str(a, "summary", ""), 900),
                cap(str(a, "customer_name", ""), 120), steps, parts, cap(str(a, "email_subject", ""), 200), reply,
                suspicious, hits, InjectionGuard.foreignLinks(reply), List.copyOf(trace));
    }

    public static String orderUrl(Catalog.Part p) {
        String q = (p.brand() + " " + p.name().replaceAll("\\(.*?\\)", "")).trim();
        return "https://www.3djake.at/search?keyword=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
    }

    private static String pick(String v, List<String> ok, String def) { return v != null && ok.contains(v) ? v : def; }

    private static String cap(String s, int n) { return s == null ? null : s.length() > n ? s.substring(0, n) : s; }
}
