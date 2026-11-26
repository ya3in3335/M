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
const env = { BREVO_API_KEY: "BK", MAIL_FROM: "no-reply@medk.app", NOWPAY_API_KEY: "NPKEY", PROJECT_ID: P, SA_KEY: JSON.stringify({ client_email: "push@x", private_key: saKeys.privateKey.export({ type: "pkcs8", format: "pem" }) }) };

const iso = (ms = 0) => new Date(Date.now() + ms).toISOString();
let db, sent, deleted;
function reset() {
  sent = []; deleted = []; ut = {}; mails = []; verifiedUsers = []; payments = {}; invoices = []; commitFail = 0;
  db = {
    "users/own": { role: "owner", active: true }, "users/cust": { role: "customer" }, "users/st1": { role: "staff" },
    "orders/o1": { reference: "MK-1", customerId: "cust", staffId: "st1", status: "paid", totalUsd: 3, items: [{ productName: "Diamonds" }], createdAt: iso(), updatedAt: iso() },
    "orders/old": { reference: "MK-2", customerId: "cust", staffId: "st1", status: "open", totalUsd: 1, items: [], createdAt: iso(-3600e3), updatedAt: iso(-3600e3) },
    "conversations/c1": { customerId: "cust", staffId: "st1", orderId: "o1", subject: "طلب MK-1" },
    "conversations/c2": { customerId: "cust", staffId: null, orderId: null, subject: "الدعم" },
    "conversations/c1/messages/m1": { senderId: "cust", body: "مرحبا", createdAt: iso() },
    "conversations/c2/messages/m2": { senderId: "cust", body: null, attachment: { type: "image" }, createdAt: iso() },
    "orders/o3": { reference: "MK-3", customerId: "cust", staffId: "st1", status: "open", totalUsd: 20, items: [], createdAt: iso(), updatedAt: iso() },
    "orders/o4": { reference: "MK-4", customerId: "cust", staffId: null, status: "open", totalUsd: 12, items: [], createdAt: iso(), updatedAt: iso() },
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
let payments = {}, invoices = [], commitFail = 0, mails = [], verifiedUsers = [];

globalThis.fetch = async (url, init = {}) => {
  url = String(url);
  const ok = (o) => new Response(JSON.stringify(o), { status: 200 });
  if (url.includes("securetoken@system")) return ok({ keys: [jwk] });
  if (url === "https://oauth2.googleapis.com/token") return ok({ access_token: "AT", expires_in: 3600 });
  if (url.includes("nowpayments.io")) {
    if (init.headers["x-api-key"] !== "NPKEY") return new Response('{"message":"bad key"}', { status: 403 });
    if (url.endsWith("/invoice")) { const b = JSON.parse(init.body); invoices.push(b); return ok({ id: 9000 + invoices.length, invoice_url: `https://pay/inv/${invoices.length}` }); }
    if (url.includes("/min-amount")) return ok({ min_amount: 10.3, fiat_equivalent: 10.31 });
    const pid = url.split("/payment/")[1]; return payments[pid] ? ok(payments[pid]) : new Response("{}", { status: 404 });
  }
  if (url === "https://api.brevo.com/v3/smtp/email") { mails.push(JSON.parse(init.body)); return ok({ messageId: "m" }); }
  if (url.includes("identitytoolkit.googleapis.com")) { verifiedUsers.push(JSON.parse(init.body)); return ok({}); }
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
        if (t.increment) db[path][t.fieldPath] = Math.round(((db[path][t.fieldPath] || 0) + Number(t.increment.doubleValue ?? t.increment.integerValue)) * 100) / 100;
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

// ------------------------------------------------------------------ manual payments
await t("customer payment claim → assigned seller only", async () => {
  db["orders/o1"].paymentClaim = { method: "بريدي موب", at: iso() };
  const r = await call("cust", { type: "paymentClaim", orderId: "o1" });
  assert.equal(r.sent, 1); assert.deepEqual(to(), ["TOK_ST1"]); assert.match(sent[0].notification.body, /بريدي موب/);
});
await t("claim on an unassigned order → owner", async () => {
  db["orders/o4"].paymentClaim = { method: "CCP", at: iso() };
  await call("cust", { type: "paymentClaim", orderId: "o4" }); assert.deepEqual(to(), ["TOK_OWN"]);
});
await t("claims: only the customer, only recent", async () => {
  db["orders/o1"].paymentClaim = { method: "x", at: iso() };
  assert.equal((await call("st1", { type: "paymentClaim", orderId: "o1" })).status, 403);
  db["orders/o1"].paymentClaim = { method: "x", at: iso(-3600e3) };
  assert.equal((await call("cust", { type: "paymentClaim", orderId: "o1" })).status, 403);
  delete db["orders/o1"].paymentClaim;
  assert.equal((await call("cust", { type: "paymentClaim", orderId: "o1" })).status, 403);
});
await t("payment endpoints are gone", async () => {
  for (const p of ["/pay", "/ipn", "/withdraw", "/withdrawal", "/paycheck"]) {
    const r = await worker.fetch(new Request("https://w" + p, { method: "POST", body: "{}" }), env, {}); assert.equal(r.status, 404);
  }
});
// ------------------------------------------------------------------ e-mail code (OTP)
const otp = (path, body = {}, claims = {}) => call("newu", body, idToken("newu", { email: "new@user.dev", email_verified: false, ...claims }), path);
const codeFromMail = () => mails.at(-1).subject.match(/(\d{6})/)[1];
await t("sends a 6-digit code to the token's own e-mail, stored hashed", async () => {
  const r = await otp("/otp/send", { email: "victim@evil.dev" });
  assert.equal(r.sent, true); assert.equal(mails[0].to[0].email, "new@user.dev"); assert.match(mails[0].subject, /\d{6}/);
  assert.equal(mails[0].sender.email, "no-reply@medk.app");
  assert.ok(db["emailOtps/newu"].hash && !JSON.stringify(db["emailOtps/newu"]).includes(codeFromMail()));
});
await t("right code → Firebase account marked verified, code deleted", async () => {
  await otp("/otp/send"); const r = await otp("/otp/verify", { code: codeFromMail() });
  assert.equal(r.verified, true); assert.deepEqual(verifiedUsers[0], { localId: "newu", emailVerified: true });
  assert.equal(db["emailOtps/newu"], undefined);
});
await t("wrong codes are counted and lock after 5 attempts", async () => {
  await otp("/otp/send"); const good = codeFromMail(); const bad = good === "000000" ? "111111" : "000000";
  for (let i = 0; i < 5; i++) assert.equal((await otp("/otp/verify", { code: bad })).error, "otp_invalid");
  assert.equal((await otp("/otp/verify", { code: good })).error, "otp_locked"); assert.equal(verifiedUsers.length, 0);
});
await t("expired codes and malformed input refused", async () => {
  await otp("/otp/send"); db["emailOtps/newu"].expiresAt = iso(-1000);
  assert.equal((await otp("/otp/verify", { code: codeFromMail() })).error, "otp_expired");
  assert.equal((await otp("/otp/verify", { code: "12ab" })).error, "otp_invalid");
});
await t("resend cooldown and hourly limit", async () => {
  await otp("/otp/send"); assert.equal((await otp("/otp/send")).error, "otp_wait");
  db["emailOtps/newu"].sentAt = iso(-60_000); db["emailOtps/newu"].sends = 6;
  assert.equal((await otp("/otp/send")).error, "otp_too_many");
});
await t("someone else's code is useless; already-verified accounts skip; no token → 401", async () => {
  await otp("/otp/send"); const c = codeFromMail();
  const other = await call("other", { code: c }, idToken("other", { email: "new@user.dev", email_verified: false }), "/otp/verify");
  assert.equal(other.error, "otp_expired");
  assert.equal((await otp("/otp/send", {}, { email_verified: true })).verified, true);
  assert.equal((await call("x", {}, "bad", "/otp/send")).status, 401);
});
await t("not configured → clear error (app falls back to the e-mail link)", async () => {
  const r = await worker.fetch(new Request("https://w/otp/send", { method: "POST", headers: { authorization: `Bearer ${idToken("newu", { email: "a@b.c" })}` } }),
    { ...env, BREVO_API_KEY: undefined }, {});
  assert.equal((await r.json()).error, "otp_not_configured");
});
console.log(`\n  ${n} passing`);
