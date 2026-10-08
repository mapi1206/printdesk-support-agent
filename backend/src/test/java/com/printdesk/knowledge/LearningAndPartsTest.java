package com.printdesk.knowledge;

import com.printdesk.TestData;
import com.printdesk.catalog.Catalog;
import com.printdesk.orders.OrderService;
import com.printdesk.parts.PartRanker;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LearningAndPartsTest {

    @Test
    void fixRateIsSolvedOverReached() {
        Learning l = new Learning(TestData.catalog());
        var st = l.stepStats("P01", null);
        assertEquals(10, st.cases());
        var coldPull = st.steps().get(1);
        assertEquals(8, coldPull.reached());     // 6 solved at step 2 + 2 that went on to step 3
        assertEquals(6, coldPull.fixed());
        assertEquals(0.75, coldPull.rate(), 1e-9);
        assertEquals(1.0, st.steps().get(2).rate(), 1e-9);
    }

    @Test
    void freeChecksComeBeforePartReplacementsEvenIfThePartWorksBetter() {
        Learning l = new Learning(TestData.catalog());
        List<String> order = l.rankedSteps("P01", null).stream().map(s -> s.step().id()).toList();
        assertEquals(List.of("P01-S2", "P01-S1", "P01-S3"), order);
    }

    @Test
    void newOutcomesChangeTheRanking() {
        Catalog c = TestData.catalog();
        Learning l = new Learning(c);
        for (int i = 0; i < 40; i++) c.record(new Catalog.CaseRecord("P01", "a1", 1, 0, true, null, LocalDate.of(2026, 10, 1)));
        assertEquals("P01-S1", l.rankedSteps("P01", null).getFirst().step().id());
    }

    @Test
    void printerSpecificStatsNeedEnoughCases() {
        Learning l = new Learning(TestData.catalog());
        assertEquals("A1", l.stepStats("P01", "a1").basis());           // 10 A1 cases
        assertEquals("all printers", l.stepStats("P01", "p1s").basis()); // no P1S cases
    }

    @Test
    void bestOfferPrefersCheapInStockOriginal() {
        Catalog c = TestData.catalog();
        PartRanker r = new PartRanker(c, new Learning(c));
        List<String> ids = r.rank("p1s", "hotend", null, PartRanker.Priority.BALANCED).stream().map(x -> x.part().id()).toList();
        assertEquals(List.of("BL-HE-P04H", "PH-CONCH", "MS-FT"), ids);
        assertEquals(16.49 + 4.99, r.totalCost(c.part("BL-HE-P04H").orElseThrow()), 1e-9);
        assertEquals(85.49, r.totalCost(c.part("MS-FT").orElseThrow()), 1e-9);   // free shipping above 49.90
    }

    @Test
    void incompatiblePartsAreNeverOffered() {
        Catalog c = TestData.catalog();
        PartRanker r = new PartRanker(c, new Learning(c));
        assertTrue(r.rank("a1", "hotend", null, PartRanker.Priority.BALANCED).stream().allMatch(x -> x.part().compat().contains("a1")));
        assertEquals(List.of("BL-HE-P04H"), r.rank("p1s", "hotend", null, PartRanker.Priority.ORIGINAL_ONLY).stream().map(x -> x.part().id()).toList());
    }

    @Test
    void orderLookupAndWarranty() {
        OrderService o = new OrderService(TestData.catalog(), TestData.CLOCK);
        var expired = o.find("3dj-2024-077315", null).orElseThrow();
        assertEquals(OrderService.Warranty.OUT, o.warranty(expired));
        var byEmail = o.find(null, "PETER@example.com").orElseThrow();
        assertEquals(OrderService.Warranty.IN, o.warranty(byEmail));
        assertEquals(OrderService.Warranty.UNKNOWN, o.warrantyFromPurchase("sometime"));
        assertEquals(OrderService.Warranty.IN, o.warrantyFromPurchase("2025-06-01"));
    }
}
