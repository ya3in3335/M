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
    if (req.method === "GET" && url.pathname === "/") return json({ ok: true, service: "medk-push", payments: !!env.NOWPAY_API_KEY });
    if (req.method === "GET" && url.pathname === "/paid") return donePage();
    if (req.method === "GET" && url.pathname === "/paycheck") { // setup diagnostics: is the gateway key accepted? (no secrets returned)
      try { const j = await nowpay(env, "/merchant/coins"); const list = (j.selectedCurrencies || []).map((x) => String(x).toLowerCase());
        return json({ ok: true, usdttrc20: list.includes("usdttrc20") }); }
      catch (e) { return json({ ok: false, error: e.message }); }
    }
    try {
      if (req.method === "POST" && url.pathname === "/ipn") return await handleIpn(req, env, ctx);
      if (req.method === "POST" && ["/pay", "/withdraw", "/withdrawal"].includes(url.pathname)) {
        const caller = await verifyIdToken((req.headers.get("authorization") || "").replace(/^Bearer\s+/i, ""), env.PROJECT_ID);
        const body = await req.json().catch(() => { throw new HttpError(400, "bad_json"); });
        const fs = new Firestore(env);
        if (url.pathname === "/pay") return json(await createInvoice(env, fs, caller, body, url.origin));
        if (url.pathname === "/withdraw") return json(await requestWithdrawal(fs, caller, body));
        return json(await decideWithdrawal(env, fs, caller, body));
      }
    } catch (e) {
      return json({ error: e.message || String(e) }, e.status || 500);
    }
    if (req.method !== "POST" || url.pathname !== "/notify") return json({ error: "not_found" }, 404);
    try {
      if (!env.SA_KEY || !env.PROJECT_ID) throw new HttpError(500, "server_not_configured");
      const auth = req.headers.get("authorization") || "";
      const caller = await verifyIdToken(auth.replace(/^Bearer\s+/i, ""), env.PROJECT_ID);
      const body = await req.json().catch(() => { throw new HttpError(400, "bad_json"); });
      const fs = new Firestore(env);
      const plan = await planFor(body, caller, fs);
      if (!plan) return json({ sent: 0 });
      return json({ sent: await pushTo(env, fs, plan, caller) });
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

async function pushTo(env, fs, plan, exclude) {
  const to = [...new Set(plan.to.filter((u) => u && u !== exclude))];
  const tokens = await fs.tokensFor(to);
  let sent = 0;
  await Promise.all(tokens.map(async (t) => { if (await sendFcm(env, fs, t, plan)) sent++; }));
  return sent;
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
    const d = await r.json();
    return { ...decodeFields(d.fields || {}), __updateTime: d.updateTime };
  }
  name(path) { return `projects/${this.env.PROJECT_ID}/databases/(default)/documents/${path}`; }
  /** Atomic multi-document write. Throws HttpError(409) when a precondition fails (concurrent change). */
  async commit(writes) {
    const r = await this.req(":commit", { method: "POST", body: JSON.stringify({ writes }) });
    if (r.status === 409 || r.status === 400 && /precondition|FAILED_PRECONDITION|ALREADY_EXISTS/i.test(await r.clone().text())) throw new HttpError(409, "conflict");
    if (!r.ok) throw new HttpError(502, `firestore_commit_${r.status}`);
    return r.json();
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
function encodeValue(v) {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === "string") return { stringValue: v };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(encodeValue) } };
  return { mapValue: { fields: encodeFields(v) } };
}
function encodeFields(o) { return Object.fromEntries(Object.entries(o).map(([k, v]) => [k, encodeValue(v)])); }
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

// ============================================================================================== payments (USDT)
//
// Gateway: NOWPayments (crypto, pay-as-you-go fee). The whole amount lands in the owner's NOWPayments payout
// wallet; this server splits it in the books the moment the payment is final:
//   gateway fee (price − outcome) → commission (config/payments.commissionPct of the net) → seller (the rest).
// Sellers see their balance in wallets/{uid} and request a USDT (TRC20) withdrawal; the owner approves and
// sends it. Clients can never write wallets, withdrawals or order.payment — only this server can.
//
// Secrets: NOWPAY_API_KEY. Vars: NOWPAY_SANDBOX = "1" to use the sandbox.

const PAY_CURRENCY = "usdttrc20";
const round2 = (x) => Math.round(x * 100) / 100;
const nowpayBase = (env) => env.NOWPAY_SANDBOX === "1" ? "https://api-sandbox.nowpayments.io/v1" : "https://api.nowpayments.io/v1";

async function nowpay(env, path, init = {}) {
  if (!env.NOWPAY_API_KEY) throw new HttpError(503, "payments_not_configured");
  const r = await fetch(`${nowpayBase(env)}${path}`, {
    ...init, headers: { "x-api-key": env.NOWPAY_API_KEY, "content-type": "application/json", ...(init.headers || {}) },
  });
  const j = await r.json().catch(() => ({}));
  if (!r.ok) throw new HttpError(502, `gateway_${r.status}${j.message ? ": " + j.message : ""}`);
  return j;
}

async function paymentsConfig(fs) {
  const c = (await fs.get("config/payments")) || {};
  const pct = Number(c.commissionPct);
  return { enabled: c.enabled !== false, commissionPct: Number.isFinite(pct) && pct >= 0 && pct <= 50 ? pct : 10 };
}

/** Customer asks to pay an open order → hosted USDT invoice (amount always taken from the database). */
async function createInvoice(env, fs, caller, body, origin) {
  const oid = typeof body.orderId === "string" && /^[A-Za-z0-9_-]{1,64}$/.test(body.orderId) ? body.orderId : null;
  if (!oid) throw new HttpError(400, "bad_id");
  const o = await fs.get(`orders/${oid}`);
  if (!o || o.customerId !== caller) throw new HttpError(403, "forbidden");
  if (o.status !== "open" || (o.payment && o.payment.status === "finished")) throw new HttpError(409, "already_paid");
  if (!(await paymentsConfig(fs)).enabled) throw new HttpError(503, "payments_disabled");
  const amount = round2(Number(o.totalUsd || 0));
  if (!(amount >= 1)) throw new HttpError(400, "amount_too_small");
  // The gateway refuses tiny crypto payments (network fees); tell the customer the current minimum.
  const min = await nowpay(env, `/min-amount?currency_from=${PAY_CURRENCY}&currency_to=${PAY_CURRENCY}&fiat_equivalent=usd`).catch(() => null);
  const minUsd = min && Number(min.fiat_equivalent);
  if (Number.isFinite(minUsd) && amount < minUsd) throw new HttpError(400, `amount_below_min:${Math.ceil(minUsd * 100) / 100}`);
  const p = o.payment;
  if (p && p.invoiceUrl && p.status === "waiting" && Date.now() - Date.parse(p.createdAt || 0) < 3600_000) return { url: p.invoiceUrl };
  const inv = await nowpay(env, "/invoice", {
    method: "POST",
    body: JSON.stringify({
      price_amount: amount, price_currency: "usd", pay_currency: PAY_CURRENCY,
      order_id: oid, order_description: `MED-K ${o.reference}`,
      ipn_callback_url: `${origin}/ipn`, success_url: `${origin}/paid?ok=1`, cancel_url: `${origin}/paid?ok=0`,
      is_fixed_rate: true, is_fee_paid_by_user: false,
    }),
  });
  if (!inv.invoice_url || !inv.id) throw new HttpError(502, "gateway_bad_response");
  await fs.commit([{
    update: { name: fs.name(`orders/${oid}`), fields: encodeFields({ payment: {
      provider: "nowpayments", currency: PAY_CURRENCY, invoiceId: String(inv.id), invoiceUrl: inv.invoice_url,
      status: "waiting", amountUsd: amount, createdAt: new Date().toISOString() } }) },
    updateMask: { fieldPaths: ["payment"] },
    currentDocument: { updateTime: o.__updateTime },
  }]);
  return { url: inv.invoice_url };
}

/**
 * Gateway callback. The body is only a hint: the payment is re-read from the gateway API with our key,
 * so a forged callback can at most trigger a harmless re-check.
 */
async function handleIpn(req, env, ctx) {
  const hint = await req.json().catch(() => ({}));
  const pid = String(hint.payment_id || "");
  if (!/^\d{1,20}$/.test(pid)) return json({ ok: false, error: "bad_payment_id" }, 400);
  const pay = await nowpay(env, `/payment/${pid}`);
  const fs = new Firestore(env);
  const oid = String(pay.order_id || "");
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(oid)) return json({ ok: false, error: "bad_order" }, 400);
  for (let attempt = 0; attempt < 3; attempt++) {
    try { return json(await settle(env, fs, oid, pay)); }
    catch (e) { if (e.status !== 409) throw e; }
  }
  return json({ ok: false, error: "conflict" }, 409);
}

async function settle(env, fs, oid, pay) {
  const o = await fs.get(`orders/${oid}`);
  if (!o || !o.payment || String(o.payment.invoiceId) !== String(pay.invoice_id)) return { ok: false, error: "unknown_invoice" };
  if (o.payment.status === "finished") return { ok: true, already: true };
  const status = String(pay.payment_status || "");
  const price = round2(Number(pay.price_amount || 0));
  if (Math.abs(price - round2(Number(o.totalUsd || 0))) > 0.01) return { ok: false, error: "amount_mismatch" };

  if (status !== "finished") { // progress only (waiting / confirming / partially_paid / failed / expired …)
    await fs.commit([{
      update: { name: fs.name(`orders/${oid}`), fields: encodeFields({ payment: { ...stripMeta(o.payment), status, paymentId: String(pay.payment_id) } }) },
      updateMask: { fieldPaths: ["payment"] }, currentDocument: { updateTime: o.__updateTime },
    }]);
    if (status === "partially_paid") await pushTo(env, fs, { to: await fs.ownerIds(), title: "دفع ناقص ⚠️",
      body: `الطلب ${o.reference}: وصل جزء من المبلغ فقط`, data: { order_id: oid }, tag: `pay-${oid}` });
    return { ok: true, status };
  }

  // ---- final: split
  const out = Number(pay.outcome_amount);
  const fee = /^usdt/i.test(String(pay.outcome_currency || "")) && Number.isFinite(out) && out > 0 ? round2(Math.max(0, price - out)) : 0;
  const net = round2(price - fee);
  const { commissionPct } = await paymentsConfig(fs);
  const commission = o.staffId ? round2(net * commissionPct / 100) : net;
  const seller = round2(net - commission);
  const now = new Date().toISOString();
  const payment = { ...stripMeta(o.payment), status: "finished", paymentId: String(pay.payment_id),
    paidUsd: price, feeUsd: fee, commissionUsd: commission, commissionPct, sellerUsd: seller, sellerId: o.staffId || null, settledAt: now };

  const writes = [{
    update: { name: fs.name(`orders/${oid}`), fields: encodeFields({ payment, ...(o.status === "open" ? { status: "paid" } : {}) }) },
    updateMask: { fieldPaths: ["payment", ...(o.status === "open" ? ["status"] : [])] },
    updateTransforms: [{ fieldPath: "updatedAt", setToServerValue: "REQUEST_TIME" },
      ...(o.paidAt ? [] : [{ fieldPath: "paidAt", setToServerValue: "REQUEST_TIME" }])],
    currentDocument: { updateTime: o.__updateTime },
  }];
  const credit = (uid, amount, type) => amount > 0 && writes.push(
    { update: { name: fs.name(`wallets/${uid}`), fields: encodeFields({ updatedAt: now }) }, updateMask: { fieldPaths: ["updatedAt"] },
      updateTransforms: [{ fieldPath: "balanceUsd", increment: { doubleValue: amount } }, { fieldPath: "earnedUsd", increment: { doubleValue: amount } }] },
    { update: { name: fs.name(`wallets/${uid}/entries/${type}-${oid}`), fields: encodeFields({
        type, amountUsd: amount, orderId: oid, reference: o.reference, createdAt: now }) }, currentDocument: { exists: false } });
  if (o.staffId) credit(o.staffId, seller, "sale");
  credit("platform", commission, "commission");
  await fs.commit(writes);

  await pushTo(env, fs, { to: [...(await fs.ownerIds()), o.staffId], title: "تم استلام دفعة 💰",
    body: `الطلب ${o.reference} — ${price.toFixed(2)} USDT`, data: { order_id: oid }, tag: `pay-${oid}` });
  await pushTo(env, fs, { to: [o.customerId], title: "تم الدفع بنجاح ✅",
    body: `طلبك ${o.reference} مدفوع، البائع سيتواصل معك`, data: { order_id: oid }, tag: `pay-${oid}` });
  return { ok: true, status, split: { fee, commission, seller } };
}

const stripMeta = (o) => Object.fromEntries(Object.entries(o || {}).filter(([k]) => !k.startsWith("__")));

/** Seller asks to withdraw part of his balance to a USDT (TRC20) address. Balance is reserved immediately. */
async function requestWithdrawal(fs, caller, body) {
  const u = await fs.get(`users/${caller}`);
  if (!u || u.active === false || u.role !== "staff") throw new HttpError(403, "forbidden");
  const amount = round2(Number(body.amountUsd));
  const address = String(body.address || "").trim();
  if (!(amount >= 5)) throw new HttpError(400, "min_withdrawal_5");
  if (!/^T[1-9A-HJ-NP-Za-km-z]{33}$/.test(address)) throw new HttpError(400, "bad_address");
  const w = await fs.get(`wallets/${caller}`);
  if (!w || round2(Number(w.balanceUsd || 0)) < amount) throw new HttpError(400, "insufficient_balance");
  const id = crypto.randomUUID().replace(/-/g, "").slice(0, 20);
  const now = new Date().toISOString();
  await fs.commit([
    { update: { name: fs.name(`wallets/${caller}`), fields: encodeFields({ updatedAt: now }) }, updateMask: { fieldPaths: ["updatedAt"] },
      updateTransforms: [{ fieldPath: "balanceUsd", increment: { doubleValue: -amount } }],
      currentDocument: { updateTime: w.__updateTime } },
    { update: { name: fs.name(`withdrawals/${id}`), fields: encodeFields({
        uid: caller, name: u.fullName || "", amountUsd: amount, address, network: "TRC20", status: "pending", txid: null, createdAt: now }) },
      currentDocument: { exists: false } },
    { update: { name: fs.name(`wallets/${caller}/entries/withdraw-${id}`), fields: encodeFields({
        type: "withdraw", amountUsd: -amount, withdrawalId: id, createdAt: now }) }, currentDocument: { exists: false } },
  ]);
  await pushTo(fs.env, fs, { to: await fs.ownerIds(), title: "طلب سحب جديد 🏦",
    body: `${u.fullName || "بائع"} — ${amount.toFixed(2)} USDT`, data: {}, tag: `wd-${id}` }).catch(() => 0);
  return { ok: true, id };
}

/** Owner marks a withdrawal as sent (with the transaction id) or rejects it (balance is refunded). */
async function decideWithdrawal(env, fs, caller, body) {
  if (!(await fs.isOwner(caller))) throw new HttpError(403, "forbidden");
  const id = typeof body.id === "string" && /^[A-Za-z0-9]{1,40}$/.test(body.id) ? body.id : null;
  if (!id || !["paid", "reject"].includes(body.action)) throw new HttpError(400, "bad_request");
  const w = await fs.get(`withdrawals/${id}`);
  if (!w || w.status !== "pending") throw new HttpError(409, "not_pending");
  const now = new Date().toISOString();
  const txid = String(body.txid || "").trim().slice(0, 120) || null;
  const writes = [{
    update: { name: fs.name(`withdrawals/${id}`), fields: encodeFields({ status: body.action === "paid" ? "paid" : "rejected", txid, decidedAt: now }) },
    updateMask: { fieldPaths: ["status", "txid", "decidedAt"] }, currentDocument: { updateTime: w.__updateTime },
  }];
  if (body.action === "reject") writes.push(
    { update: { name: fs.name(`wallets/${w.uid}`), fields: encodeFields({ updatedAt: now }) }, updateMask: { fieldPaths: ["updatedAt"] },
      updateTransforms: [{ fieldPath: "balanceUsd", increment: { doubleValue: Number(w.amountUsd) } }] },
    { update: { name: fs.name(`wallets/${w.uid}/entries/refund-${id}`), fields: encodeFields({
        type: "refund", amountUsd: Number(w.amountUsd), withdrawalId: id, createdAt: now }) }, currentDocument: { exists: false } });
  await fs.commit(writes);
  await pushTo(env, fs, { to: [w.uid], title: body.action === "paid" ? "تم تحويل أموالك ✅" : "تم رفض طلب السحب",
    body: `${Number(w.amountUsd).toFixed(2)} USDT${body.action === "paid" ? "" : " — أُعيد المبلغ إلى رصيدك"}`, data: {}, tag: `wd-${id}` });
  return { ok: true };
}

function donePage() {
  return new Response(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>MED-K</title><body style="font-family:system-ui;background:#0a0a0c;color:#fff;display:grid;place-items:center;min-height:90vh;text-align:center">
<div><div style="font-size:56px">✅</div><h2>تمت العملية</h2><p style="color:#aaa">ارجع إلى تطبيق MED-K — ستتحدث حالة طلبك تلقائياً عند تأكيد الدفع.</p></div></body></html>`,
    { headers: { "content-type": "text/html; charset=utf-8" } });
}
