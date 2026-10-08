/*
 * Local runtime for PrintDesk.
 *
 * Inside claude.ai the page gets `window.claude` from the Artifact runtime (Claude calls,
 * shared database, Gmail connector). When you run it locally with the Java backend
 * (`java -jar backend/target/printdesk.jar`), this file provides the same small surface
 * on top of the backend's REST API:
 *   sample(input) / sample.json(input) → POST /api/complete   (API key stays on the server)
 *   db.collection(..).doc(..).set/get/delete/onSnapshot → /api/db/...  (JSON file store)
 * Gmail is not available locally; paste emails or load the demo emails instead.
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

  window.claude = {
    use: async (name) => {
      const h = await health;
      if (!h) return null;
      if (name === "sample") return h.llm ? sample : null;
      if (name === "db") return db;
      return null; // mcp (Gmail), user, … are claude.ai-only
    },
  };
})();
