package com.printdesk.team;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssignerTest {
    private static final Assigner.Member PETER = new Assigner.Member("m1", "Péter", List.of("hu", "en"), true, true);
    private static final Assigner.Member ANNA = new Assigner.Member("m2", "Anna", List.of("de", "en"), false, true);

    @Test
    void colleagueBelowTheMinimumGetsTheTicket() {
        Assigner a = new Assigner(5);
        var loads = List.of(new Assigner.Load(PETER, 5, 1), new Assigner.Load(ANNA, 1, 1));
        var pick = a.pick(loads, "hu", null).orElseThrow();
        assertEquals("m2", pick.memberId());   // language bonus does not beat a 4-ticket deficit
        assertTrue(pick.reason().contains("below daily minimum (1/5)"));
    }

    @Test
    void languageDecidesBetweenEqualLoads() {
        Assigner a = new Assigner(5);
        var loads = List.of(new Assigner.Load(PETER, 2, 2), new Assigner.Load(ANNA, 2, 2));
        assertEquals("m2", a.pick(loads, "de", null).orElseThrow().memberId());
        assertEquals("m1", a.pick(loads, "hu", null).orElseThrow().memberId());
    }

    @Test
    void openTicketsSpreadTheWork() {
        Assigner a = new Assigner(0);
        var loads = List.of(new Assigner.Load(PETER, 0, 6), new Assigner.Load(ANNA, 0, 1));
        assertEquals("m2", a.pick(loads, "hu", null).orElseThrow().memberId());
    }

    @Test
    void personalMailboxAlwaysGoesToItsOwner() {
        Assigner a = new Assigner(5);
        var loads = List.of(new Assigner.Load(PETER, 9, 9), new Assigner.Load(ANNA, 0, 0));
        var pick = a.pick(loads, "de", "péter").orElseThrow();
        assertEquals("m1", pick.memberId());
        assertTrue(pick.reason().startsWith("personal mailbox"));
    }

    @Test
    void inactiveColleaguesAreSkipped() {
        Assigner a = new Assigner(5);
        var away = new Assigner.Member("m3", "Away", List.of("hu"), false, false);
        assertTrue(a.pick(List.of(new Assigner.Load(away, 0, 0)), "hu", null).isEmpty());
    }
}
