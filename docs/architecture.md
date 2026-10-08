# Architecture

## Components

| Component | Where | Responsibility |
|---|---|---|
| UI | `frontend/index.html` | Customer chat, support inbox, phone quick mode, parts & offers, knowledge base, insights, settings |
| Runtime adapter | `frontend/local-runtime.js` | Gives the UI the same `window.claude` surface (`sample`, `db`) on top of the Java API when run locally |
| Triage agent | `backend/.../agent` | Claude Messages API tool-use loop, prompts, tools, output validation |
| Domain logic | `backend/.../triage`, `knowledge`, `parts`, `orders`, `team` | Priority, pre-filter, injection guard, learning loop, best offer, warranty, assignment |
| API | `backend/.../api` | REST endpoints, static files, JSON document store |
| Data | `data/` | 3DJake catalog snapshot, knowledge base, simulated history and orders |

## Ticket lifecycle

```mermaid
stateDiagram-v2
  [*] --> new: email / chat escalation / unsolved call
  new --> filtered: pre-filter or agent says not support
  filtered --> new: colleague: "this is support"
  new --> triaging: agent picks it up (lease, one worker)
  triaging --> ready: triage + draft saved, auto-assigned
  triaging --> error: model or tool failure
  error --> new: retry
  ready --> approved: colleague approves (after optional edits / rewrite)
  approved --> ready: edit again
  approved --> sent: reply sent in the Gmail thread
  sent --> new: customer writes back (follow-up, full history)
  sent --> closed: Close ticket (outcome recorded → learning loop)
  ready --> closed: customer confirms fix / close without reply
  closed --> new: customer writes back
  closed --> [*]: anonymised after retention period
```

## One triage, step by step

```mermaid
sequenceDiagram
  participant W as Worker / UI
  participant A as TriageAgent
  participant C as Claude API
  participant T as Tools
  W->>A: Email(from, subject, body)
  A->>C: system rules + problem index + schema, user: <<<email>>>, tools
  C-->>A: tool_use lookup_order, get_troubleshooting
  A->>T: run tools (orders, ranked steps with fix rates)
  T-->>A: results
  A->>C: tool_result blocks
  C-->>A: tool_use find_parts(p1s, ams, "feeder")
  A->>T: best-offer ranking
  A->>C: tool_result
  C-->>A: final JSON (lang, printer, problem, steps, parts, reply…)
  A->>A: validate ids · order facts win · links · injection scan
  A-->>W: Result + Priority (points, reasons, due) → assignment
```

## Learning loop

Every closed ticket stores which step solved it (or a free-text fix). For each step:

```
reached = cases where the step was tried or solved
fixed   = cases solved by the step
rate    = fixed / reached
```

`get_troubleshooting` returns free checks first and part replacements last, each group by rate.
Printer-specific stats are used once a printer has at least 8 cases for the problem. Free-text
fixes show up in the knowledge base, where a lead can approve them as new steps.

## Security model

- **Untrusted input**: customer text is wrapped in `<<< >>>`, and the prompt says it is data only.
  A regex scan (EN/DE/HU) flags override attempts for the colleague. Reply drafts are checked for
  links outside the shop's domain.
- **No autonomous actions**: the agent cannot send, refund or change orders. Its tools are read-only.
- **Least data**: tools return small summaries rather than raw records. Closed tickets are
  anonymised after the retention period.
- **Secrets**: the API key lives on the server (local mode). In claude.ai, calls run under the
  viewer's own account and connectors.
