package com.printdesk;

import com.printdesk.catalog.Catalog;
import com.printdesk.catalog.Catalog.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Small, hand-built catalog so tests are exact and independent of the generated data. */
public final class TestData {
    private TestData() {}

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC);

    public static Catalog catalog() {
        List<Printer> printers = List.of(new Printer("p1s", "P1S", true), new Printer("a1", "A1", false));
        List<Part> parts = List.of(
                new Part("BL-HE-P04H", "Hotend - P1 Series, 0.4 mm hardened steel", "Bambu Lab", "hotend", 16.49, null, 3, 4, 10, List.of("p1s"), true),
                new Part("PH-CONCH", "Conch Hotend M6, 0.4 mm (X1 / P1)", "Phaetus", "hotend", 21.99, null, 3, 40, 10, List.of("p1s"), false),
                new Part("MS-FT", "FlowTech Hotend, CHT nozzle (P1P / P1S)", "Micro-Swiss", "hotend", 85.49, 94.99, 3, 0, 5, List.of("p1s"), false),
                new Part("BL-HE-A04H", "Hotend - A1/A2 Series, 0.4 mm hardened steel", "Bambu Lab", "hotend", 12.99, null, 3, 6, 10, List.of("a1"), true),
                new Part("BL-AMS-FEED", "AMS Feeder Unit (AMS 2 Pro)", "Bambu Lab", "ams", 49.99, null, 3, 2, 5, List.of("p1s"), true));
        Problem clog = new Problem("P01", "extrusion", List.of("p1s", "a1"), "Nozzle clog", "Düse verstopft", "clog",
                List.of(new Step("P01-S1", "Check the spool", "Spule prüfen", null, null),
                        new Step("P01-S2", "Do a cold pull", "Cold Pull", null, null),
                        new Step("P01-S3", "Replace the hotend", "Hotend tauschen", "hotend", null)));
        List<CaseRecord> cases = new ArrayList<>();
        LocalDate d = LocalDate.of(2026, 9, 1);
        // 10 cases: step 2 (cold pull) solved 6 of the 8 cases that reached it; the hotend solved 2 of 2.
        for (int i = 0; i < 6; i++) cases.add(new CaseRecord("P01", "a1", 2, 1, true, null, d));
        cases.add(new CaseRecord("P01", "a1", 3, 2, true, "BL-HE-A04H", d));
        cases.add(new CaseRecord("P01", "a1", 3, 2, true, "BL-HE-A04H", d));
        cases.add(new CaseRecord("P01", "a1", 1, 0, true, null, d));
        cases.add(new CaseRecord("P01", "a1", 1, 0, true, null, d));
        List<Order> orders = List.of(
                new Order("3DJ-2024-077315", "James Carter", "james@example.com", LocalDate.of(2024, 3, 12), LocalDate.of(2026, 3, 12),
                        List.of(new OrderItem("printer", "p1s", "Bambu Lab P1S", 379))),
                new Order("3DJ-2026-048213", "Szabó Péter", "peter@example.com", LocalDate.of(2026, 4, 8), LocalDate.of(2028, 4, 8),
                        List.of(new OrderItem("printer", "p1s", "Bambu Lab P1S Combo", 529))));
        return new Catalog(49.90, 4.99, 24, printers, parts, List.of(clog), cases, orders);
    }
}
