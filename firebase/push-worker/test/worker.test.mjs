// Offline test of the push worker: Google token endpoint, JWKS, Firestore REST and FCM are all mocked.
import assert from "node:assert/strict";
import { generateKeyPairSync, createSign } from "node:crypto";
import worker from "../src/index.js";

const P = "demo-medk";
const idKeys = generateKeyPairSync("rsa", { modulusLength: 2048 });
const saKeys = generateKeyPairSync("rsa", { modulusLength: 2048 });
const jwk = { ...idKeys.publicKey.export({ format: "jwk" }), kid: "k1", alg: "RS256", use: "sig" };
const b64u = (b) => Buffer.from(b).toString("base64url");
function idToken(sub, o = {}) {
  const now = Math.floor(Date.now() / 1000);
  const h = b64u(JSON.stringify({ alg: "RS256", kid: "k1" }));
  const p = b64u(JSON.stringify({ aud: P, iss: `https://securetoken.google.com/${P}`, sub, iat: now, exp: now + 3600, ...o }));
  const s = createSign("RSA-SHA256").update(`${h}.${p}`).sign(idKeys.privateKey);
  return `${h}.${p}.${b64u(s)}`;
}
const env = { NOWPAY_API_KEY: "NPKEY", PROJECT_ID: P, SA_KEY: JSON.stringify({ client_email: "push@x", private_key: saKeys.privateKey.export({ type: "pkcs8", format: "pem" }) }) };

const iso = (ms = 0) => new Date(Date.now() + ms).toISOString();
let db, sent, deleted;
function reset() {
  sent = []; deleted = []; ut = {}; payments = {}; invoices = []; commitFail = 0;
  db = {
    "users/own": { role: "owner", active: true }, "users/cust": { role: "customer" }, "users/st1": { role: "staff" },
    "orders/o1": { reference: "MK-1", customerId: "cust", staffId: "st1", status: "paid", totalUsd: 3, items: [{ productName: "Diamonds" }], createdAt: iso(), updatedAt: iso() },
    "orders/old": { reference: "MK-2", customerId: "cust", staffId: "st1", status: "open", totalUsd: 1, items: [], createdAt: iso(-3600e3), updatedAt: iso(-3600e3) },
    "conversations/c1": { customerId: "cust", staffId: "st1", orderId: "o1", subject: "طلب MK-1" },
    "conversations/c2": { customerId: "cust", staffId: null, orderId: null, subject: "الدعم" },
    "conversations/c1/messages/m1": { senderId: "cust", body: "مرحبا", createdAt: iso() },
    "conversations/c2/messages/m2": { senderId: "cust", body: null, attachment: { type: "image" }, createdAt: iso() },
    "orders/o3": { reference: "MK-3", customerId: "cust", staffId: "st1", status: "open", totalUsd: 20, items: [], createdAt: iso(), updatedAt: iso() },
    "orders/o4": { reference: "MK-4", customerId: "cust", staffId: null, status: "open", totalUsd: 10, items: [], createdAt: iso(), updatedAt: iso() },
    "config/payments": { commissionPct: 10, enabled: true },
    "pushTokens/tc": { uid: "cust", token: "TOK_CUST" }, "pushTokens/ts": { uid: "st1", token: "TOK_ST1" },
    "pushTokens/to": { uid: "own", token: "TOK_OWN" }, "pushTokens/dead": { uid: "own", token: "TOK_DEAD" },
  };
}
const enc = (v) => v === null ? { nullValue: null } : typeof v === "string" ? { stringValue: v } : typeof v === "number" ? { doubleValue: v }
  : typeof v === "boolean" ? { booleanValue: v } : Array.isArray(v) ? { arrayValue: { values: v.map(enc) } }
  : { mapValue: { fields: Object.fromEntries(Object.entries(v).map(([k, x]) => [k, enc(x)])) } };
let ut = {}, clock = 0;
const touch = (path) => { ut[path] = `2026-01-01T00:00:00.${String(++clock).padStart(6, "0")}Z`; };
const docOut = (path) => ({ name: `projects/${P}/databases/(default)/documents/${path}`, fields: enc(db[path]).mapValue.fields, updateTime: ut[path] || "2026-01-01T00:00:00.000000Z" });
const dec = (v) => "stringValue" in v ? v.stringValue : "integerValue" in v ? Number(v.integerValue) : "doubleValue" in v ? v.doubleValue
  : "booleanValue" in v ? v.booleanValue : "nullValue" in v ? null : "arrayValue" in v ? (v.arrayValue.values || []).map(dec)
  : Object.fromEntries(Object.entries(v.mapValue.fields || {}).map(([k, x]) => [k, dec(x)]));
let payments = {}, invoices = [], commitFail = 0;

globalThis.fetch = async (url, init = {}) => {
  url = String(url);
  const ok = (o) => new Response(JSON.stringify(o), { status: 200 });
  if (url.includes("securetoken@system")) return ok({ keys: [jwk] });
  if (url === "https://oauth2.googleapis.com/token") return ok({ access_token: "AT", expires_in: 3600 });
  if (url.includes("nowpayments.io")) {
    if (init.headers["x-api-key"] !== "NPKEY") return new Response('{"message":"bad key"}', { status: 403 });
    if (url.endsWith("/invoice")) { const b = JSON.parse(init.body); invoices.push(b); return ok({ id: 9000 + invoices.length, invoice_url: `https://pay/inv/${invoices.length}` }); }
    const pid = url.split("/payment/")[1]; return payments[pid] ? ok(payments[pid]) : new Response("{}", { status: 404 });
  }
  if (url.includes("fcm.googleapis.com")) {
    const m = JSON.parse(init.body).message;
    if (m.token === "TOK_DEAD") return new Response('{"error":{"status":"NOT_FOUND","details":[{"errorCode":"UNREGISTERED"}]}}', { status: 404 });
    sent.push(m); return ok({ name: "x" });
  }
  const base = `https://firestore.googleapis.com/v1/projects/${P}/databases/(default)/documents`;
  if (url === `${base}:runQuery`) {
    const q = JSON.parse(init.body).structuredQuery, f = q.where.fieldFilter, col = q.from[0].collectionId;
    const vals = f.op === "IN" ? f.value.arrayValue.values.map((x) => x.stringValue) : [f.value.stringValue];
    return ok(Object.keys(db).filter((k) => k.split("/").length === 2 && k.startsWith(col + "/") && vals.includes(db[k][f.field.fieldPath]))
      .map((k) => ({ document: docOut(k) })));
  }
  if (url === `${base}:commit`) {
    if (commitFail > 0) { commitFail--; return new Response('{"error":{"status":"FAILED_PRECONDITION"}}', { status: 400 }); }
    const writes = JSON.parse(init.body).writes;
    for (const w of writes) { // validate all preconditions first (atomic)
      const path = w.update.name.split("/documents/")[1];
      if (w.currentDocument?.exists === false && db[path]) return new Response('{"error":{"status":"ALREADY_EXISTS"}}', { status: 409 });
      if (w.currentDocument?.updateTime && w.currentDocument.updateTime !== (ut[path] || "2026-01-01T00:00:00.000000Z"))
        return new Response('{"error":{"status":"FAILED_PRECONDITION"}}', { status: 400 });
    }
    for (const w of writes) {
      const path = w.update.name.split("/documents/")[1];
      const fields = Object.fromEntries(Object.entries(w.update.fields || {}).map(([k, v]) => [k, dec(v)]));
      db[path] = w.updateMask ? { ...(db[path] || {}), ...Object.fromEntries(w.updateMask.fieldPaths.map((f) => [f, fields[f]])) } : fields;
      for (const t of w.updateTransforms || []) {
        if (t.increment) db[path][t.fieldPath] = Math.round(((db[path][t.fieldPath] || 0) + t.increment.doubleValue) * 100) / 100;
        else db[path][t.fieldPath] = new Date().toISOString();
      }
      touch(path);
    }
    return ok({});
  }
  const path = url.slice(base.length + 1);
  if (init.method === "DELETE") { deleted.push(path); delete db[path]; return ok({}); }
  return db[path] ? ok(docOut(path)) : new Response("{}", { status: 404 });
};

const call = async (who, body, tok, path = "/notify") => {
  const r = await worker.fetch(new Request("https://w" + path, { method: "POST",
    headers: { authorization: `Bearer ${tok ?? idToken(who)}` }, body: JSON.stringify(body) }), env, {});
  return { status: r.status, ...(await r.json()) };
};
const to = () => sent.map((m) => m.token).sort();
let n = 0; const t = async (name, fn) => { reset(); await fn(); n++; console.log("  ✔", name); };

await t("health check", async () => { const r = await worker.fetch(new Request("https://w/"), env, {}); assert.equal(r.status, 200); });
await t("new order → owner + seller, not the customer; dead token removed", async () => {
  const r = await call("cust", { type: "order", orderId: "o1" });
  assert.equal(r.sent, 2); assert.deepEqual(to(), ["TOK_OWN", "TOK_ST1"]); assert.deepEqual(deleted, ["pushTokens/dead"]);
  assert.match(sent[0].notification.title, /طلب جديد/); assert.equal(sent[0].data.order_id, "o1"); assert.equal(sent[0].android.notification.channel_id, "medk_default");
});
await t("someone else cannot announce a customer's order", async () => { assert.equal((await call("st1", { type: "order", orderId: "o1" })).status, 403); assert.equal(sent.length, 0); });
await t("old events cannot be replayed", async () => { assert.equal((await call("cust", { type: "order", orderId: "old" })).status, 403); });
await t("status change by seller → customer only", async () => {
  const r = await call("st1", { type: "status", orderId: "o1" }); assert.equal(r.sent, 1); assert.deepEqual(to(), ["TOK_CUST"]);
  assert.match(sent[0].notification.body, /تم الدفع/);
});
await t("status change by owner allowed, by customer refused", async () => {
  assert.equal((await call("own", { type: "status", orderId: "o1" })).sent, 1);
  assert.equal((await call("cust", { type: "status", orderId: "o1" })).status, 403);
});
await t("assignment: owner only → seller", async () => {
  assert.equal((await call("st1", { type: "assigned", orderId: "o1" })).status, 403);
  reset(); const r = await call("own", { type: "assigned", orderId: "o1" }); assert.deepEqual(to(), ["TOK_ST1"]); assert.equal(r.sent, 1);
});
await t("message with seller → seller only (not sender)", async () => {
  const r = await call("cust", { type: "message", conversationId: "c1", messageId: "m1" }); assert.deepEqual(to(), ["TOK_ST1"]);
  assert.equal(sent[0].notification.body, "مرحبا"); assert.equal(sent[0].data.conversation_id, "c1");
});
await t("support message without seller → owner; image preview text", async () => {
  await call("cust", { type: "message", conversationId: "c2", messageId: "m2" }); assert.deepEqual(to(), ["TOK_OWN"]);
  assert.equal(sent[0].notification.body, "📷 صورة");
});
await t("cannot announce someone else's message", async () => { assert.equal((await call("st1", { type: "message", conversationId: "c1", messageId: "m1" })).status, 403); });
await t("forged / expired / wrong-project tokens rejected", async () => {
  const good = idToken("cust"); const forged = good.slice(0, -4) + "AAAA";
  assert.equal((await call("cust", { type: "order", orderId: "o1" }, forged)).status, 401);
  assert.equal((await call("cust", { type: "order", orderId: "o1" }, idToken("cust", { exp: 1 }))).status, 401);
  assert.equal((await call("cust", { type: "order", orderId: "o1" }, idToken("cust", { aud: "other" }))).status, 401);
  assert.equal((await call("cust", { type: "order", orderId: "o1" }, "x")).status, 401);
  assert.equal(sent.length, 0);
});
await t("bad input rejected", async () => {
  assert.equal((await call("cust", { type: "order", orderId: "../users/own" })).status, 400);
  assert.equal((await call("cust", { type: "spam" })).status, 400);
});

// ------------------------------------------------------------------ payments
const ipn = async (pid) => { const r = await worker.fetch(new Request("https://w/ipn", { method: "POST", body: JSON.stringify({ payment_id: pid }) }), env, {}); return { status: r.status, ...(await r.json()) }; };
const finished = (o) => ({ payment_id: 555, invoice_id: 9001, order_id: "o3", payment_status: "finished", price_amount: 20, outcome_amount: 19.8, outcome_currency: "usdttrc20", ...o });

await t("customer opens a USDT invoice; amount comes from the database", async () => {
  const r = await call("cust", { orderId: "o3", amountUsd: 0.01 }, undefined, "/pay");
  assert.equal(r.url, "https://pay/inv/1"); assert.equal(invoices[0].price_amount, 20); assert.equal(invoices[0].order_id, "o3");
  assert.equal(invoices[0].ipn_callback_url, "https://w/ipn"); assert.equal(db["orders/o3"].payment.status, "waiting");
  assert.equal((await call("cust", { orderId: "o3" }, undefined, "/pay")).url, "https://pay/inv/1", "reuses the open invoice");
  assert.equal(invoices.length, 1);
});
await t("only the order's customer can pay it; paid orders refused", async () => {
  assert.equal((await call("cust2", { orderId: "o3" }, undefined, "/pay")).status, 403);
  assert.equal((await call("cust", { orderId: "o1" }, undefined, "/pay")).status, 409);
});
await t("payments disabled by the owner → refused", async () => {
  db["config/payments"].enabled = false; assert.equal((await call("cust", { orderId: "o3" }, undefined, "/pay")).status, 503);
});
await t("finished payment: order paid + split fee / 10% commission / seller, notifications", async () => {
  await call("cust", { orderId: "o3" }, undefined, "/pay"); payments["555"] = finished();
  const r = await ipn(555);
  assert.deepEqual(r.split, { fee: 0.2, commission: 1.98, seller: 17.82 });
  assert.equal(db["orders/o3"].status, "paid"); assert.ok(db["orders/o3"].paidAt);
  assert.equal(db["orders/o3"].payment.sellerUsd, 17.82); assert.equal(db["wallets/st1"].balanceUsd, 17.82);
  assert.equal(db["wallets/platform"].balanceUsd, 1.98); assert.equal(db["wallets/st1/entries/sale-o3"].amountUsd, 17.82);
  assert.ok(to().includes("TOK_CUST") && to().includes("TOK_ST1") && to().includes("TOK_OWN"));
});
await t("duplicate callbacks never pay twice", async () => {
  await call("cust", { orderId: "o3" }, undefined, "/pay"); payments["555"] = finished();
  await ipn(555); const again = await ipn(555);
  assert.equal(again.already, true); assert.equal(db["wallets/st1"].balanceUsd, 17.82);
});
await t("concurrent change is retried safely", async () => {
  await call("cust", { orderId: "o3" }, undefined, "/pay"); payments["555"] = finished(); commitFail = 1;
  assert.equal((await ipn(555)).ok, true); assert.equal(db["wallets/st1"].balanceUsd, 17.82);
});
await t("wrong amount / unknown invoice / not finished → no money moves", async () => {
  await call("cust", { orderId: "o3" }, undefined, "/pay");
  payments["1"] = finished({ payment_id: 1, price_amount: 2 }); assert.equal((await ipn(1)).error, "amount_mismatch");
  payments["2"] = finished({ payment_id: 2, invoice_id: 1 }); assert.equal((await ipn(2)).error, "unknown_invoice");
  payments["3"] = finished({ payment_id: 3, payment_status: "partially_paid" }); assert.equal((await ipn(3)).status, "partially_paid");
  assert.equal(db["orders/o3"].status, "open"); assert.equal(db["wallets/st1"], undefined);
  assert.equal((await ipn("abc")).status, 400);
});
await t("order without a seller → everything to the platform", async () => {
  await call("cust", { orderId: "o4" }, undefined, "/pay");
  payments["7"] = finished({ payment_id: 7, invoice_id: 9001, order_id: "o4", price_amount: 10, outcome_amount: 9.95 });
  const r = await ipn(7); assert.deepEqual(r.split, { fee: 0.05, commission: 9.95, seller: 0 });
  assert.equal(db["wallets/platform"].balanceUsd, 9.95);
});
await t("seller withdraws to TRC20; balance reserved; validation", async () => {
  db["wallets/st1"] = { balanceUsd: 50 };
  const addr = "TXYZabcdefghijkmnopqrstuvwxyz12345";
  assert.equal((await call("st1", { amountUsd: 100, address: addr }, undefined, "/withdraw")).error, "insufficient_balance");
  assert.equal((await call("st1", { amountUsd: 10, address: "0xabc" }, undefined, "/withdraw")).error, "bad_address");
  assert.equal((await call("st1", { amountUsd: 2, address: addr }, undefined, "/withdraw")).error, "min_withdrawal_5");
  assert.equal((await call("cust", { amountUsd: 10, address: addr }, undefined, "/withdraw")).status, 403);
  const r = await call("st1", { amountUsd: 20, address: addr }, undefined, "/withdraw");
  assert.equal(r.ok, true); assert.equal(db["wallets/st1"].balanceUsd, 30); assert.equal(db[`withdrawals/${r.id}`].status, "pending");
  assert.ok(to().includes("TOK_OWN"));
});
await t("owner pays or rejects (refund); others cannot", async () => {
  db["wallets/st1"] = { balanceUsd: 50 }; const addr = "TXYZabcdefghijkmnopqrstuvwxyz12345";
  const a = await call("st1", { amountUsd: 20, address: addr }, undefined, "/withdraw");
  const b = await call("st1", { amountUsd: 10, address: addr }, undefined, "/withdraw");
  assert.equal((await call("st1", { id: a.id, action: "paid" }, undefined, "/withdrawal")).status, 403);
  assert.equal((await call("own", { id: a.id, action: "paid", txid: "abc123" }, undefined, "/withdrawal")).ok, true);
  assert.equal(db[`withdrawals/${a.id}`].status, "paid"); assert.equal(db[`withdrawals/${a.id}`].txid, "abc123");
  assert.equal((await call("own", { id: a.id, action: "reject" }, undefined, "/withdrawal")).status, 409, "no double decision");
  await call("own", { id: b.id, action: "reject" }, undefined, "/withdrawal");
  assert.equal(db["wallets/st1"].balanceUsd, 30); assert.equal(db[`withdrawals/${b.id}`].status, "rejected");
});
console.log(`\n  ${n} passing`);
