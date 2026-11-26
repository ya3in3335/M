// MED-K — instant notification server (Cloudflare Worker, free plan).
//
// The apps never send notifications themselves. After writing an order / message / status change they call
//   POST /notify  { type, orderId | conversationId+messageId }   with  Authorization: Bearer <Firebase ID token>
// This worker:
//   1. verifies the Firebase ID token (signature, project, expiry),
//   2. re-reads the document from Firestore with a service account,
//   3. checks the caller really authored this event, recently (no spoofing, no replays of old events),
//   4. picks the recipients itself (never trusts the client for that) and sends FCM notifications,
//   5. removes device tokens that FCM reports as dead.
// Secrets: SA_KEY (service-account JSON, Worker secret). Vars: PROJECT_ID.

const JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const FRESH_MS = 5 * 60 * 1000; // an event can only be announced within 5 minutes of happening
const STATUS_LABEL = { open: "مفتوح", paid: "تم الدفع", delivered: "تم التسليم", closed: "مغلق" };

const json = (o, status = 200) => new Response(JSON.stringify(o), { status, headers: { "content-type": "application/json" } });
class HttpError extends Error { constructor(status, msg) { super(msg); this.status = status; } }

export default {
  async fetch(req, env, ctx) {
    const url = new URL(req.url);
    if (req.method === "GET" && url.pathname === "/") return json({ ok: true, service: "medk-push" });
    if (req.method !== "POST" || url.pathname !== "/notify") return json({ error: "not_found" }, 404);
    try {
      if (!env.SA_KEY || !env.PROJECT_ID) throw new HttpError(500, "server_not_configured");
      const auth = req.headers.get("authorization") || "";
      const caller = await verifyIdToken(auth.replace(/^Bearer\s+/i, ""), env.PROJECT_ID);
      const body = await req.json().catch(() => { throw new HttpError(400, "bad_json"); });
      const fs = new Firestore(env);
      const plan = await planFor(body, caller, fs);
      if (!plan) return json({ sent: 0 });
      const to = [...new Set(plan.to.filter((u) => u && u !== caller))];
      const tokens = await fs.tokensFor(to);
      let sent = 0;
      await Promise.all(tokens.map(async (t) => { if (await sendFcm(env, fs, t, plan)) sent++; }));
      return json({ sent });
    } catch (e) {
      return json({ error: e.message || String(e) }, e.status || 500);
    }
  },
};

// ---------------------------------------------------------------------------------------------- events

async function planFor(body, caller, fs) {
  const id = (v) => typeof v === "string" && /^[A-Za-z0-9_-]{1,64}$/.test(v) ? v : null;
  switch (body.type) {
    case "order": { // customer placed an order → owners + assigned seller
      const oid = id(body.orderId); if (!oid) throw new HttpError(400, "bad_id");
      const o = await fs.get(`orders/${oid}`);
      if (!o || o.customerId !== caller || !fresh(o.createdAt)) throw new HttpError(403, "forbidden");
      const item = (o.items || [])[0] || {};
      return {
        to: [...(await fs.ownerIds()), o.staffId],
        title: "طلب جديد 🛒",
        body: `${o.reference} — ${item.productName || ""} — ${Number(o.totalUsd || 0).toFixed(2)}$`,
        data: { order_id: oid }, tag: `order-${oid}`,
      };
    }
    case "status": { // seller/owner changed the status → customer
      const oid = id(body.orderId); if (!oid) throw new HttpError(400, "bad_id");
      const o = await fs.get(`orders/${oid}`);
      if (!o || !fresh(o.updatedAt)) throw new HttpError(403, "forbidden");
      if (o.staffId !== caller && !(await fs.isOwner(caller))) throw new HttpError(403, "forbidden");
      return {
        to: [o.customerId],
        title: "تحديث طلبك 📦",
        body: `حالة الطلب ${o.reference} الآن: ${STATUS_LABEL[o.status] || o.status}`,
        data: { order_id: oid }, tag: `status-${oid}`,
      };
    }
    case "assigned": { // owner assigned an order to a seller → that seller
      const oid = id(body.orderId); if (!oid) throw new HttpError(400, "bad_id");
      if (!(await fs.isOwner(caller))) throw new HttpError(403, "forbidden");
      const o = await fs.get(`orders/${oid}`);
      if (!o || !o.staffId || !fresh(o.updatedAt)) throw new HttpError(403, "forbidden");
      return {
        to: [o.staffId],
        title: "طلب جديد مُسند إليك 📋",
        body: `${o.reference} — ${Number(o.totalUsd || 0).toFixed(2)}$`,
        data: { order_id: oid }, tag: `assigned-${oid}`,
      };
    }
    case "message": { // chat message → the other participants
      const cid = id(body.conversationId), mid = id(body.messageId);
      if (!cid || !mid) throw new HttpError(400, "bad_id");
      const [cv, m] = await Promise.all([fs.get(`conversations/${cid}`), fs.get(`conversations/${cid}/messages/${mid}`)]);
      if (!cv || !m || m.senderId !== caller || !fresh(m.createdAt)) throw new HttpError(403, "forbidden");
      const to = cv.staffId ? [cv.customerId, cv.staffId] : [cv.customerId, ...(await fs.ownerIds())];
      const text = (m.body && String(m.body).trim()) ||
        (m.attachment && m.attachment.type === "image" ? "📷 صورة" : "📄 ملف PDF");
      return {
        to,
        title: `رسالة جديدة 💬 ${cv.subject ? "— " + String(cv.subject).slice(0, 60) : ""}`.trim(),
        body: text.length > 140 ? text.slice(0, 137) + "…" : text,
        data: { conversation_id: cid, ...(cv.orderId ? { order_id: cv.orderId } : {}) }, tag: `chat-${cid}`,
      };
    }
    default:
      throw new HttpError(400, "bad_type");
  }
}

function fresh(ts) {
  const t = ts ? Date.parse(ts) : NaN;
  return Number.isFinite(t) && Math.abs(Date.now() - t) <= FRESH_MS;
}

// ---------------------------------------------------------------------------------------------- FCM

async function sendFcm(env, fs, t, plan) {
  const at = await accessToken(env);
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${env.PROJECT_ID}/messages:send`, {
    method: "POST",
    headers: { authorization: `Bearer ${at}`, "content-type": "application/json" },
    body: JSON.stringify({
      message: {
        token: t.token,
        notification: { title: plan.title, body: plan.body },
        data: { ...plan.data, tag: plan.tag },
        android: { priority: "HIGH", notification: { channel_id: "medk_default", tag: plan.tag, default_sound: true } },
      },
    }),
  });
  if (res.ok) return true;
  const err = await res.text();
  // Dead / foreign tokens are removed so they are not retried forever.
  if (res.status === 404 || /UNREGISTERED|SENDER_ID_MISMATCH|registration-token-not-registered/.test(err)) {
    await fs.delete(`pushTokens/${t.id}`).catch(() => {});
  }
  return false;
}

// ---------------------------------------------------------------------------------------------- Firestore (REST)

class Firestore {
  constructor(env) {
    this.env = env;
    this.base = `https://firestore.googleapis.com/v1/projects/${env.PROJECT_ID}/databases/(default)/documents`;
  }
  async req(path, init = {}) {
    const at = await accessToken(this.env);
    return fetch(`${this.base}${path}`, { ...init, headers: { authorization: `Bearer ${at}`, "content-type": "application/json" } });
  }
  async get(path) {
    const r = await this.req(`/${path}`);
    if (r.status === 404) return null;
    if (!r.ok) throw new HttpError(502, `firestore_${r.status}`);
    return decodeFields((await r.json()).fields || {});
  }
  async delete(path) { await this.req(`/${path}`, { method: "DELETE" }); }
  async query(structuredQuery) {
    const r = await this.req(":runQuery", { method: "POST", body: JSON.stringify({ structuredQuery }) });
    if (!r.ok) throw new HttpError(502, `firestore_query_${r.status}`);
    return (await r.json()).filter((x) => x.document).map((x) => ({
      id: x.document.name.split("/").pop(), ...decodeFields(x.document.fields || {}),
    }));
  }
  async ownerIds() {
    if (!this._owners) {
      const rows = await this.query({
        from: [{ collectionId: "users" }],
        where: { fieldFilter: { field: { fieldPath: "role" }, op: "EQUAL", value: { stringValue: "owner" } } },
        limit: 20,
      });
      this._owners = rows.filter((u) => u.active !== false).map((u) => u.id);
    }
    return this._owners;
  }
  async isOwner(uid) { return (await this.ownerIds()).includes(uid); }
  async tokensFor(uids) {
    const out = [];
    for (let i = 0; i < uids.length; i += 30) {
      const chunk = uids.slice(i, i + 30);
      if (!chunk.length) continue;
      out.push(...(await this.query({
        from: [{ collectionId: "pushTokens" }],
        where: { fieldFilter: { field: { fieldPath: "uid" }, op: "IN",
          value: { arrayValue: { values: chunk.map((s) => ({ stringValue: s })) } } } },
        limit: 300,
      })));
    }
    return out.filter((t) => typeof t.token === "string");
  }
}

function decodeValue(v) {
  if ("stringValue" in v) return v.stringValue;
  if ("integerValue" in v) return Number(v.integerValue);
  if ("doubleValue" in v) return v.doubleValue;
  if ("booleanValue" in v) return v.booleanValue;
  if ("timestampValue" in v) return v.timestampValue;
  if ("nullValue" in v) return null;
  if ("mapValue" in v) return decodeFields(v.mapValue.fields || {});
  if ("arrayValue" in v) return (v.arrayValue.values || []).map(decodeValue);
  if ("referenceValue" in v) return v.referenceValue;
  return null;
}
function decodeFields(f) { return Object.fromEntries(Object.entries(f).map(([k, v]) => [k, decodeValue(v)])); }

// ---------------------------------------------------------------------------------------------- crypto

const b64url = (buf) => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const b64urlJson = (o) => b64url(new TextEncoder().encode(JSON.stringify(o)));
const fromB64url = (s) => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(s.length / 4) * 4, "=")), (c) => c.charCodeAt(0));

let jwksCache = { at: 0, keys: [] };
async function verifyIdToken(token, projectId) {
  const parts = (token || "").split(".");
  if (parts.length !== 3) throw new HttpError(401, "unauthenticated");
  let header, payload;
  try {
    header = JSON.parse(new TextDecoder().decode(fromB64url(parts[0])));
    payload = JSON.parse(new TextDecoder().decode(fromB64url(parts[1])));
  } catch { throw new HttpError(401, "unauthenticated"); }
  if (header.alg !== "RS256") throw new HttpError(401, "unauthenticated");
  if (Date.now() - jwksCache.at > 3600_000 || !jwksCache.keys.some((k) => k.kid === header.kid)) {
    const r = await fetch(JWKS_URL);
    jwksCache = { at: Date.now(), keys: (await r.json()).keys || [] };
  }
  const jwk = jwksCache.keys.find((k) => k.kid === header.kid);
  if (!jwk) throw new HttpError(401, "unauthenticated");
  const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, fromB64url(parts[2]), new TextEncoder().encode(`${parts[0]}.${parts[1]}`));
  const now = Math.floor(Date.now() / 1000);
  if (!ok || payload.aud !== projectId || payload.iss !== `https://securetoken.google.com/${projectId}` ||
      !payload.sub || payload.exp < now || payload.iat > now + 300) throw new HttpError(401, "unauthenticated");
  return payload.sub;
}

let tokenCache = { value: null, exp: 0 };
async function accessToken(env) {
  if (tokenCache.value && Date.now() < tokenCache.exp - 60_000) return tokenCache.value;
  const sa = JSON.parse(env.SA_KEY);
  const now = Math.floor(Date.now() / 1000);
  const unsigned = `${b64urlJson({ alg: "RS256", typ: "JWT" })}.${b64urlJson({
    iss: sa.client_email, aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600,
    scope: "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging",
  })}`;
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const key = await crypto.subtle.importKey("pkcs8", Uint8Array.from(atob(pem), (c) => c.charCodeAt(0)),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${unsigned}.${b64url(sig)}`,
  });
  const j = await r.json();
  if (!j.access_token) throw new HttpError(502, "google_auth_failed");
  tokenCache = { value: j.access_token, exp: Date.now() + (j.expires_in || 3600) * 1000 };
  return j.access_token;
}
