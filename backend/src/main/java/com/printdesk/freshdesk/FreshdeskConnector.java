package com.printdesk.freshdesk;

import com.printdesk.agent.LlmClient;
import com.printdesk.agent.TriageAgent;
import com.printdesk.catalog.Catalog;
import com.printdesk.triage.PreFilter;
import com.printdesk.triage.Priority;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.printdesk.json.Records.*;

/**
 * Runs the PrintDesk agent inside Freshdesk.
 *
 * <p>A Freshdesk automation rule calls the webhook when a ticket is created or the customer replies.
 * PrintDesk reads the ticket with its conversation, runs the same pre-filter, agent and priority
 * rules as the inbox, and writes the result back:
 * <ul>
 *   <li>the ticket priority (raised, never lowered: a colleague's choice wins),</li>
 *   <li>tags for language, printer, problem and warnings,</li>
 *   <li>one <b>private note</b> with summary, reasons, steps, part and the reply draft.</li>
 * </ul>
 * It never replies to the customer: a colleague copies or edits the draft and sends it from Freshdesk.
 * When a ticket is resolved with the custom field {@code cf_printdesk_fixed_step} set, the outcome
 * goes into the learning loop.
 */
public final class FreshdeskConnector {
    public static final String TAG = "printdesk";
    public static final String FIXED_STEP_FIELD = "cf_printdesk_fixed_step";
    static final String MARKER = "PrintDesk triage · ref ";
    private static final int HISTORY_CHARS = 6000;

    /** The agent call. In production {@code agent::triage}; tests pass a canned answer. */
    @FunctionalInterface
    public interface Triager {
        TriageAgent.Result triage(TriageAgent.Email email) throws LlmClient.LlmException;
    }

    public record Outcome(String action, long ticketId, String detail) {}

    private final FreshdeskApi api;
    private final Triager agent;
    private final Catalog catalog;
    private final Clock clock;
    private String fixedStepField = FIXED_STEP_FIELD;

    public FreshdeskConnector(FreshdeskApi api, Triager agent, Catalog catalog, Clock clock) {
        this.api = api;
        this.agent = agent;
        this.catalog = catalog;
        this.clock = clock;
    }

    /** API name of the ticket field where colleagues pick the step that fixed it (Freshdesk prefixes custom fields with cf_). */
    public FreshdeskConnector withFixedStepField(String apiName) {
        if (apiName != null && !apiName.isBlank()) this.fixedStepField = apiName;
        return this;
    }

    // ------------------------------------------------------------------ new ticket or customer reply

    public Outcome handleNewMessage(long ticketId) throws FreshdeskApi.FreshdeskException, LlmClient.LlmException {
        Map<String, Object> ticket = asMap(api.get("/api/v2/tickets/" + ticketId + "?include=requester"));
        List<Map<String, Object>> convs = asList(api.get("/api/v2/tickets/" + ticketId + "/conversations?per_page=100"));

        // The customer's messages: the ticket description, then incoming public conversations.
        List<Msg> thread = new ArrayList<>();
        thread.add(new Msg("customer", "t" + ticketId, str(ticket, "description_text", ""), time(ticket, "created_at")));
        for (Map<String, Object> c : convs) {
            if (bool(c, "private", false)) continue;
            String who = bool(c, "incoming", false) ? "customer" : "shop";
            thread.add(new Msg(who, "c" + (long) num(c, "id", 0), str(c, "body_text", ""), time(c, "created_at")));
        }
        Msg latest = thread.reversed().stream().filter(m -> m.who.equals("customer")).findFirst().orElse(thread.getFirst());

        // Freshdesk may call the webhook twice for the same message: one note per customer message.
        String ref = ticketId + "/" + latest.id;
        boolean done = convs.stream().anyMatch(c -> bool(c, "private", false) && str(c, "body_text", "").contains(MARKER + ref));
        if (done) return new Outcome("duplicate", ticketId, ref);

        Map<String, Object> requester = obj(ticket, "requester");
        String from = str(requester, "email", "");
        String subject = str(ticket, "subject", "");

        Optional<PreFilter.Reason> filtered = PreFilter.classify(from, subject, latest.text);
        if (filtered.isPresent()) {
            String why = filtered.get().name().toLowerCase(Locale.ROOT);
            updateTicket(ticket, null, List.of(TAG, "printdesk-filtered"));
            note(ticketId, "<p><b>PrintDesk</b> set this aside as not a support request (" + esc(why) + "). "
                    + "No AI was used.</p>" + footer(ref));
            return new Outcome("filtered", ticketId, why);
        }

        TriageAgent.Email email = new TriageAgent.Email(str(requester, "name", ""), from, subject, withHistory(latest, thread));
        TriageAgent.Result r = agent.triage(email);

        if (!r.isSupport()) {
            updateTicket(ticket, null, List.of(TAG, "printdesk-not-support"));
            note(ticketId, "<p><b>PrintDesk</b>: the agent thinks this is not a support request. Please check.</p>" + footer(ref));
            return new Outcome("not_support", ticketId, "");
        }

        Priority.Score p = Priority.score(new Priority.Signals(r.safety(), r.urgency(), r.sentiment(), r.printerDown(),
                r.deadline(), r.warranty(), thread.stream().filter(m -> m.who.equals("customer")).count() > 1, 0, null),
                latest.at == null ? Instant.now(clock) : latest.at, Instant.now(clock));

        List<String> tags = new ArrayList<>(List.of(TAG));
        if (r.lang() != null) tags.add("lang-" + r.lang());
        if (r.printerId() != null) tags.add("printer-" + r.printerId());
        if (r.problemId() != null) tags.add("problem-" + r.problemId().toLowerCase(Locale.ROOT));
        if (r.safety()) tags.add("safety-risk");
        if (r.suspicious() || !r.injectionHits().isEmpty()) tags.add("manipulation-attempt");
        if (r.escalate()) tags.add("escalate");
        updateTicket(ticket, freshdeskPriority(p.level()), tags);
        note(ticketId, noteHtml(r, p) + footer(ref));
        return new Outcome("triaged", ticketId, p.level().name());
    }

    // ------------------------------------------------------------------ resolved → learning loop

    public Outcome handleResolved(long ticketId) throws FreshdeskApi.FreshdeskException {
        Map<String, Object> ticket = asMap(api.get("/api/v2/tickets/" + ticketId));
        String problem = null, printer = null;
        for (Object t : list(ticket, "tags")) {
            String s = String.valueOf(t);
            if (s.startsWith("problem-")) problem = s.substring(8).toUpperCase(Locale.ROOT);
            if (s.startsWith("printer-")) printer = s.substring(8);
        }
        if (problem == null || catalog.problem(problem).isEmpty()) return new Outcome("skipped", ticketId, "no problem tag");
        String stepId = str(obj(ticket, "custom_fields"), fixedStepField);
        int idx = stepId == null ? -1 : catalog.problem(problem).get().stepIndex(stepId);
        catalog.record(new Catalog.CaseRecord(problem, printer, Math.max(idx + 1, 0), idx >= 0 ? idx : null, idx >= 0, null,
                LocalDate.now(clock)));
        return new Outcome("recorded", ticketId, idx >= 0 ? stepId : "unsolved");
    }

    // ------------------------------------------------------------------ writing back

    private void updateTicket(Map<String, Object> ticket, Integer priority, List<String> add) throws FreshdeskApi.FreshdeskException {
        Set<String> tags = new LinkedHashSet<>();
        for (Object t : list(ticket, "tags")) tags.add(String.valueOf(t));   // keep the team's own tags
        tags.addAll(add);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tags", List.copyOf(tags));
        int current = (int) num(ticket, "priority", 1);
        if (priority != null && priority > current) body.put("priority", priority);   // raise, never lower
        api.put("/api/v2/tickets/" + (long) num(ticket, "id", 0), body);
    }

    private void note(long ticketId, String html) throws FreshdeskApi.FreshdeskException {
        api.post("/api/v2/tickets/" + ticketId + "/notes", Map.of("body", html, "private", true));
    }

    /** P1 → Urgent (4), P2 → High (3), P3 → Medium (2), P4 → Low (1). */
    static int freshdeskPriority(Priority.Level l) {
        return switch (l) { case P1 -> 4; case P2 -> 3; case P3 -> 2; case P4 -> 1; };
    }

    String noteHtml(TriageAgent.Result r, Priority.Score p) {
        StringBuilder h = new StringBuilder();
        h.append("<p><b>PrintDesk triage · ").append(p.level()).append("</b> (").append(p.points()).append(" pts, reply within ")
                .append(p.level().slaHours).append(" h)<br>");
        List<String> why = p.reasons().stream().map(x -> x.key().replace('_', ' ') + " +" + x.points()
                + (x.detail() == null ? "" : " (" + x.detail() + ")")).toList();
        h.append(esc(String.join(" · ", why))).append("</p>");

        if (r.safety()) h.append("<p>⚠️ <b>Safety risk.</b> The draft tells the customer to keep the printer switched off and unplugged.</p>");
        if (r.suspicious() || !r.injectionHits().isEmpty())
            h.append("<p>⚠️ <b>Possible manipulation attempt</b> (").append(esc(String.join(", ", r.injectionHits())))
                    .append("). The agent treated it as data. Check the draft carefully.</p>");
        if (!r.foreignLinks().isEmpty())
            h.append("<p>⚠️ The draft contains links outside the shop: ").append(esc(String.join(", ", r.foreignLinks()))).append("</p>");
        if (r.escalate()) h.append("<p>⚠️ <b>Needs a colleague's decision:</b> ").append(esc(r.escalateReason())).append("</p>");

        h.append("<p><b>Summary</b><br>").append(esc(r.summary())).append("</p>");
        h.append("<p><b>Detected</b><br>Language: ").append(esc(r.lang()))
                .append(" · Printer: ").append(esc(catalog.printer(r.printerId()).map(Catalog.Printer::name).orElse("unknown")))
                .append(" · Problem: ").append(esc(catalog.problem(r.problemId()).map(x -> x.id() + " " + x.en()).orElse("unknown")))
                .append(" · Warranty: ").append(esc(r.warranty()))
                .append(r.orderNo() == null || r.orderNo().isBlank() ? "" : " · Order: " + esc(r.orderNo())).append("</p>");
        if (!r.steps().isEmpty()) {
            h.append("<p><b>Suggested steps</b> (ranked by how often they fixed this problem)</p><ol>");
            for (TriageAgent.StepRef s : r.steps()) h.append("<li>").append(esc(s.text())).append("</li>");
            h.append("</ol>");
        }
        for (TriageAgent.PartRef pr : r.parts()) {
            catalog.part(pr.id()).ifPresent(part -> h.append("<p><b>Part:</b> <a href=\"").append(esc(TriageAgent.orderUrl(part))).append("\">")
                    .append(esc(part.name())).append("</a> · €").append(String.format(Locale.ROOT, "%.2f", part.price()))
                    .append(" · ").append(part.days()).append(" days · ").append(part.stock() > 0 ? part.stock() + " in stock" : "out of stock")
                    .append(part.official() ? " · original" : " · alternative").append("<br>").append(esc(pr.why())).append("</p>"));
        }
        h.append("<p><b>Reply draft</b> (copy into the reply, check and edit before sending)</p>");
        h.append("<blockquote>").append(esc(r.reply()).replace("\n", "<br>")).append("</blockquote>");
        return h.toString();
    }

    private static String footer(String ref) {
        return "<p style=\"color:#888;font-size:12px\">" + MARKER + esc(ref) + " · internal note, not visible to the customer</p>";
    }

    // ------------------------------------------------------------------ helpers

    private record Msg(String who, String id, String text, Instant at) {}

    private static String withHistory(Msg latest, List<Msg> thread) {
        List<Msg> before = thread.subList(0, thread.indexOf(latest));
        if (before.isEmpty()) return latest.text;
        StringBuilder h = new StringBuilder();
        for (Msg m : before) h.append('[').append(m.who.equals("customer") ? "Customer" : "Our reply").append("]\n").append(m.text).append("\n\n");
        String hist = h.length() > HISTORY_CHARS ? "…" + h.substring(h.length() - HISTORY_CHARS) : h.toString();
        return latest.text + "\n\n--- Earlier in this conversation (oldest first). ---\n" + hist;
    }

    private static Instant time(Map<String, Object> m, String k) {
        String v = str(m, k);
        if (v == null) return null;
        try { return Instant.parse(v); } catch (DateTimeParseException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object o) {
        if (!(o instanceof List<?> l)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object x : l) if (x instanceof Map) out.add((Map<String, Object>) x);
        return out;
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
