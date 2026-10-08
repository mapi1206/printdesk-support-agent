package com.printdesk;

import com.printdesk.agent.AnthropicClient;
import com.printdesk.agent.LlmClient;
import com.printdesk.agent.Tools;
import com.printdesk.agent.TriageAgent;
import com.printdesk.api.LocalStore;
import com.printdesk.api.Server;
import com.printdesk.catalog.Catalog;
import com.printdesk.knowledge.Learning;
import com.printdesk.orders.OrderService;
import com.printdesk.parts.PartRanker;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Starts PrintDesk locally:
 * <pre>
 * export ANTHROPIC_API_KEY=...        # optional – without it the UI runs in knowledge-base mode
 * java -jar backend/target/printdesk.jar   (run from the repository root)
 * open http://localhost:8080
 * </pre>
 * Env: PORT (8080), PRINTDESK_DATA (data/data.json), PRINTDESK_FRONTEND (frontend), PRINTDESK_DB (data/local-db.json).
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        Path data = Path.of(env("PRINTDESK_DATA", "data/data.json"));
        Path frontend = Path.of(env("PRINTDESK_FRONTEND", "frontend"));
        Path dbFile = Path.of(env("PRINTDESK_DB", "data/local-db.json"));
        int port = Integer.parseInt(env("PORT", "8080"));

        Clock clock = Clock.systemDefaultZone();
        Catalog catalog = Catalog.load(data);
        Learning learning = new Learning(catalog);
        PartRanker ranker = new PartRanker(catalog, learning);
        OrderService orders = new OrderService(catalog, clock);
        Tools tools = new Tools(catalog, learning, ranker, orders);

        String key = System.getenv("ANTHROPIC_API_KEY");
        LlmClient llm = key == null || key.isBlank() ? null : new AnthropicClient(key);
        TriageAgent agent = llm == null ? null : new TriageAgent(catalog, tools, orders, llm, clock);

        new Server(catalog, learning, ranker, agent, llm, new LocalStore(dbFile), frontend, data, clock).start(port);
        System.out.printf("PrintDesk running on http://localhost:%d  (catalog: %d parts, %d problems, %d cases; Claude: %s)%n",
                port, catalog.parts.size(), catalog.problems.size(), catalog.cases.size(), llm == null ? "off – set ANTHROPIC_API_KEY" : "on");
    }

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? def : v;
    }
}
