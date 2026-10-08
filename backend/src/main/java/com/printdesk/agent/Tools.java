package com.printdesk.agent;

import com.printdesk.catalog.Catalog;
import com.printdesk.catalog.Catalog.Order;
import com.printdesk.knowledge.Learning;
import com.printdesk.orders.OrderService;
import com.printdesk.parts.PartRanker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.printdesk.json.Records.str;

/**
 * The tools Claude may call while it works on a ticket. Each tool returns small, plain
 * data from the shop's own sources, so prices, part ids, compatibility and warranty
 * always come from the catalog, never from the model's memory.
 */
public final class Tools {

    public record Tool(String name, String description, Map<String, Object> inputSchema,
                       Function<Map<String, Object>, Object> run) {
        public Map<String, Object> definition() {
            return Map.of("name", name, "description", description, "input_schema", inputSchema);
        }
    }

    private final Catalog catalog;
    private final Learning learning;
    private final PartRanker ranker;
    private final OrderService orders;
    private final List<Tool> tools = new ArrayList<>();

    public Tools(Catalog catalog, Learning learning, PartRanker ranker, OrderService orders) {
        this.catalog = catalog;
        this.learning = learning;
        this.ranker = ranker;
        this.orders = orders;
        tools.add(new Tool("get_troubleshooting",
                "Returns the troubleshooting steps for one problem, ranked by how often each step actually fixed it in past cases "
                        + "(free checks first, then part replacements). Call it once you know the problem.",
                schema(Map.of("problem_id", prop("string", "Problem id from the PROBLEM INDEX, e.g. P01"),
                        "printer_id", prop("string", "Printer id, e.g. p1s; omit if unknown")), List.of("problem_id")),
                this::troubleshoot));
        tools.add(new Tool("find_parts",
                "Returns up to 4 compatible shop parts (3DJake catalog) in one category, best value first, with price, delivery days, "
                        + "stock and whether it is an original Bambu Lab part. Only recommend part ids returned here.",
                schema(Map.of("printer_id", prop("string", "Printer id"),
                        "category", Map.of("type", "string", "enum", List.of("hotend", "heater", "fan", "electronics", "extruder", "sensor", "cutter", "wear", "motion", "ptfe", "ams", "plate")),
                        "name_contains", prop("string", "Optional: part_name_contains from get_troubleshooting")), List.of("printer_id", "category")),
                this::findParts));
        tools.add(new Tool("lookup_order",
                "Looks up the customer's order by order number (preferred) or email. Returns date, items (printer model) and warranty end. "
                        + "Trust it over what the customer writes.",
                schema(Map.of("order_no", prop("string", "Order number"), "email", prop("string", "Customer email")), List.of()),
                this::lookupOrder));
        tools.add(new Tool("check_warranty",
                "Given a purchase date (YYYY-MM-DD), returns whether the printer is still under the EU warranty.",
                schema(Map.of("purchase_date", prop("string", "YYYY-MM-DD")), List.of("purchase_date")),
                in -> Map.of("warranty", orders.warrantyFromPurchase(str(in, "purchase_date")).name().toLowerCase())));
    }

    public List<Tool> all() { return tools; }

    public List<Map<String, Object>> definitions() { return tools.stream().map(Tool::definition).toList(); }

    public Object run(String name, Map<String, Object> input) {
        for (Tool t : tools) if (t.name().equals(name)) return t.run().apply(input);
        throw new IllegalArgumentException("Unknown tool " + name);
    }

    // ------------------------------------------------------------------ implementations

    Object troubleshoot(Map<String, Object> in) {
        String pid = str(in, "problem_id");
        String printer = str(in, "printer_id");
        if (catalog.problem(pid).isEmpty()) throw new IllegalArgumentException("Unknown problem id " + pid);
        if (printer != null && catalog.printer(printer).isEmpty()) printer = null;
        List<Map<String, Object>> steps = new ArrayList<>();
        for (Learning.StepStat s : learning.rankedSteps(pid, printer)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.step().id());
            m.put("text_en", s.step().en());
            m.put("fix_rate", s.rate() == null ? null : Math.round(s.rate() * 100) + "%");
            m.put("tried_in", s.reached());
            m.put("needs_part_category", s.step().partCat());
            m.put("part_name_contains", s.step().partHint());
            steps.add(m);
        }
        Learning.Stats st = learning.stepStats(pid, printer);
        return Map.of("problem_id", pid, "based_on_cases", st.cases(), "basis", st.basis(), "steps", steps);
    }

    Object findParts(Map<String, Object> in) {
        String printer = str(in, "printer_id");
        if (printer == null || catalog.printer(printer).isEmpty()) throw new IllegalArgumentException("Unknown printer id " + printer);
        List<Map<String, Object>> out = new ArrayList<>();
        for (PartRanker.Ranked r : ranker.rank(printer, str(in, "category"), str(in, "name_contains"), PartRanker.Priority.BALANCED)) {
            if (out.size() == 4) break;
            Catalog.Part p = r.part();
            out.add(Map.of("id", p.id(), "name", p.name(), "brand", p.brand(), "original_bambu", p.official(),
                    "price_eur", p.price(), "delivery_days", p.days(), "in_stock", p.stock()));
        }
        return out.isEmpty() ? Map.of("note", "No compatible part in this category.") : out;
    }

    Object lookupOrder(Map<String, Object> in) {
        return orders.find(str(in, "order_no"), str(in, "email")).<Object>map(this::orderView)
                .orElse(Map.of("note", "No order found."));
    }

    private Map<String, Object> orderView(Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("order_no", o.orderNo());
        m.put("customer", o.name());
        m.put("date", o.date().toString());
        m.put("items", o.items().stream().map(Catalog.OrderItem::name).toList());
        m.put("printer_id", o.printerId().orElse(null));
        m.put("warranty", orders.warranty(o).name().toLowerCase());
        m.put("warranty_until", o.warrantyUntil().toString());
        return m;
    }

    private static Map<String, Object> prop(String type, String desc) { return Map.of("type", type, "description", desc); }

    private static Map<String, Object> schema(Map<String, Object> props, List<String> required) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("properties", props);
        s.put("required", required);
        return s;
    }
}
