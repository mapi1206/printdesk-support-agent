package com.printdesk.triage;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Cheap, deterministic filter that runs before the agent: bounces, auto-replies,
 * no-reply senders and newsletters are set aside without spending a model call.
 * A message that clearly talks about a printer or an order is never filtered.
 */
public final class PreFilter {
    private PreFilter() {}

    public enum Reason { BOUNCE, AUTO_REPLY, NO_REPLY_SENDER, NEWSLETTER }

    private static final Pattern BOUNCE_FROM = Pattern.compile("^(mailer-daemon|postmaster)@");
    private static final Pattern BOUNCE_SUBJ = Pattern.compile("(delivery status notification|undeliverable|unzustellbar|nem kézbesíthető|mail delivery (failed|subsystem))", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTO_SUBJ = Pattern.compile("^(auto|automatic) ?(reply|response)|out of office|abwesenheit|automatische antwort|házon kívül|automatikus válasz|absence", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOREPLY_FROM = Pattern.compile("(^|[._-])(no-?reply|noreply|do-?not-?reply|notifications?|newsletter|marketing|news)@");
    private static final Pattern NEWSLETTER_BODY = Pattern.compile("(unsubscribe|abmelden|abbestellen|leiratkozás|désinscri|view (this|in) (email|browser))", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUPPORTISH = Pattern.compile("(printer|drucker|nyomtató|imprimante|drukark|bambu|\\b(a1|a1 mini|p1s|p1p|p2s|x1c?|x2d|h2d|a2l|ams)\\b|nozzle|düse|fúvóka|hotend|extru|bestellnummer|bestellung|order (no|number|#)|rendelésszám)", Pattern.CASE_INSENSITIVE);

    public static Optional<Reason> classify(String from, String subject, String body) {
        String f = from == null ? "" : from.toLowerCase(Locale.ROOT).trim();
        String s = subject == null ? "" : subject;
        String b = body == null ? "" : body;
        if (BOUNCE_FROM.matcher(f).find() || BOUNCE_SUBJ.matcher(s).find()) return Optional.of(Reason.BOUNCE);
        if (AUTO_SUBJ.matcher(s).find()) return Optional.of(Reason.AUTO_REPLY);
        boolean supportish = SUPPORTISH.matcher(s + " " + b).find();
        if (NOREPLY_FROM.matcher(f).find() && !supportish) return Optional.of(Reason.NO_REPLY_SENDER);
        if (NEWSLETTER_BODY.matcher(b).find() && !supportish) return Optional.of(Reason.NEWSLETTER);
        return Optional.empty();
    }
}
