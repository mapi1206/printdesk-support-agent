package com.printdesk.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EvalReportTest {

    @Test
    void scoresOnlyLabelledFieldsAndListsMisses() {
        var outcomes = List.of(
                new EvalRunner.Outcome("e1", Map.of("problem", "P01", "lang", "hu"), Map.of("problem", "P01", "lang", "hu")),
                new EvalRunner.Outcome("e2", Map.of("problem", "P05"), Map.of("problem", "P03", "lang", "de")),
                new EvalRunner.Outcome("e3", Map.of("is_support", false), Map.of("is_support", false)));
        String r = EvalRunner.report(outcomes);
        assertTrue(r.contains("problem       1/2     50.0%"), r);
        assertTrue(r.contains("lang          1/1    100.0%"), r);
        assertTrue(r.contains("is_support    1/1    100.0%"), r);
        assertTrue(r.contains("e2     problem    expected P05      got P03"), r);
        assertFalse(r.contains("printer"), r);
    }
}
