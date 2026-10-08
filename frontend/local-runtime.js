/*
 * Local runtime for PrintDesk.
 *
 * Inside claude.ai the page gets `window.claude` from the Artifact runtime (Claude calls,
 * shared database, Gmail connector). When you run it locally with the Java backend
 * (`java -jar backend/target/printdesk.jar`), this file provides the same small surface
 * on top of the backend's REST API:
 *   sample(input) / sample.json(input) → POST /api/complete   (API key stays on the server)
 *   db.collection(..).doc(..).set/get/delete/onSnapshot → /api/db/...  (JSON file store)
 *   mcp (Gmail) → /api/mail/...  a local demo mailbox that answers the same Gmail calls
 *                 (search_threads, get_thread, reply, send_message). Customers write and read
 *                 their mail on /mailbox.html, so the whole email loop works without Gmail.
 */
(function () {
  if (window.claude) return; // running inside claude.ai – use the real runtime

  const api = async (path, opts = {}) => {
    const r = await fetch(path, opts);
    const body = await r.json().catch(() => ({}));
    if (!r.ok) throw { code: r.status === 429 ? "rate_limited" : r.status === 503 ? "sampling_disabled" : "upstream_error", message: body.error || String(r.status) };
    return body;
  };
  const health = api("/api/health").catch(() => null);
  // First local start: point the inbox at the demo mailbox's support address.
  health.then(async h => {
    if (!h) return;
    const cur = await api("/api/db/settings/mailboxes").catch(() => null);
    if (cur && !cur.exists) await api("/api/db/settings/mailboxes", { method: "PUT", headers: { "content-type": "application/json" },
      body: JSON.stringify({ list: [{ addr: "support@printdesk-demo.example", kind: "general", owner: "General support" }], updated: Date.now() }) }).catch(() => {});
  });

  // ---------------------------------------------------------------- sample (Claude via backend)
  async function sample(input, opts = {}) {
    if (opts.signal?.aborted) throw { code: "cancelled", message: "aborted" };
    let res;
    try {
      res = await api("/api/complete", {
        method: "POST", headers: { "content-type": "application/json" }, signal: opts.signal,
        body: JSON.stringify({ input, tier: opts.modelTier || "default" }),
      });
    } catch (e) {
      if (e?.name === "AbortError") throw { code: "cancelled", message: "aborted" };
      throw e;
    }
    if (!res.text || !res.text.trim()) throw { code: "empty_completion", message: "empty answer" };
    opts.onText?.({ text: res.text, delta: res.text });
    return { text: res.text, truncated: !!res.truncated, modelTierApplied: opts.modelTier || "default" };
  }
  sample.json = async (input, opts = {}) => {
    const { text, truncated } = await sample(input, opts);
    const fence = text.match(/```(?:json)?\s*([\s\S]*?)```/);
    const candidates = [text, fence && fence[1], (() => { const a = Math.min(...["{", "["].map(c => text.indexOf(c)).filter(i => i >= 0)); const b = Math.max(text.lastIndexOf("}"), text.lastIndexOf("]")); return a >= 0 && b > a ? text.slice(a, b + 1) : null; })()];
    for (const c of candidates) { if (!c) continue; try { if (!truncated) return JSON.parse(c); } catch (_) {} }
    throw { code: "invalid_json", message: "answer was not JSON", text };
  };
  sample.limits = async () => ({ maxPromptBytes: 262144 }); // no page tools, no images locally

  // ---------------------------------------------------------------- db (backend JSON store)
  const enc = encodeURIComponent;
  const subs = new Set();
  const snapDoc = (id, d) => ({ id, exists: !!d, data: () => d, metadata: { fromCache: false, hasPendingWrites: false } });
  const refreshAll = () => subs.forEach(fn => fn());
  setInterval(refreshAll, 3000);

  function docRef(coll, id) {
    const url = `/api/db/${enc(coll)}/${enc(id)}`;
    return {
      id, path: `${coll}/${id}`,
      async get() { const r = await api(url); return snapDoc(id, r.exists ? r.data : undefined); },
      async set(data) { await api(url, { method: "PUT", headers: { "content-type": "application/json" }, body: JSON.stringify(data) }); refreshAll(); },
      async update(data) { const cur = await this.get(); if (!cur.exists) throw { code: "invalid_argument", message: "document does not exist" }; await this.set({ ...cur.data(), ...data }); },
      async delete() { await api(url, { method: "DELETE" }); refreshAll(); },
      async acquire() { return { acquired: true }; }, // single local user
      onSnapshot(next, error) {
        let last = "";
        const pull = () => this.get().then(s => { const k = JSON.stringify(s.data() ?? null); if (k !== last) { last = k; next(s); } }).catch(e => error?.(e));
        subs.add(pull); pull();
        return () => subs.delete(pull);
      },
    };
  }
  function collRef(coll) {
    return {
      path: coll,
      doc: (id) => docRef(coll, id || "d" + Date.now().toString(36) + Math.random().toString(36).slice(2, 7)),
      async add(data) { const d = docRef(coll, "d" + Date.now().toString(36) + Math.random().toString(36).slice(2, 7)); await d.set(data); return d; },
      async get() { const list = await api(`/api/db/${enc(coll)}`); const docs = list.map(d => snapDoc(d.id, d)); return { docs, size: docs.length, empty: !docs.length, docChanges: () => [], metadata: {} }; },
      onSnapshot(next, error) {
        let last = "";
        const pull = () => this.get().then(s => { const k = JSON.stringify(s.docs.map(d => d.data())); if (k !== last) { last = k; next(s); } }).catch(e => error?.(e));
        subs.add(pull); pull();
        return () => subs.delete(pull);
      },
    };
  }
  const db = {
    collection: collRef,
    doc(path) { const i = path.lastIndexOf("/"); return docRef(path.slice(0, i), path.slice(i + 1)); },
  };

  // ---------------------------------------------------------------- mcp (local demo mailbox instead of Gmail)
  const MAIL_POLL_MS = 5000; // local and cheap, so check more often than Gmail's 60 s
  async function gmailCall(tool, args = {}) {
    const post = (p, b) => api(p, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(b) });
    try {
      switch (tool) {
        case "search_threads": return { payload: await api("/api/mail/threads?q=" + enc(args.query || "")) };
        case "get_thread": return { payload: await api("/api/mail/threads/" + enc(args.threadId)) };
        case "reply": return { payload: await post("/api/mail/reply", { messageId: args.messageId, body: args.body, to: args.to || [] }) };
        case "send_message": return { payload: await post("/api/mail/send", { to: args.to || [], subject: args.subject, body: args.body }) };
        default: throw { code: "not_in_manifest", message: tool };
      }
    } catch (e) {
      throw e?.code === "not_in_manifest" ? e : { code: "tool_error", message: e?.message || "mail error" };
    }
  }
  const mcp = {
    async listTools() { return { servers: [{ server: "Gmail", authStatus: "ok", tools: ["search_threads", "get_thread", "reply", "send_message"] }] }; },
    async callTool(server, tool, args) { return gmailCall(tool, args); },
    watchTool(server, tool, args, onEvent, opts = {}) {
      let stopped = false;
      const tick = () => gmailCall(tool, args).then(r => !stopped && onEvent({ type: "result", result: r })).catch(e => !stopped && onEvent({ type: "error", error: e }));
      tick();
      const h = setInterval(tick, Math.min(opts.refetchInterval || MAIL_POLL_MS, MAIL_POLL_MS));
      return () => { stopped = true; clearInterval(h); };
    },
  };

  window.claude = {
    localMailLabel: "Demo mailbox",
    use: async (name) => {
      const h = await health;
      if (!h) return null;
      if (name === "sample") return h.llm ? sample : null;
      if (name === "db") return db;
      if (name === "mcp") return mcp;
      return null; // user, … are claude.ai-only
    },
  };
})();
