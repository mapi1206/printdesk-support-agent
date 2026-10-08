package com.printdesk.triage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Explainable priority: every point has a visible reason, so a colleague can see
 * why a ticket is P1 instead of trusting a black box.
 *
 * <pre>
 * safety +50 · urgency high +25 / medium +10 · angry +15 / frustrated +8 · printer down +10
 * deadline +10 · in warranty +5 · repeat contact +8 · personal mailbox +boost · waiting +2/h (max 20)
 * P1 ≥ 60 · P2 ≥ 40 · P3 ≥ 20 · P4 below — reply due after 2 / 8 / 24 / 48 hours
 * </pre>
 */
public final class Priority {
    private Priority() {}

    public enum Level {
        P1(2), P2(8), P3(24), P4(48);
        public final int slaHours;
        Level(int h) { slaHours = h; }
    }

    public record Reason(String key, int points, String detail) {}

    public record Score(int points, Level level, List<Reason> reasons, Instant due) {}

    /** Signals extracted by the agent (plus a few facts the desk knows itself). */
    public record Signals(boolean safety, String urgency, String sentiment, boolean printerDown, String deadline,
                          String warranty, boolean repeatContact, int personalBoost, String personalLabel) {}

    public static Score score(Signals s, Instant lastCustomerMessage, Instant now) {
        List<Reason> r = new ArrayList<>();
        if (s.safety()) r.add(new Reason("safety", 50, null));
        if ("high".equals(s.urgency())) r.add(new Reason("urgent", 25, null));
        else if ("medium".equals(s.urgency())) r.add(new Reason("time_sensitive", 10, null));
        if ("angry".equals(s.sentiment())) r.add(new Reason("angry", 15, null));
        else if ("frustrated".equals(s.sentiment())) r.add(new Reason("frustrated", 8, null));
        if (s.printerDown()) r.add(new Reason("printer_down", 10, null));
        if (s.deadline() != null && !s.deadline().isBlank()) r.add(new Reason("deadline", 10, s.deadline()));
        if ("in".equals(s.warranty())) r.add(new Reason("in_warranty", 5, null));
        if (s.repeatContact()) r.add(new Reason("repeat_contact", 8, null));
        if (s.personalBoost() > 0) r.add(new Reason("personal_mailbox", s.personalBoost(), s.personalLabel()));
        double hours = Math.max(0, Duration.between(lastCustomerMessage, now).toMinutes() / 60.0);
        int waiting = (int) Math.min(20, Math.floor(hours * 2));
        if (waiting > 0) r.add(new Reason("waiting", waiting, hours < 1 ? "<1 h" : Math.round(hours) + " h"));

        int pts = r.stream().mapToInt(Reason::points).sum();
        Level level = pts >= 60 ? Level.P1 : pts >= 40 ? Level.P2 : pts >= 20 ? Level.P3 : Level.P4;
        return new Score(pts, level, List.copyOf(r), lastCustomerMessage.plus(Duration.ofHours(level.slaHours)));
    }
}
