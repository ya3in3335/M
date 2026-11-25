/**
 * MED-K Cloud Functions. All privileged business logic lives here:
 * clients can never write orders, conversations, roles or permissions directly.
 */
import { onCall } from "firebase-functions/v2/https";
import { onDocumentCreated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { randomBytes } from "crypto";
import {
  auth, bool, caller, callOpts, db, fail, FieldValue, hasPerm, id, NO_PERMS, notify, OWNER_EMAILS, OWNER_PERMS,
  ownerIds, Perms, rateLimit, REGION, requireOwner, Role, str, UserDoc,
} from "./core";
import { seedCatalogIfEmpty } from "./seed";

const STATUSES = ["open", "paid", "delivered", "closed"] as const;
type Status = typeof STATUSES[number];
const STATUS_AR: Record<Status, string> = { open: "مفتوح", paid: "تم الدفع", delivered: "تم التسليم", closed: "مغلق" };

function publicUser(uid: string, u: UserDoc) {
  return {
    id: uid, email: u.email, full_name: u.fullName, avatar_url: u.avatarUrl, role: u.role, is_active: u.active,
    can_manage_products: u.perms.manageProducts, can_update_orders: u.perms.updateOrders,
    can_manage_p2p: u.perms.manageP2p, can_view_stats: u.perms.viewStats,
    created_at: u.createdAt?.toDate().toISOString() ?? null,
  };
}

async function syncClaims(uid: string, role: Role) {
  const rec = await auth.getUser(uid);
  if (rec.customClaims?.role !== role) {
    await auth.setCustomUserClaims(uid, { ...(rec.customClaims ?? {}), role });
    return true;
  }
  return false;
}

function isOwnerEmail(email: string | undefined): boolean {
  const e = (email ?? "").trim().toLowerCase();
  const owners = OWNER_EMAILS.value().split(",").map((x) => x.trim().toLowerCase()).filter(Boolean);
  return e.length > 0 && owners.includes(e);
}

// ================================================================ bootstrap
/** Called after every sign-in: creates the profile, syncs the role claim. */
export const bootstrap = onCall(callOpts(), async (req) => {
  if (!req.auth) fail("unauthenticated", "not authenticated");
  const uid = req.auth!.uid;
  const token = req.auth!.token;
  const ref = db.doc(`users/${uid}`);
  let snap = await ref.get();
  if (!snap.exists) {
    // Owner promotion only for VERIFIED e-mails, so nobody can pre-register the owner's address.
    const role: Role = token.email_verified === true && isOwnerEmail(token.email) ? "owner" : "customer";
    const fullName = (str((req.data as { full_name?: unknown })?.full_name, "full_name", 80, false) ?? token.name ?? null) as string | null;
    const doc: UserDoc = {
      email: token.email ?? null, fullName, avatarUrl: (token.picture as string | undefined) ?? null,
      role, active: true, perms: role === "owner" ? OWNER_PERMS : NO_PERMS, sections: [],
      createdAt: FieldValue.serverTimestamp() as never,
    };
    await ref.create(doc);
    if (role === "owner") await seedCatalogIfEmpty();
    snap = await ref.get();
  }
  let user = snap.data() as UserDoc;
  if (!user.active) fail("permission-denied", "account_disabled");
  // An owner who signed up with e-mail/password is promoted once the address is verified.
  if (user.role === "customer" && token.email_verified === true && isOwnerEmail(token.email)) {
    await ref.update({ role: "owner", perms: OWNER_PERMS, updatedAt: FieldValue.serverTimestamp() });
    await seedCatalogIfEmpty();
    user = (await ref.get()).data() as UserDoc;
  }
  const claimsUpdated = await syncClaims(uid, user.role);
  return { profile: publicUser(uid, user), claims_updated: claimsUpdated };
});

// Keep the public card (name/avatar/role) in sync with the private profile.
export const syncPublicProfile = onDocumentWritten({ document: "users/{uid}", region: REGION }, async (ev) => {
  const after = ev.data?.after;
  const ref = db.doc(`publicProfiles/${ev.params.uid}`);
  if (!after?.exists) { await ref.delete(); return; }
  const u = after.data() as UserDoc;
  await ref.set({ displayName: u.fullName ?? (u.role === "customer" ? "عميل" : "فريق MED-K"), avatarUrl: u.avatarUrl ?? null, role: u.role });
});

// ================================================================= checkout
/** Creates one order (+ conversation) per assigned seller. Prices are read server-side. */
export const checkout = onCall(callOpts(), async (req) => {
  const c = await caller(req);
  await rateLimit(c.uid, "checkout", 10, 60);
  const data = req.data as { items?: unknown; note?: unknown };
  if (!Array.isArray(data.items) || data.items.length < 1 || data.items.length > 50) fail("invalid-argument", "empty cart");
  const note = str(data.note, "note", 500, false);
  const wanted = new Map<string, number>();
  for (const it of data.items as Array<{ product_id?: unknown; quantity?: unknown }>) {
    const pid = id(it.product_id, "product_id")!;
    const q = Number(it.quantity);
    if (!Number.isInteger(q) || q < 1 || q > 99) fail("invalid-argument", "quantity invalid");
    wanted.set(pid, (wanted.get(pid) ?? 0) + q);
  }
  const owners = await ownerIds();

  const created = await db.runTransaction(async (tx) => {
    const prodSnaps = await tx.getAll(...[...wanted.keys()].map((p) => db.doc(`products/${p}`)));
    const lines = prodSnaps.filter((s) => s.exists && s.get("available") === true).map((s) => ({
      id: s.id, name: s.get("name") as string, image: ((s.get("images") as string[] | undefined) ?? [])[0] ?? null,
      usd: Number(s.get("priceUsd")), dzd: Number(s.get("priceDzd")), stock: s.get("stock") as number | null,
      sectionId: (s.get("sectionId") as string | null) ?? null, qty: wanted.get(s.id)!,
    }));
    if (lines.length === 0) fail("failed-precondition", "no available products");
    if (lines.some((l) => l.stock !== null && l.stock !== undefined && l.stock < l.qty)) fail("failed-precondition", "insufficient stock");

    const secIds = [...new Set(lines.map((l) => l.sectionId).filter((s): s is string => !!s))];
    const secSnaps = secIds.length ? await tx.getAll(...secIds.map((s) => db.doc(`sections/${s}`))) : [];
    const sec = new Map(secSnaps.filter((s) => s.exists).map((s) => [s.id, { staff: (s.get("assignedStaffId") as string | null) ?? null, name: s.get("name") as string }]));

    const groups = new Map<string, typeof lines>();
    for (const l of lines) {
      const staff = (l.sectionId && sec.get(l.sectionId)?.staff) || "";
      groups.set(staff, [...(groups.get(staff) ?? []), l]);
    }
    const out: Array<{ order_id: string; conversation_id: string; reference: string; staff: string | null; usd: number }> = [];
    for (const [staffKey, group] of groups) {
      const staffId = staffKey || null;
      const ref = "MK-" + randomBytes(5).toString("hex").toUpperCase().slice(0, 8);
      const usd = Math.round(group.reduce((a, l) => a + l.usd * l.qty, 0) * 100) / 100;
      const dzd = Math.round(group.reduce((a, l) => a + l.dzd * l.qty, 0));
      const sections = [...new Set(group.map((l) => l.sectionId).filter((s): s is string => !!s))];
      const orderRef = db.collection("orders").doc();
      const convRef = db.collection("conversations").doc();
      tx.create(orderRef, {
        reference: ref, customerId: c.uid, staffId, sectionId: sections.length === 1 ? sections[0] : null,
        status: "open", totalUsd: usd, totalDzd: dzd, note: note ?? null,
        items: group.map((l) => ({ productId: l.id, productName: l.name, imageUrl: l.image, quantity: l.qty, unitPriceUsd: l.usd, unitPriceDzd: l.dzd })),
        createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
        paidAt: null, deliveredAt: null, closedAt: null,
      });
      for (const l of group) if (l.stock !== null && l.stock !== undefined) tx.update(db.doc(`products/${l.id}`), { stock: FieldValue.increment(-l.qty) });
      const names = sections.map((s) => sec.get(s)?.name).filter(Boolean).join("، ");
      const body = `مرحباً، أرغب في إتمام الطلب ${ref}. الإجمالي: ${usd.toFixed(2)} USD / ${dzd} DZD`;
      tx.create(convRef, {
        customerId: c.uid, staffId, orderId: orderRef.id, productId: null,
        sectionId: sections.length === 1 ? sections[0] : null,
        subject: `طلب ${ref}` + (names ? ` — ${names}` : ""), lastMessage: body,
        lastMessageAt: FieldValue.serverTimestamp(), createdAt: FieldValue.serverTimestamp(),
      });
      tx.create(convRef.collection("messages").doc(), { senderId: c.uid, body, attachment: null, createdAt: FieldValue.serverTimestamp() });
      out.push({ order_id: orderRef.id, conversation_id: convRef.id, reference: ref, staff: staffId, usd });
    }
    return out;
  });

  await Promise.all(created.flatMap((o) => [
    notify(o.staff, "طلب جديد", `الطلب ${o.reference}`, { orderId: o.order_id, conversationId: o.conversation_id }),
    ...owners.map((ow) => notify(ow, "طلب جديد في المتجر", `الطلب ${o.reference} — ${o.usd.toFixed(2)} USD`, { orderId: o.order_id, conversationId: o.conversation_id })),
  ]));
  return created.map(({ order_id, conversation_id, reference }) => ({ order_id, conversation_id, reference }));
});

// ======================================================== conversations
/** Opens (or reuses) a conversation with the seller of a product/section, or support. */
export const startConversation = onCall(callOpts(), async (req) => {
  const c = await caller(req);
  await rateLimit(c.uid, "startConversation", 20, 60);
  const data = req.data as { product_id?: unknown; section_id?: unknown; subject?: unknown };
  const productId = id(data.product_id, "product_id", false);
  let sectionId = id(data.section_id, "section_id", false);
  const subject = str(data.subject, "subject", 120, false) ?? "الدعم الفني";
  if (productId) {
    const p = await db.doc(`products/${productId}`).get();
    if (!p.exists) fail("not-found", "product not found");
    sectionId = (p.get("sectionId") as string | null) ?? null;
  }
  let staffId: string | null = null;
  if (sectionId) {
    const s = await db.doc(`sections/${sectionId}`).get();
    if (!s.exists) fail("not-found", "section not found");
    staffId = (s.get("assignedStaffId") as string | null) ?? null;
  }
  const existing = await db.collection("conversations")
    .where("customerId", "==", c.uid).where("orderId", "==", null)
    .where("productId", "==", productId).where("sectionId", "==", sectionId).limit(1).get();
  if (!existing.empty) return { conversation_id: existing.docs[0].id };
  const ref = await db.collection("conversations").add({
    customerId: c.uid, staffId, orderId: null, productId, sectionId, subject,
    lastMessage: null, lastMessageAt: FieldValue.serverTimestamp(), createdAt: FieldValue.serverTimestamp(),
  });
  return { conversation_id: ref.id };
});

/** Denormalises the last message and notifies the other side. */
export const onMessageCreated = onDocumentCreated({ document: "conversations/{cid}/messages/{mid}", region: REGION }, async (ev) => {
  const m = ev.data?.data();
  if (!m) return;
  const convRef = db.doc(`conversations/${ev.params.cid}`);
  const conv = (await convRef.get()).data();
  if (!conv) return;
  const kind: Record<string, string> = { image: "📷 صورة", video: "🎬 فيديو", pdf: "📄 ملف PDF" };
  const preview = (m.body as string | null) || kind[(m.attachment?.type as string) ?? ""] || "";
  await convRef.update({ lastMessage: preview.slice(0, 200), lastMessageAt: m.createdAt ?? FieldValue.serverTimestamp() });
  if (m.senderId === conv.customerId) {
    const targets = conv.staffId ? [conv.staffId as string] : await ownerIds();
    await Promise.all(targets.map((t) => notify(t, "رسالة جديدة", preview.slice(0, 140), { conversationId: ev.params.cid })));
  } else {
    await notify(conv.customerId as string, "رسالة جديدة", preview.slice(0, 140), { conversationId: ev.params.cid });
  }
});

// ================================================================== orders
export const setOrderStatus = onCall(callOpts(), async (req) => {
  const c = await caller(req);
  const data = req.data as { order_id?: unknown; status?: unknown };
  const orderId = id(data.order_id, "order_id")!;
  const status = data.status as Status;
  if (!STATUSES.includes(status)) fail("invalid-argument", "status invalid");
  const ref = db.doc(`orders/${orderId}`);
  const o = await ref.get();
  if (!o.exists) fail("not-found", "order not found");
  const allowed = c.user.role === "owner" || (c.user.role === "staff" && o.get("staffId") === c.uid && hasPerm(c, "updateOrders"));
  if (!allowed) fail("permission-denied", "permission denied");
  const now = FieldValue.serverTimestamp();
  const patch: Record<string, unknown> = { status, updatedAt: now };
  if (status !== "open" && !o.get("paidAt")) patch.paidAt = now;
  if ((status === "delivered" || status === "closed") && !o.get("deliveredAt")) patch.deliveredAt = now;
  if (status === "closed") patch.closedAt = now;
  await ref.update(patch);
  await notify(o.get("customerId"), `تحديث الطلب ${o.get("reference")}`, `حالة طلبك الآن: ${STATUS_AR[status]}`, { orderId });
  return { ok: true };
});

export const assignOrder = onCall(callOpts(), async (req) => {
  const c = await caller(req); requireOwner(c);
  const data = req.data as { order_id?: unknown; staff_id?: unknown };
  const orderId = id(data.order_id, "order_id")!;
  const staffId = id(data.staff_id, "staff_id", false);
  if (staffId) {
    const s = await db.doc(`users/${staffId}`).get();
    if (!s.exists || !s.get("active") || !["staff", "owner"].includes(s.get("role"))) fail("invalid-argument", "not a seller");
  }
  const o = await db.doc(`orders/${orderId}`).get();
  if (!o.exists) fail("not-found", "order not found");
  const batch = db.batch();
  batch.update(o.ref, { staffId, updatedAt: FieldValue.serverTimestamp() });
  const convs = await db.collection("conversations").where("orderId", "==", orderId).get();
  convs.forEach((d) => batch.update(d.ref, { staffId }));
  await batch.commit();
  await notify(staffId, "طلب جديد مُسند إليك", `الطلب ${o.get("reference")}`, { orderId });
  return { ok: true };
});

export const dashboardStats = onCall(callOpts(), async (req) => {
  const c = await caller(req);
  if (!hasPerm(c, "viewStats")) fail("permission-denied", "forbidden");
  const { AggregateField } = await import("firebase-admin/firestore");
  const owner = c.user.role === "owner";
  const base = owner ? db.collection("orders") : db.collection("orders").where("staffId", "==", c.uid);
  const count = async (q: FirebaseFirestore.Query) => (await q.count().get()).data().count;
  const [total, open, paid, delivered, closed, unassigned, revenue, convs, products, staff, customers] = await Promise.all([
    count(base), count(base.where("status", "==", "open")), count(base.where("status", "==", "paid")),
    count(base.where("status", "==", "delivered")), count(base.where("status", "==", "closed")),
    owner ? count(db.collection("orders").where("staffId", "==", null).where("status", "in", ["open", "paid", "delivered"])) : Promise.resolve(0),
    base.where("status", "in", ["paid", "delivered", "closed"])
      .aggregate({ usd: AggregateField.sum("totalUsd"), dzd: AggregateField.sum("totalDzd") }).get(),
    count(owner ? db.collection("conversations") : db.collection("conversations").where("staffId", "==", c.uid)),
    count(db.collection("products")),
    owner ? count(db.collection("users").where("role", "==", "staff")) : Promise.resolve(0),
    owner ? count(db.collection("users").where("role", "==", "customer")) : Promise.resolve(0),
  ]);
  return {
    total_orders: total, open_orders: open, paid_orders: paid, delivered_orders: delivered, closed_orders: closed,
    revenue_usd: revenue.data().usd ?? 0, revenue_dzd: revenue.data().dzd ?? 0,
    open_conversations: convs, unassigned_orders: unassigned, products, staff, customers,
  };
});

// ============================================================ owner admin
function parsePerms(v: unknown): Perms {
  const p = (v ?? {}) as Record<string, unknown>;
  return { manageProducts: bool(p.manage_products), updateOrders: bool(p.update_orders, true),
           manageP2p: bool(p.manage_p2p), viewStats: bool(p.view_stats) };
}

async function targetUser(uid: string) {
  const s = await db.doc(`users/${uid}`).get();
  if (!s.exists) fail("not-found", "user not found");
  const u = s.data() as UserDoc;
  if (u.role === "owner") fail("failed-precondition", "cannot change an owner account");
  return u;
}

export const adminCreateStaff = onCall(callOpts(), async (req) => {
  const c = await caller(req); requireOwner(c);
  await rateLimit(c.uid, "adminCreateStaff", 20, 3600);
  const d = req.data as { email?: unknown; password?: unknown; full_name?: unknown; perms?: unknown };
  const email = str(d.email, "email", 120)!.toLowerCase();
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) fail("invalid-argument", "email invalid");
  const password = str(d.password, "password", 128)!;
  if (password.length < 8) fail("invalid-argument", "password must be at least 8 characters");
  const fullName = str(d.full_name, "full_name", 80)!;
  const perms = parsePerms(d.perms);
  const rec = await auth.createUser({ email, password, displayName: fullName, emailVerified: true });
  await auth.setCustomUserClaims(rec.uid, { role: "staff" });
  const doc: UserDoc = { email, fullName, avatarUrl: null, role: "staff", active: true, perms, sections: [], createdAt: FieldValue.serverTimestamp() as never };
  await db.doc(`users/${rec.uid}`).create(doc);
  return { profile: publicUser(rec.uid, { ...doc, createdAt: undefined as never }) };
});

export const adminSetPassword = onCall(callOpts(), async (req) => {
  const c = await caller(req); requireOwner(c);
  const d = req.data as { uid?: unknown; password?: unknown };
  const uid = id(d.uid, "uid")!;
  const password = str(d.password, "password", 128)!;
  if (password.length < 8) fail("invalid-argument", "password must be at least 8 characters");
  const u = await targetUser(uid);
  if (u.role !== "staff") fail("failed-precondition", "target is not a seller");
  await auth.updateUser(uid, { password });
  await auth.revokeRefreshTokens(uid);
  return { ok: true };
});

export const adminSetActive = onCall(callOpts(), async (req) => {
  const c = await caller(req); requireOwner(c);
  const d = req.data as { uid?: unknown; active?: unknown };
  const uid = id(d.uid, "uid")!;
  if (uid === c.uid) fail("failed-precondition", "cannot change your own account");
  await targetUser(uid);
  const active = bool(d.active);
  await auth.updateUser(uid, { disabled: !active });
  if (!active) await auth.revokeRefreshTokens(uid);
  await db.doc(`users/${uid}`).update({ active, updatedAt: FieldValue.serverTimestamp() });
  return { ok: true };
});

export const adminSetRole = onCall(callOpts(), async (req) => {
  const c = await caller(req); requireOwner(c);
  const d = req.data as { uid?: unknown; role?: unknown };
  const uid = id(d.uid, "uid")!;
  const role = d.role as Role;
  if (role !== "customer" && role !== "staff") fail("invalid-argument", "invalid role");
  if (uid === c.uid) fail("failed-precondition", "cannot change your own role");
  await targetUser(uid);
  await db.doc(`users/${uid}`).update({ role, perms: NO_PERMS, sections: [], updatedAt: FieldValue.serverTimestamp() });
  await auth.setCustomUserClaims(uid, { role });
  await auth.revokeRefreshTokens(uid);
  return { ok: true };
});

export const adminSetPermissions = onCall(callOpts(), async (req) => {
  const c = await caller(req); requireOwner(c);
  const d = req.data as { uid?: unknown; perms?: unknown; sections?: unknown };
  const uid = id(d.uid, "uid")!;
  const u = await targetUser(uid);
  if (u.role !== "staff") fail("failed-precondition", "target is not a seller");
  if (!Array.isArray(d.sections) || d.sections.length > 200) fail("invalid-argument", "sections invalid");
  const sections = [...new Set((d.sections as unknown[]).map((s) => id(s, "section")!))];
  if (sections.length) {
    const snaps = await db.getAll(...sections.map((s) => db.doc(`sections/${s}`)));
    if (snaps.some((s) => !s.exists)) fail("invalid-argument", "unknown section");
  }
  await db.doc(`users/${uid}`).update({ perms: parsePerms(d.perms), sections, updatedAt: FieldValue.serverTimestamp() });
  return { ok: true };
});
