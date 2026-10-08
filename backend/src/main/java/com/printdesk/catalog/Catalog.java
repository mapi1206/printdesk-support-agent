package com.printdesk.catalog;

import com.printdesk.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.printdesk.json.Records.*;

/**
 * Read-only shop data: printers, the 3DJake parts catalog, the troubleshooting
 * knowledge base, simulated order history and the case history the agent learns from.
 * Loaded from {@code data/data.json} (produced by {@code data/generate.py}).
 */
public final class Catalog {

    public record Printer(String id, String name, boolean enclosed) {}

    public record Part(String id, String name, String brand, String cat, double price, Double was,
                       int days, int stock, int reorderLevel, List<String> compat, boolean official) {
        public boolean fits(String printerId) { return printerId == null || compat.contains(printerId); }
    }

    public record Step(String id, String en, String de, String partCat, String partHint) {
        public boolean needsPart() { return partCat != null; }
    }

    public record Problem(String id, String cat, List<String> printers, String en, String de, String kw, List<Step> steps) {
        public int stepIndex(String stepId) {
            for (int i = 0; i < steps.size(); i++) if (steps.get(i).id().equals(stepId)) return i;
            return -1;
        }
    }

    /** One historical support case. {@code solvedStep} is the index of the step that fixed it, or null. */
    public record CaseRecord(String problem, String printer, int stepsTried, Integer solvedStep, boolean resolved,
                             String partId, LocalDate date) {}

    public record OrderItem(String type, String id, String name, double price) {}

    public record Order(String orderNo, String name, String email, LocalDate date, LocalDate warrantyUntil, List<OrderItem> items) {
        public boolean inWarranty(LocalDate today) { return !today.isAfter(warrantyUntil); }
        public Optional<String> printerId() {
            return items.stream().filter(i -> "printer".equals(i.type())).map(OrderItem::id).findFirst();
        }
    }

    public final double freeShippingFrom;
    public final double standardShipping;
    public final int warrantyMonths;
    public final List<Printer> printers;
    public final List<Part> parts;
    public final List<Problem> problems;
    public final List<CaseRecord> cases;
    public final List<Order> orders;

    public Catalog(double freeShippingFrom, double standardShipping, int warrantyMonths, List<Printer> printers,
                   List<Part> parts, List<Problem> problems, List<CaseRecord> cases, List<Order> orders) {
        this.freeShippingFrom = freeShippingFrom;
        this.standardShipping = standardShipping;
        this.warrantyMonths = warrantyMonths;
        this.printers = List.copyOf(printers);
        this.parts = List.copyOf(parts);
        this.problems = List.copyOf(problems);
        this.cases = new ArrayList<>(cases);
        this.orders = List.copyOf(orders);
    }

    public Optional<Printer> printer(String id) { return printers.stream().filter(p -> p.id().equals(id)).findFirst(); }
    public Optional<Part> part(String id) { return parts.stream().filter(p -> p.id().equals(id)).findFirst(); }
    public Optional<Problem> problem(String id) { return problems.stream().filter(p -> p.id().equals(id)).findFirst(); }

    public Optional<Step> step(String stepId) {
        if (stepId == null) return Optional.empty();
        int cut = stepId.indexOf("-S");
        if (cut < 0) return Optional.empty();
        return problem(stepId.substring(0, cut)).flatMap(p -> p.steps().stream().filter(s -> s.id().equals(stepId)).findFirst());
    }

    /** Adds a resolved case to the history so rankings learn from it immediately. */
    public synchronized void record(CaseRecord c) { cases.add(c); }

    // ------------------------------------------------------------------ loading

    public static Catalog load(Path file) throws IOException {
        return fromJson(Json.parseObject(Files.readString(file, StandardCharsets.UTF_8)));
    }

    @SuppressWarnings("unchecked")
    public static Catalog fromJson(Map<String, Object> d) {
        Map<String, Object> meta = obj(d, "meta");
        List<Printer> printers = new ArrayList<>();
        for (Object o : list(d, "printers")) {
            Map<String, Object> m = (Map<String, Object>) o;
            printers.add(new Printer(str(m, "id"), str(m, "name"), bool(m, "enclosed", false)));
        }
        List<Part> parts = new ArrayList<>();
        for (Object o : list(d, "parts")) {
            Map<String, Object> m = (Map<String, Object>) o;
            Object was = m.get("was");
            parts.add(new Part(str(m, "id"), str(m, "name"), str(m, "brand"), str(m, "cat"), num(m, "price", 0),
                    was instanceof Number n ? n.doubleValue() : null, (int) num(m, "days", 3), (int) num(m, "stock", 0),
                    (int) num(m, "reorder_level", 5), strings(list(m, "compat")), bool(m, "official", false)));
        }
        List<Problem> problems = new ArrayList<>();
        for (Object o : list(d, "problems")) {
            Map<String, Object> m = (Map<String, Object>) o;
            List<Step> steps = new ArrayList<>();
            for (Object so : list(m, "steps")) {
                Map<String, Object> s = (Map<String, Object>) so;
                steps.add(new Step(str(s, "id"), str(s, "en"), str(s, "de"), str(s, "part_cat"), str(s, "part_hint")));
            }
            problems.add(new Problem(str(m, "id"), str(m, "cat"), strings(list(m, "printers")), str(m, "en"), str(m, "de"), str(m, "kw", ""), steps));
        }
        List<CaseRecord> cases = new ArrayList<>();
        for (Object o : list(d, "cases")) {
            Map<String, Object> m = (Map<String, Object>) o;
            Object solved = m.get("solved_step");
            cases.add(new CaseRecord(str(m, "problem"), str(m, "printer"), (int) num(m, "steps_tried", 0),
                    solved instanceof Number n ? n.intValue() : null, bool(m, "resolved", false), str(m, "part_id"),
                    LocalDate.parse(str(m, "date", "2026-01-01"))));
        }
        List<Order> orders = new ArrayList<>();
        for (Object o : list(d, "orders")) {
            Map<String, Object> m = (Map<String, Object>) o;
            List<OrderItem> items = new ArrayList<>();
            for (Object io : list(m, "items")) {
                Map<String, Object> it = (Map<String, Object>) io;
                items.add(new OrderItem(str(it, "type"), str(it, "id"), str(it, "name"), num(it, "price", 0)));
            }
            orders.add(new Order(str(m, "order_no"), str(m, "name"), str(m, "email"), LocalDate.parse(str(m, "date")),
                    LocalDate.parse(str(m, "warranty_until")), items));
        }
        return new Catalog(num(meta, "free_shipping_from", 49.90), num(meta, "std_shipping", 4.99), (int) num(meta, "warranty_months", 24),
                printers, parts, problems, cases, orders);
    }

    private static List<String> strings(List<Object> l) {
        List<String> out = new ArrayList<>();
        for (Object o : l) if (o != null) out.add(String.valueOf(o));
        return out;
    }
}
