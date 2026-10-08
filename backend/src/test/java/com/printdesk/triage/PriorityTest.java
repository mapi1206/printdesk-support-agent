package com.printdesk.triage;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PriorityTest {
    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    private static Priority.Signals signals(boolean safety, String urgency, String sentiment) {
        return new Priority.Signals(safety, urgency, sentiment, false, null, "unknown", false, 0, null);
    }

    @Test
    void safetyAndUrgencyMakeP1WithTwoHourDeadline() {
        Priority.Score s = Priority.score(signals(true, "high", "neutral"), NOW, NOW);
        assertEquals(75, s.points());
        assertEquals(Priority.Level.P1, s.level());
        assertEquals(NOW.plus(Duration.ofHours(2)), s.due());
    }

    @Test
    void quietRequestIsP4() {
        Priority.Score s = Priority.score(signals(false, "low", "neutral"), NOW, NOW);
        assertEquals(0, s.points());
        assertEquals(Priority.Level.P4, s.level());
        assertTrue(s.reasons().isEmpty());
    }

    @Test
    void waitingTimeAddsTwoPointsPerHourCappedAtTwenty() {
        Priority.Score threeHours = Priority.score(signals(false, "low", "neutral"), NOW.minus(Duration.ofHours(3)), NOW);
        assertEquals(6, threeHours.points());
        Priority.Score twoDays = Priority.score(signals(false, "low", "neutral"), NOW.minus(Duration.ofHours(48)), NOW);
        assertEquals(20, twoDays.points());
        assertEquals(Priority.Level.P3, twoDays.level());
    }

    @Test
    void everyPointHasAReason() {
        Priority.Signals sig = new Priority.Signals(false, "medium", "frustrated", true, "Friday", "in", true, 10, "Péter");
        Priority.Score s = Priority.score(sig, NOW, NOW);
        assertEquals(10 + 8 + 10 + 10 + 5 + 8 + 10, s.points());
        assertEquals(s.points(), s.reasons().stream().mapToInt(Priority.Reason::points).sum());
        assertEquals(Priority.Level.P2, Priority.score(new Priority.Signals(false, "medium", "frustrated", true, "Friday", "in", false, 0, null), NOW, NOW).level());
    }
}
