package com.printdesk.eval;

import com.printdesk.agent.AnthropicClient;
import com.printdesk.agent.LlmClient;
import com.printdesk.agent.Tools;
import com.printdesk.agent.TriageAgent;
import com.printdesk.catalog.Catalog;
import com.printdesk.json.Json;
import com.printdesk.knowledge.Learning;
import com.printdesk.orders.OrderService;
import com.printdesk.parts.PartRanker;
import com.printdesk.triage.PreFilter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.printdesk.json.Records.*;

/**
 * Measures the triage agent on labelled emails ({@code evals/emails.jsonl}).
 * Each line: {"id","from","subject","body","expect":{"is_support","lang","printer","problem","suspicious","escalate"}}.
 * Only the fields present in "expect" are scored. Prints per-field accuracy and every miss.
 *
 * <pre>java -cp backend/target/printdesk.jar com.printdesk.eval.EvalRunner evals/emails.jsonl</pre>
 */
public final class EvalRunner {

    public record Outcome(String id, Map<String, Object> expected, Map<String, Object> actual) {}

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args.length > 0 ? args[0] : "evals/emails.jsonl");
        Catalog catalog = Catalog.load(Path.of(System.getenv().getOrDefault("PRINTDESK_DATA", "data/data.json")));
        Clock clock = Clock.systemDefaultZone();
        Learning learning = new Learning(catalog);
        PartRanker ranker = new PartRanker(catalog, learning);
        OrderService orders = new OrderService(catalog, clock);
        LlmClient llm = new AnthropicClient(System.getenv("ANTHROPIC_API_KEY"));
        TriageAgent agent = new TriageAgent(catalog, new Tools(catalog, learning, ranker, orders), orders, llm, clock);

        List<Outcome> outcomes = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            Map<String, Object> c = Json.parseObject(line);
            Map<String, Object> actual = run(agent, c);
            outcomes.add(new Outcome(str(c, "id"), obj(c, "expect"), actual));
            System.out.print(".");
        }
        System.out.println();
        System.out.println(report(outcomes));
    }

    static Map<String, Object> run(TriageAgent agent, Map<String, Object> c) {
        Map<String, Object> a = new LinkedHashMap<>();
        if (PreFilter.classify(str(c, "from"), str(c, "subject"), str(c, "body")).isPresent()) {
            a.put("is_support", false);
            return a;
        }
        try {
            TriageAgent.Result r = agent.triage(new TriageAgent.Email(null, str(c, "from"), str(c, "subject"), str(c, "body")));
            a.put("is_support", r.isSupport());
            a.put("lang", r.lang());
            a.put("printer", r.printerId());
            a.put("problem", r.problemId());
            a.put("suspicious", r.suspicious());
            a.put("escalate", r.escalate() || r.safety());
        } catch (Exception e) {
            a.put("error", e.getMessage());
        }
        return a;
    }

    /** Per-field accuracy over the cases that label that field. */
    public static String report(List<Outcome> outcomes) {
        String[] fields = {"is_support", "lang", "printer", "problem", "suspicious", "escalate"};
        StringBuilder sb = new StringBuilder("PrintDesk triage eval – " + outcomes.size() + " emails\n\n");
        List<String> misses = new ArrayList<>();
        for (String f : fields) {
            int n = 0, ok = 0;
            for (Outcome o : outcomes) {
                if (!o.expected().containsKey(f)) continue;
                n++;
                Object exp = o.expected().get(f), act = o.actual().get(f);
                if (Objects.equals(String.valueOf(exp), String.valueOf(act))) ok++;
                else misses.add(String.format("  %-6s %-10s expected %-8s got %s", o.id(), f, exp, act));
            }
            if (n > 0) sb.append(String.format("%-11s %3d/%-3d  %5.1f%%%n", f, ok, n, 100.0 * ok / n));
        }
        if (!misses.isEmpty()) sb.append("\nMisses:\n").append(String.join("\n", misses)).append('\n');
        return sb.toString();
    }
}
