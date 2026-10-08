package com.printdesk.api;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small in-memory mail server that stands in for Gmail when PrintDesk runs locally.
 *
 * <p>It answers the same calls the UI makes to the Gmail connector in claude.ai
 * ({@code search_threads}, {@code get_thread}, {@code reply}, {@code send_message}) with the same
 * field names, so the inbox → ticket → reply → follow-up flow runs through exactly the same UI code.
 * Customers write and read their mail on {@code /mailbox.html}.
 *
 * <p>Labels are seen from the support account: customer mail is {@code INBOX}, our replies are {@code SENT}.
 */
public final class DemoMailbox {
    private static final Pattern TO = Pattern.compile("to:([^\\s{}]+)");
    private static final Pattern ADDR = Pattern.compile("<([^>]+)>");

    private record Msg(String id, String threadId, String sender, List<String> to, String subject,
                       String body, List<String> labels, long at) {}

    private final List<Msg> messages = new ArrayList<>();
    private final AtomicLong seq = new AtomicLong(1000);
    private final Clock clock;

    public DemoMailbox(Clock clock) {
        this.clock = clock;
    }

    /** A customer sends an email (new thread, or a reply when threadId is given). */
    public synchronized Map<String, Object> customerSend(String fromName, String from, String to, String subject,
                                                         String body, String threadId) {
        requireAddr(from, "from");
        requireAddr(to, "to");
        if (threadId != null && thread(threadId).isEmpty()) throw new IllegalArgumentException("unknown thread");
        String tid = threadId != null ? threadId : "t" + seq.incrementAndGet();
        String sender = fromName == null || fromName.isBlank() ? from : fromName + " <" + from + ">";
        Msg m = new Msg("m" + seq.incrementAndGet(), tid, sender, List.of(to), nz(subject), nz(body), List.of("INBOX", "UNREAD"), clock.millis());
        messages.add(m);
        return gmail(m);
    }

    /** Gmail {@code reply}: answer in the thread of messageId, from the support address the mail was sent to. */
    public synchronized Map<String, Object> reply(String messageId, String body, List<String> to) {
        Msg orig = messages.stream().filter(m -> m.id.equals(messageId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown message"));
        String support = orig.labels.contains("SENT") ? email(orig.sender) : orig.to.getFirst();
        List<String> rcpt = to == null || to.isEmpty() ? List.of(email(orig.sender)) : List.copyOf(to);
        String subject = orig.subject.regionMatches(true, 0, "Re:", 0, 3) ? orig.subject : "Re: " + orig.subject;
        Msg m = new Msg("m" + seq.incrementAndGet(), orig.threadId, "PrintDesk Support <" + support + ">", rcpt, subject, nz(body),
                List.of("SENT"), clock.millis());
        messages.add(m);
        return gmail(m);
    }

    /** Gmail {@code send_message}: a new thread from the support account. */
    public synchronized Map<String, Object> send(List<String> to, String subject, String body, String from) {
        if (to == null || to.isEmpty()) throw new IllegalArgumentException("to is required");
        Msg m = new Msg("m" + seq.incrementAndGet(), "t" + seq.incrementAndGet(), "PrintDesk Support <" + from + ">", List.copyOf(to),
                nz(subject), nz(body), List.of("SENT"), clock.millis());
        messages.add(m);
        return gmail(m);
    }

    /** Gmail {@code search_threads}: supports the {@code to:} terms the UI uses; other terms are ignored. */
    public synchronized List<Map<String, Object>> searchThreads(String query) {
        List<String> to = new ArrayList<>();
        Matcher mt = TO.matcher(query == null ? "" : query);
        while (mt.find()) to.add(mt.group(1).toLowerCase(Locale.ROOT));
        List<Map<String, Object>> out = new ArrayList<>();
        for (String tid : threadIds()) {
            List<Msg> th = thread(tid);
            boolean hit = to.isEmpty() || th.stream().anyMatch(m -> m.to.stream().anyMatch(r -> to.contains(email(r))));
            if (hit) out.add(summary(tid, th));
        }
        return out;
    }

    /** Gmail {@code get_thread} with FULL_CONTENT. */
    public synchronized List<Map<String, Object>> getThread(String threadId) {
        List<Msg> th = thread(threadId);
        if (th.isEmpty()) throw new IllegalArgumentException("unknown thread");
        return th.stream().map(this::gmail).toList();
    }

    /** The customer's own mailbox: every thread the address took part in, with all messages. */
    public synchronized List<Map<String, Object>> mailboxOf(String addr) {
        String a = addr == null ? "" : addr.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> out = new ArrayList<>();
        for (String tid : threadIds()) {
            List<Msg> th = thread(tid);
            if (th.stream().anyMatch(m -> email(m.sender).equals(a) || m.to.stream().anyMatch(r -> email(r).equals(a)))) {
                Map<String, Object> s = summary(tid, th);
                s.put("messages", th.stream().map(this::gmail).toList());
                out.add(s);
            }
        }
        out.sort((x, y) -> Long.compare((long) y.get("lastAt"), (long) x.get("lastAt")));
        return out;
    }

    public synchronized void clear() {
        messages.clear();
    }

    // ------------------------------------------------------------------ helpers

    private List<String> threadIds() {
        return messages.stream().map(Msg::threadId).distinct().toList();
    }

    private List<Msg> thread(String id) {
        return messages.stream().filter(m -> m.threadId.equals(id)).toList();
    }

    private Map<String, Object> summary(String tid, List<Msg> th) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", tid);
        s.put("messageCount", th.size());
        s.put("subject", th.getFirst().subject);
        s.put("snippet", snippet(th.getLast().body));
        s.put("lastAt", th.getLast().at);
        return s;
    }

    private Map<String, Object> gmail(Msg m) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", m.id);
        o.put("threadId", m.threadId);
        o.put("sender", m.sender);
        o.put("toRecipients", m.to);
        o.put("subject", m.subject);
        o.put("plaintextBody", m.body);
        o.put("snippet", snippet(m.body));
        o.put("labelIds", m.labels);
        o.put("internalDate", String.valueOf(m.at));
        return o;
    }

    static String email(String s) {
        Matcher m = ADDR.matcher(s == null ? "" : s);
        return (m.find() ? m.group(1) : nz(s)).trim().toLowerCase(Locale.ROOT);
    }

    private static String snippet(String body) {
        String b = body.replaceAll("\\s+", " ").trim();
        return b.length() > 120 ? b.substring(0, 120) + "…" : b;
    }

    private static void requireAddr(String a, String field) {
        if (a == null || !a.matches("[^@\\s<>]+@[^@\\s<>]+")) throw new IllegalArgumentException(field + " must be an email address");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
