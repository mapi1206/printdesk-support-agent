package com.printdesk.team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Distributes tickets so every colleague reaches the daily minimum.
 * <pre>score = 10 × (minimum − handled today, if positive) − 4 × open tickets + 6 if they speak the customer's language (−2 if they list languages but not this one)</pre>
 * A ticket that arrived at a colleague's personal mailbox always goes to that colleague.
 */
public final class Assigner {

    public record Member(String id, String name, List<String> languages, boolean lead, boolean active) {}

    /** How much a member has on their plate today. */
    public record Load(Member member, int handledToday, int open) {}

    public record Assignment(String memberId, String reason) {}

    private final int dailyMinimum;

    public Assigner(int dailyMinimum) { this.dailyMinimum = dailyMinimum; }

    public Optional<Assignment> pick(List<Load> loads, String customerLanguage, String personalMailboxOwner) {
        List<Load> active = loads.stream().filter(l -> l.member().active()).toList();
        if (active.isEmpty()) return Optional.empty();
        if (personalMailboxOwner != null && !personalMailboxOwner.isBlank()) {
            String owner = personalMailboxOwner.trim().toLowerCase(Locale.ROOT);
            Optional<Load> o = active.stream().filter(l -> l.member().name().trim().toLowerCase(Locale.ROOT).equals(owner)).findFirst();
            if (o.isPresent()) return Optional.of(new Assignment(o.get().member().id(), "personal mailbox of " + o.get().member().name()));
        }
        Load best = active.stream()
                .sorted(Comparator.comparingInt((Load l) -> score(l, customerLanguage)).reversed().thenComparingInt(Load::open))
                .findFirst().orElseThrow();
        List<String> why = new ArrayList<>();
        int deficit = deficit(best);
        if (deficit > 0) why.add("below daily minimum (" + best.handledToday() + "/" + dailyMinimum + ")");
        why.add(best.open() + " open");
        if (speaks(best, customerLanguage)) why.add("speaks " + customerLanguage.toUpperCase(Locale.ROOT));
        return Optional.of(new Assignment(best.member().id(), String.join(" · ", why)));
    }

    int deficit(Load l) { return Math.max(0, dailyMinimum - l.handledToday()); }

    static boolean speaks(Load l, String lang) {
        return lang != null && l.member().languages() != null && l.member().languages().contains(lang.toLowerCase(Locale.ROOT));
    }

    int score(Load l, String lang) {
        int languageBonus = speaks(l, lang) ? 6 : (l.member().languages() == null || l.member().languages().isEmpty() ? 0 : -2);
        return deficit(l) * 10 - l.open() * 4 + languageBonus;
    }
}
