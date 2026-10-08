# Privacy brief for Legal (GDPR / DSGVO)

What personal data PrintDesk processes, where it goes and how long it stays, so Legal can check
the use case before a pilot. This is a description of the system, not legal advice. Each open
point is marked **→ Legal**.

## Personal data involved

| Data | Source | Why it is needed |
|---|---|---|
| Name and email address of the customer | Incoming email | To reply and to find the order |
| Email content (problem description, sometimes address or phone number) | Incoming email | To understand the problem and draft the reply |
| Order number, order items, purchase date | Shop system (lookup) | Warranty and the exact printer model |
| Photos attached to the email (optional) | Incoming email / upload | To see the print problem |
| Internal notes, the assigned colleague | Support team | Teamwork |
| Outcome (which step fixed it, ratings) | Support team | The learning loop. Kept after anonymisation **without** personal data. |

No special categories of data (Art. 9) are needed. If a customer writes something sensitive
anyway, it is stored like the rest of the message and removed at anonymisation.

## Where the data goes

| Recipient | What | Role |
|---|---|---|
| Anthropic (Claude) | The email text, order summary and photos, for triage and drafting | Processor **→ Legal: DPA, data region, confirm no training on the data** |
| Mail provider (e.g. Google) | Mail already stored there today | Existing processor |
| Ticket database | Tickets, notes, outcomes | Internal (production) / artifact storage (demo) |

What the model gets is limited on purpose. The tools return **short summaries** (e.g. the
order's date, items and warranty status), not raw customer records. The model cannot read
other customers' data, and it cannot send, refund or change anything.

## Retention and deletion

- **Automatic anonymisation** of closed tickets after a configurable period (default 90 days).
  Name, email address, message texts, order number and notes are removed. Printer, problem,
  language, priority and outcome stay for the statistics.
- **Deletion on request:** one click deletes the ticket (Art. 17). The linked learning record
  stays, with no personal data: its transcript and summary are cleared.
- **→ Legal:** confirm the retention period, and whether warranty cases need a longer one.

## Transparency

- **Email replies** are drafted by AI and reviewed, edited and sent by a person. **→ Legal:**
  is a note in the privacy policy enough, or should replies say that AI assisted?
- **Customer chat:** the chat says up front that the customer is talking to an AI assistant and
  that a colleague takes over when needed (EU AI Act Art. 50 transparency for AI that interacts
  with people). **→ Legal:** confirm the wording.

## Security measures already in place

- Customer text is treated as untrusted data: a prompt rule, a pattern scan for manipulation
  attempts, and a check for links outside the shop's domain in the draft.
- A person approves every outgoing message.
- In the local version the API key stays on the server and the server listens only on
  localhost.

## Open points for the pilot (→ Legal)

1. Legal basis: Art. 6(1)(b) (handling the customer's request) for triage and reply. Is a
   legitimate-interest assessment needed for the anonymised statistics?
2. DPA and transfer basis for the model provider.
3. Is a DPIA needed? (Likely not for drafting with human approval, but worth a short check.)
4. Update the privacy policy and the records of processing activities.
