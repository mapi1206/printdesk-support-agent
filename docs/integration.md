# Integration brief for IT

What PrintDesk needs from the shop's systems to go from demo to production, so IT can estimate
the effort. Every integration point is already a narrow interface in the code. Replacing a
simulated source means implementing one class or one endpoint; the agent and the UI do not
change.

## What is simulated today and what replaces it

| Today (demo) | In code | Production source | Access needed |
|---|---|---|---|
| Product catalog: 59 parts with prices, compatibility, delivery time (3djake.at snapshot) | `data/data.json` → `Catalog` | Product feed or PIM export, refreshed daily | Read-only feed (CSV/JSON) or API |
| Stock levels (simulated) | `Part.stock` | ERP / warehouse | Read-only, near real time (15 min is enough) |
| Orders and warranty (77 simulated orders) | `OrderService.find(orderNo, email)` | Shop backend / ERP | Read-only lookup by order number **and** by customer email |
| Case history (480 simulated cases) | `cases` | Starts empty and fills from closed tickets. Optional import of old tickets from the current helpdesk. | One-off export (anonymised) |
| Mail: Gmail connector (claude.ai) or demo mailbox (local) | `search_threads`, `get_thread`, `reply`, `send_message` | Support mailboxes or the current helpdesk tool | Read inbox, send in thread. A service account, not a personal login. |
| Ticket store: artifact database (claude.ai) or JSON file (local) | `LocalStore` | Small database (e.g. PostgreSQL) | Standard |
| Claude | `AnthropicClient` (API key on the server) | Anthropic API, or Claude through the company's existing setup | API key in the secret store, monthly budget |

## Suggested production setup

```
Mail (IMAP/Gmail API or helpdesk webhook)
        │  new message
        ▼
  Ingest worker  ──►  Pre-filter  ──►  Triage agent  ──►  Ticket DB
  (scheduled, no browser needed)       (Claude + tools:            │
                                        catalog, stock, orders)    ▼
                                                            PrintDesk UI (SSO)
                                                                   │ approve + send
                                                                   ▼
                                                           Mail send in thread
```

The difference from the demo: mail is polled by a **server-side worker** rather than by the
open page. Triage runs even when nobody has PrintDesk open, so tickets are ready when the team
starts in the morning.

## Rough effort (to be validated with IT)

| Work package | Rough estimate | Depends on |
|---|---|---|
| Order lookup adapter (order no. + email → order, items, date) | 2–4 days | Existing API or DB view |
| Catalog + stock feed import | 2–3 days | Feed format |
| Mail ingestion worker + sending in thread | 3–5 days | Gmail API vs. IMAP vs. helpdesk tool |
| Ticket DB, SSO login, roles (agent / lead) | 3–5 days | Identity provider |
| Hosting, secrets, logging, monitoring | 2–3 days | Platform standards |
| Pilot with one team, then fixes | 2 weeks | – |

If support already works in a helpdesk tool (Zendesk, Freshdesk, …), the better option is
often to **run PrintDesk inside it**: the agent writes the triage and draft as an internal note
or a suggested reply via the tool's API, and the team keeps the tool they know. In that case
the mail work package is replaced by a helpdesk integration of similar size. **For Freshdesk this
is already built:** see [freshdesk.md](freshdesk.md).

## Questions for IT

1. Which system holds orders, and is there an API for lookup by order number and by email?
2. Where does support mail arrive today: Gmail/Google Workspace, Exchange, or a helpdesk tool?
3. Is there a product/stock feed we can read, and how fresh is it?
4. Where should the service run, and which identity provider do we use for login?
5. Which logging and retention rules apply to stored email content? (See `privacy.md`.)
