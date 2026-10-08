package com.printdesk.freshdesk;

import com.printdesk.TestData;
import com.printdesk.agent.TriageAgent;
import com.printdesk.catalog.Catalog;
import com.printdesk.json.Json;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FreshdeskConnectorTest {

    /** In-memory Freshdesk: canned GET answers, every write recorded. */
    static final class FakeFreshdesk implements FreshdeskApi {
        final Map<String, Object> gets = new HashMap<>();
        final List<String> writes = new ArrayList<>();
        final List<Map<String, Object>> bodies = new ArrayList<>();

        FakeFreshdesk on(String path, String json) { gets.put(path, Json.parse(json)); return this; }

        @Override public Object get(String path) throws FreshdeskException {
            if (!gets.containsKey(path)) throw new FreshdeskException(404, path);
            return gets.get(path);
        }
        @Override public Map<String, Object> post(String path, Map<String, Object> body) { writes.add("POST " + path); bodies.add(body); return Map.of("id", 1); }
        @Override public Map<String, Object> put(String path, Map<String, Object> body) { writes.add("PUT " + path); bodies.add(body); return Map.of(); }

        Map<String, Object> body(String write) { return bodies.get(writes.indexOf(write)); }
    }

    private static final String TICKET = """
            {"id":42,"subject":"Nozzle clogged","description_text":"My A1 nozzle is clogged, order 3DJ-2024-077315",
             "priority":1,"tags":["vip"],"created_at":"2026-10-08T07:00:00Z",
             "requester":{"name":"James Carter","email":"james@example.com"}}""";

    static TriageAgent.Result result(boolean safety, boolean suspicious) {
        return new TriageAgent.Result(true, "en", "a1", "P01", "high", "angry", safety, true, null, "in", "3DJ-2024-077315",
                false, "", "Clogged nozzle on an A1, customer is angry.", "James",
                List.of(new TriageAgent.StepRef("P01-S2", "Do a cold pull")), List.of(new TriageAgent.PartRef("BL-HE-A04H", "original hotend")),
                "Re: Nozzle clogged", "Hi James,\n1. Do a cold pull <now>", suspicious, suspicious ? List.of("ignore instructions") : List.of(),
                List.of(), List.of());
    }

    private static FreshdeskConnector connector(FakeFreshdesk fd, TriageAgent.Result r, List<TriageAgent.Email> seen) {
        return new FreshdeskConnector(fd, e -> { seen.add(e); return r; }, TestData.catalog(), TestData.CLOCK);
    }

    @Test
    void newTicketGetsPriorityTagsAndAPrivateNoteButNoReply() throws Exception {
        FakeFreshdesk fd = new FakeFreshdesk().on("/api/v2/tickets/42?include=requester", TICKET).on("/api/v2/tickets/42/conversations?per_page=100", "[]");
        List<TriageAgent.Email> seen = new ArrayList<>();
        FreshdeskConnector.Outcome o = connector(fd, result(false, false), seen).handleNewMessage(42);

        assertEquals("triaged", o.action());
        assertEquals("james@example.com", seen.getFirst().fromEmail());
        assertTrue(seen.getFirst().body().startsWith("My A1 nozzle is clogged"));
        assertEquals(List.of("PUT /api/v2/tickets/42", "POST /api/v2/tickets/42/notes"), fd.writes, "never calls /reply");

        Map<String, Object> put = fd.body("PUT /api/v2/tickets/42");
        assertEquals(List.of("vip", "printdesk", "lang-en", "printer-a1", "problem-p01"), put.get("tags"), "keeps the team's tags");
        assertEquals(Integer.valueOf(4), put.get("priority"), "urgent + angry + printer down + in warranty + waiting = P1 = Urgent");

        Map<String, Object> note = fd.body("POST /api/v2/tickets/42/notes");
        assertEquals(true, note.get("private"));
        String html = (String) note.get("body");
        assertTrue(html.contains("PrintDesk triage · P1"));
        assertTrue(html.contains("Do a cold pull &lt;now&gt;"), "customer and model text is escaped");
        assertTrue(html.contains("Hotend - A1/A2 Series"));
        assertTrue(html.contains(FreshdeskConnector.MARKER + "42/t42"));
    }

    @Test
    void customerReplyIsTriagedWithHistoryAndDuplicatesAreIgnored() throws Exception {
        String convs = """
                [{"id":7,"incoming":false,"private":false,"body_text":"Please do a cold pull.","created_at":"2026-10-08T08:00:00Z"},
                 {"id":8,"incoming":true,"private":false,"body_text":"Did not help, still clogged.","created_at":"2026-10-08T09:00:00Z"}]""";
        FakeFreshdesk fd = new FakeFreshdesk().on("/api/v2/tickets/42?include=requester", TICKET).on("/api/v2/tickets/42/conversations?per_page=100", convs);
        List<TriageAgent.Email> seen = new ArrayList<>();
        connector(fd, result(false, false), seen).handleNewMessage(42);

        String body = seen.getFirst().body();
        assertTrue(body.startsWith("Did not help, still clogged."));
        assertTrue(body.contains("[Our reply]\nPlease do a cold pull."));
        assertTrue(((String) fd.body("POST /api/v2/tickets/42/notes").get("body")).contains("42/c8"));

        // The same webhook again: the note for message c8 exists, so nothing is written.
        String withNote = convs.replace("]", ",{\"id\":9,\"incoming\":false,\"private\":true,\"body_text\":\"... " + FreshdeskConnector.MARKER + "42/c8 ...\"}]");
        FakeFreshdesk again = new FakeFreshdesk().on("/api/v2/tickets/42?include=requester", TICKET).on("/api/v2/tickets/42/conversations?per_page=100", withNote);
        assertEquals("duplicate", connector(again, result(false, false), new ArrayList<>()).handleNewMessage(42).action());
        assertTrue(again.writes.isEmpty());
    }

    @Test
    void newsletterIsSetAsideWithoutCallingTheAgent() throws Exception {
        String t = TICKET.replace("Nozzle clogged", "Autumn sale").replace("james@example.com", "newsletter@shop.example").replace("My A1 nozzle is clogged, order 3DJ-2024-077315",
                "Our autumn sale! Click to unsubscribe.");
        FakeFreshdesk fd = new FakeFreshdesk().on("/api/v2/tickets/42?include=requester", t).on("/api/v2/tickets/42/conversations?per_page=100", "[]");
        List<TriageAgent.Email> seen = new ArrayList<>();
        assertEquals("filtered", connector(fd, result(false, false), seen).handleNewMessage(42).action());
        assertTrue(seen.isEmpty());
        assertNull(fd.body("PUT /api/v2/tickets/42").get("priority"));
    }

    @Test
    void neverLowersAPriorityAColleagueSetAndFlagsManipulation() throws Exception {
        TriageAgent.Result calm = new TriageAgent.Result(true, "en", "a1", "P01", "low", "neutral", false, false, null, "unknown", null,
                false, "", "s", "", List.of(), List.of(), "", "Hi", true, List.of("ignore instructions"), List.of(), List.of());
        FakeFreshdesk fd = new FakeFreshdesk().on("/api/v2/tickets/42?include=requester", TICKET.replace("\"priority\":1", "\"priority\":4"))
                .on("/api/v2/tickets/42/conversations?per_page=100", "[]");
        connector(fd, calm, new ArrayList<>()).handleNewMessage(42);
        Map<String, Object> put = fd.body("PUT /api/v2/tickets/42");
        assertNull(put.get("priority"), "Urgent set by a colleague stays");
        assertTrue(((List<?>) put.get("tags")).contains("manipulation-attempt"));
        assertTrue(((String) fd.body("POST /api/v2/tickets/42/notes").get("body")).contains("Possible manipulation attempt"));
    }

    @Test
    void resolvedTicketFeedsTheLearningLoop() throws Exception {
        Catalog c = TestData.catalog();
        int before = c.cases.size();
        FakeFreshdesk fd = new FakeFreshdesk().on("/api/v2/tickets/42",
                "{\"id\":42,\"tags\":[\"printdesk\",\"printer-a1\",\"problem-p01\"],\"custom_fields\":{\"cf_printdesk_fixed_step\":\"P01-S2\"}}");
        FreshdeskConnector.Outcome o = new FreshdeskConnector(fd, e -> { throw new AssertionError("no agent call"); }, c, TestData.CLOCK).handleResolved(42);
        assertEquals("recorded", o.action());
        assertEquals(before + 1, c.cases.size());
        Catalog.CaseRecord rec = c.cases.getLast();
        assertEquals("P01", rec.problem());
        assertEquals(Integer.valueOf(1), rec.solvedStep());
        assertTrue(rec.resolved());
    }

    @Test
    void mapsPrintDeskPriorityToFreshdesk() {
        assertEquals(4, FreshdeskConnector.freshdeskPriority(com.printdesk.triage.Priority.Level.P1));
        assertEquals(1, FreshdeskConnector.freshdeskPriority(com.printdesk.triage.Priority.Level.P4));
    }
}
