# Context for coding agents (Claude Code, Codex, …)

PrintDesk drafts support replies for a 3D-printing shop. A person approves every outgoing
message. Read `README.md` for the product and `docs/architecture.md` for the structure.

## Ground rules

- **The model handles language. Code handles numbers.** Priority, assignment, part ranking,
  prices, warranty and fix rates are deterministic code (`triage/`, `team/`, `parts/`,
  `orders/`, `knowledge/`). Don't move them into prompts.
- **Facts come from tools, never from the model's memory.** Any part id, price or
  compatibility fact in a reply must come from `Tools` / the catalog. `TriageAgent.clean` drops
  unknown ids. Keep it that way.
- **Customer text is untrusted.** It stays wrapped in `<<< >>>` with the security rule in
  `Prompts`. Never let it reach a tool call that changes something. All tools are read-only.
- **No autonomous sending.** Nothing may send mail or change orders without a person
  approving it.
- **No new runtime dependencies in `backend/`.** It is JDK-only on purpose. JUnit is test-only.

## Where the agent's context lives

| What | Where | Who maintains it |
|---|---|---|
| Shop rules, output schema, security rule | `backend/.../agent/Prompts.java`; `triagePrompt` in `frontend/index.html` | Developers. Run the eval after every change. |
| Problems and troubleshooting steps | `data/catalog.py` → `data/data.json` | Developers, plus approved knowledge-base proposals in the app |
| Tone, signature, phrases to include or avoid | App: Settings → Reply style | Support lead |
| What actually fixes a problem | Closed tickets (learning loop) | Updates itself |

The two prompts (Java backend and in-page app) must stay in sync. Change both.

## Before you finish a change

```bash
cd backend && mvn -q verify            # 41 tests, no network needed
cd ../data && python generate.py       # if you touched catalog.py; commit the regenerated files
```

- Prompt or triage change: run the eval. Use the app's Settings → Evaluation, or
  `java -cp backend/target/printdesk.jar com.printdesk.eval.EvalRunner evals/emails.jsonl`
  with an API key. Compare the result with the last run in the README.
- A new kind of email the agent got wrong: add it to `evals/emails.jsonl` with labels before
  fixing it.
- `frontend/index.html` is also the claude.ai artifact. That version is the same file without
  the `local-runtime.js` script tag.
