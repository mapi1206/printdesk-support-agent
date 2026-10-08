package com.printdesk.api;

import com.printdesk.TestData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DemoMailboxTest {
    private static final String SUPPORT = "support@printdesk-demo.example";

    @Test
    void customerMailIsFoundByTheSupportAddressQuery() {
        DemoMailbox box = new DemoMailbox(TestData.CLOCK);
        box.customerSend("Szabó Péter", "szabo.peter@example.com", SUPPORT, "AMS", "slot 2 does not feed", null);
        box.customerSend("Other", "x@example.com", "sales@example.com", "Offer", "hi", null);

        List<Map<String, Object>> hits = box.searchThreads("to:" + SUPPORT + " newer_than:14d");
        assertEquals(1, hits.size());
        Map<String, Object> msg = box.getThread((String) hits.getFirst().get("id")).getFirst();
        assertEquals("Szabó Péter <szabo.peter@example.com>", msg.get("sender"));
        assertEquals(List.of(SUPPORT), msg.get("toRecipients"));
        assertEquals("slot 2 does not feed", msg.get("plaintextBody"));
        assertTrue(((List<?>) msg.get("labelIds")).contains("INBOX"));
    }

    @Test
    void replyStaysInTheThreadAndTheCustomerAnswerContinuesIt() {
        DemoMailbox box = new DemoMailbox(TestData.CLOCK);
        Map<String, Object> first = box.customerSend("Péter", "peter@example.com", SUPPORT, "AMS", "help", null);
        String thread = (String) first.get("threadId");

        Map<String, Object> reply = box.reply((String) first.get("id"), "try this", List.of("peter@example.com"));
        assertEquals(thread, reply.get("threadId"));
        assertEquals("Re: AMS", reply.get("subject"));
        assertEquals("PrintDesk Support <" + SUPPORT + ">", reply.get("sender"));
        assertEquals(List.of("SENT"), reply.get("labelIds"));

        box.customerSend("Péter", "peter@example.com", SUPPORT, "Re: AMS", "works now", thread);
        assertEquals(3, box.getThread(thread).size());
        assertEquals(3, box.searchThreads("to:" + SUPPORT).getFirst().get("messageCount"));

        List<Map<String, Object>> mine = box.mailboxOf("peter@example.com");
        assertEquals(1, mine.size());
        assertEquals(3, ((List<?>) mine.getFirst().get("messages")).size());
    }

    @Test
    void rejectsBadInput() {
        DemoMailbox box = new DemoMailbox(TestData.CLOCK);
        assertThrows(IllegalArgumentException.class, () -> box.customerSend("", "not-an-address", SUPPORT, "s", "b", null));
        assertThrows(IllegalArgumentException.class, () -> box.customerSend("", "a@example.com", SUPPORT, "s", "b", "t999"));
        assertThrows(IllegalArgumentException.class, () -> box.reply("m404", "b", List.of()));
    }
}
