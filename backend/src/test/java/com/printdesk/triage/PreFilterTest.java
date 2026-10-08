package com.printdesk.triage;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PreFilterTest {

    @Test
    void bouncesAndAutoRepliesAreSetAside() {
        assertEquals(Optional.of(PreFilter.Reason.BOUNCE), PreFilter.classify("mailer-daemon@googlemail.com", "Delivery Status Notification (Failure)", "..."));
        assertEquals(Optional.of(PreFilter.Reason.AUTO_REPLY), PreFilter.classify("anna@example.com", "Abwesenheitsnotiz: bis 12.10.", "Ich bin nicht im Büro."));
        assertEquals(Optional.of(PreFilter.Reason.AUTO_REPLY), PreFilter.classify("anna@example.com", "Out of office", "Back Monday."));
    }

    @Test
    void newslettersWithoutPrinterTalkAreSetAside() {
        assertEquals(Optional.of(PreFilter.Reason.NO_REPLY_SENDER), PreFilter.classify("newsletter@filament.example", "Herbst-Sale: 30% auf PLA", "Newsletter abbestellen"));
        assertEquals(Optional.of(PreFilter.Reason.NEWSLETTER), PreFilter.classify("shop@example.com", "Neue Farben", "Jetzt kaufen! Unsubscribe here."));
    }

    @Test
    void realSupportMailIsNeverFiltered() {
        assertTrue(PreFilter.classify("piotr@example.com", "P1S – dysza", "Mój P1S ma zatkaną dyszę").isEmpty());
        // A notification sender that talks about the customer's printer still goes to the agent.
        assertTrue(PreFilter.classify("notifications@shop.example", "Order 3DJ-1", "Your P1S nozzle question, order number 3DJ-1").isEmpty());
        // "abbestellen" must not count as a support keyword ("bestell…").
        assertTrue(PreFilter.classify("news@x.example", "Sale", "abbestellen").isPresent());
    }
}
