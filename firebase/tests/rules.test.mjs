// MED-K security-rules tests (free plan: no server code — the rules are the server).
import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc, addDoc, collection, getDocs, query, where, serverTimestamp, deleteDoc, writeBatch } from "firebase/firestore";
import { readFileSync } from "fs";

let env;
const P = { manageProducts: false, updateOrders: true, manageP2p: false, viewStats: false };
const users = {
  owner: { role: "owner", perms: { manageProducts: true, updateOrders: true, manageP2p: true, viewStats: true }, sections: [] },
  cust: { role: "customer", perms: P, sections: [] },
  cust2: { role: "customer", perms: P, sections: [] },
  banned: { role: "customer", perms: P, sections: [], active: false },
  st1: { role: "staff", perms: { ...P, manageProducts: true }, sections: ["s_ff"] },
  st2: { role: "staff", perms: { ...P, updateOrders: false }, sections: [] },
  stp2p: { role: "staff", perms: { ...P, manageP2p: true }, sections: [] },
};
const ctx = (id, extra = {}) => env.authenticatedContext(id, { email: `${id}@t.dev`, email_verified: true, ...extra });
const as = (id, extra) => ctx(id, extra).firestore();
const product = (o = {}) => ({ categoryId: "gaming", sectionId: "s_ff", name: "100 Diamonds", description: null, priceUsd: 1.5, priceDzd: 270,
  stock: null, available: true, images: [], createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...o });
const order = (o = {}, item = {}) => ({ reference: "MK-ABCD1234", customerId: "cust", staffId: "st1", sectionId: "s_ff", status: "open",
  totalUsd: 3, totalDzd: 540, note: null,
  items: [{ productId: "visible", productName: "100 Diamonds", imageUrl: null, quantity: 2, unitPriceUsd: 1.5, unitPriceDzd: 270, ...item }],
  createdAt: serverTimestamp(), updatedAt: serverTimestamp(), paidAt: null, deliveredAt: null, closedAt: null, ...o });

describe("security rules (free plan)", () => {
  before(async () => {
    await (await import("child_process")).execSync("npm run -s rules");
    env = await initializeTestEnvironment({
      projectId: "demo-medk",
      firestore: { rules: readFileSync("../firestore.rules", "utf8"), host: "127.0.0.1", port: 8080 },
    });
  });
  after(async () => { await env.cleanup(); });
  beforeEach(async () => {
    await env.clearFirestore();
    await env.withSecurityRulesDisabled(async (c) => {
      const db = c.firestore();
      for (const [id, u] of Object.entries(users)) await setDoc(doc(db, `users/${id}`), { email: `${id}@t.dev`, fullName: id, avatarUrl: null, active: true, ...u });
      await setDoc(doc(db, "categories/gaming"), { slug: "gaming", name: "شحن الألعاب", sortOrder: 1, active: true });
      await setDoc(doc(db, "sections/s_ff"), { categoryId: "gaming", name: "Free Fire", assignedStaffId: "st1", sortOrder: 1, active: true });
      await setDoc(doc(db, "sections/s_pubg"), { categoryId: "gaming", name: "PUBG", assignedStaffId: null, sortOrder: 2, active: true });
      await setDoc(doc(db, "products/visible"), { ...product(), createdAt: new Date(), updatedAt: new Date() });
      await setDoc(doc(db, "products/stocked"), { ...product({ stock: 5 }), createdAt: new Date(), updatedAt: new Date() });
      await setDoc(doc(db, "products/pkg"), { ...product({ name: "جواهر Free Fire", pricing: "packages", unit: "جوهرة", inputLabel: "ID اللاعب",
        priceUsd: 1, priceDzd: 250, packages: [{ amount: 100, priceDzd: 250, priceUsd: 1 }, { amount: 310, priceDzd: 750, priceUsd: 3.1 }] }),
        createdAt: new Date(), updatedAt: new Date() });
      await setDoc(doc(db, "products/per"), { ...product({ name: "شدات PUBG", pricing: "perUnit", unit: "شدة", priceUsd: 1.2, priceDzd: 250,
        unitBase: 100, minAmount: 60, maxAmount: 10000 }), createdAt: new Date(), updatedAt: new Date() });
      await setDoc(doc(db, "products/hidden"), { ...product({ available: false }), createdAt: new Date(), updatedAt: new Date() });
      await setDoc(doc(db, "orders/o1"), { ...order(), createdAt: new Date(), updatedAt: new Date() });
      await setDoc(doc(db, "conversations/c1"), { customerId: "cust", staffId: "st1", orderId: "o1", subject: "x", lastMessage: null, lastMessageAt: new Date(), lastSenderId: "cust" });
    });
  });

  describe("sign-up & roles", () => {
    const profile = (o = {}) => ({ email: "new@t.dev", fullName: "New", avatarUrl: null, role: "customer", active: true,
      perms: P, sections: [], createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...o });
    it("new user creates his customer profile — only after verifying his e-mail", async () => {
      await assertFails(setDoc(doc(env.authenticatedContext("new", { email: "new@t.dev" }).firestore(), "users/new"), profile()));
      await assertFails(setDoc(doc(env.authenticatedContext("new", { email: "new@t.dev", email_verified: false }).firestore(), "users/new"), profile()));
      await assertSucceeds(setDoc(doc(env.authenticatedContext("new", { email: "new@t.dev", email_verified: true }).firestore(), "users/new"), profile()));
    });
    it("STRICT: an unverified e-mail can read or write nothing (except the public catalog)", async () => {
      const un = env.authenticatedContext("cust", { email: "cust@t.dev", email_verified: false }).firestore();
      await assertFails(getDoc(doc(un, "users/cust")));
      await assertFails(getDoc(doc(un, "orders/o1")));
      await assertFails(getDoc(doc(un, "conversations/c1")));
      await assertFails(setDoc(doc(un, "users/cust/favorites/visible"), { createdAt: serverTimestamp() }));
      await assertFails(updateDoc(doc(un, "orders/o1"), { paymentClaim: { method: "x", at: serverTimestamp() }, updatedAt: serverTimestamp() }));
      await assertFails(getDoc(doc(un, "config/payments")));
      await assertSucceeds(getDoc(doc(un, "categories/gaming")));
      const unOwner = env.authenticatedContext("owner", { email: "owner@t.dev", email_verified: false }).firestore();
      await assertFails(getDocs(collection(unOwner, "users")));
    });
    it("new user cannot create himself as owner or staff", async () => {
      const db = env.authenticatedContext("new", { email: "new@t.dev", email_verified: true }).firestore();
      await assertFails(setDoc(doc(db, "users/new"), profile({ role: "owner", perms: users.owner.perms })));
      await assertFails(setDoc(doc(db, "users/new"), profile({ role: "staff" })));
      await assertFails(setDoc(doc(db, "users/new"), profile({ perms: { ...P, manageProducts: true } })));
    });
    it("owner e-mail becomes owner only when verified", async () => {
      const unverified = env.authenticatedContext("boss", { email: "owner@test.dev", email_verified: false }).firestore();
      await assertFails(setDoc(doc(unverified, "users/boss"), profile({ email: "owner@test.dev", role: "owner", perms: users.owner.perms })));
      const ok = env.authenticatedContext("boss", { email: "owner@test.dev", email_verified: true }).firestore();
      await assertSucceeds(setDoc(doc(ok, "users/boss"), profile({ email: "owner@test.dev", role: "owner", perms: users.owner.perms })));
    });
    it("customer cannot make himself owner / grant permissions / reactivate", async () => {
      await assertFails(updateDoc(doc(as("cust"), "users/cust"), { role: "owner" }));
      await assertFails(updateDoc(doc(as("st2"), "users/st2"), { "perms.manageP2p": true }));
      await assertFails(updateDoc(doc(as("banned"), "users/banned"), { active: true }));
    });
    it("customer renames himself", async () => {
      await assertSucceeds(updateDoc(doc(as("cust"), "users/cust"), { fullName: "New name", updatedAt: serverTimestamp() }));
    });
    it("privacy: no reading or listing other profiles", async () => {
      await assertFails(getDoc(doc(as("cust"), "users/cust2")));
      await assertFails(getDocs(collection(as("st1"), "users")));
      await assertSucceeds(getDocs(collection(as("owner"), "users")));
    });
    it("owner creates a seller, sets permissions, suspends a customer", async () => {
      const db = as("owner");
      await assertSucceeds(setDoc(doc(db, "users/newstaff"), { email: "s@t.dev", fullName: "S", avatarUrl: null, role: "staff", active: true,
        perms: { ...P, manageProducts: true }, sections: ["s_ff"], createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
      await assertSucceeds(updateDoc(doc(db, "users/st2"), { perms: { ...P, viewStats: true }, sections: ["s_pubg"], updatedAt: serverTimestamp() }));
      await assertSucceeds(updateDoc(doc(db, "users/cust2"), { active: false, updatedAt: serverTimestamp() }));
    });
    it("owner cannot demote himself or another owner, nor make a new owner", async () => {
      await assertFails(updateDoc(doc(as("owner"), "users/owner"), { role: "customer", updatedAt: serverTimestamp() }));
      await assertFails(updateDoc(doc(as("owner"), "users/cust"), { role: "owner", updatedAt: serverTimestamp() }));
    });
    it("public card role must match the real role", async () => {
      await assertFails(setDoc(doc(as("cust"), "publicProfiles/cust"), { displayName: "x", avatarUrl: null, role: "owner" }));
      await assertSucceeds(setDoc(doc(as("cust"), "publicProfiles/cust"), { displayName: "x", avatarUrl: null, role: "customer" }));
      await assertFails(setDoc(doc(as("cust"), "publicProfiles/cust2"), { displayName: "x", avatarUrl: null, role: "customer" }));
    });
  });

  describe("quantity pricing: packages & per-unit", () => {
    const pkgOrder = (idx, o = {}, it = {}) => {
      const p = [{ amount: 100, priceDzd: 250, priceUsd: 1 }, { amount: 310, priceDzd: 750, priceUsd: 3.1 }][idx];
      return order({ totalUsd: p.priceUsd * 2, totalDzd: p.priceDzd * 2, ...o },
        { productId: "pkg", productName: "جواهر Free Fire", quantity: 2, unitPriceUsd: p.priceUsd, unitPriceDzd: p.priceDzd,
          amount: p.amount, packageIndex: idx, unit: "جوهرة", input: "123456789", ...it });
    };
    const unitOrder = (amount, o = {}, it = {}) => order({ totalUsd: 1.2 * amount / 100, totalDzd: 250 * amount / 100, ...o },
      { productId: "per", productName: "شدات PUBG", quantity: 1, unitPriceUsd: 1.2 * amount / 100, unitPriceDzd: 250 * amount / 100,
        amount, unit: "شدة", ...it });
    it("a package is bought at exactly its price", async () => {
      await assertSucceeds(setDoc(doc(as("cust"), "orders/p1"), pkgOrder(1)));
      await assertSucceeds(setDoc(doc(as("cust"), "orders/p2"), pkgOrder(0)));
    });
    it("package tampering is rejected (price, amount, index, missing player ID)", async () => {
      await assertFails(setDoc(doc(as("cust"), "orders/x"), pkgOrder(1, { totalDzd: 500 }, { unitPriceDzd: 250 })));
      await assertFails(setDoc(doc(as("cust"), "orders/x"), pkgOrder(0, {}, { amount: 310 })));
      await assertFails(setDoc(doc(as("cust"), "orders/x"), pkgOrder(1, {}, { packageIndex: 5 })));
      await assertFails(setDoc(doc(as("cust"), "orders/x"), pkgOrder(1, {}, { input: null })));
      await assertFails(setDoc(doc(as("cust"), "orders/x"), pkgOrder(1, {}, { unit: "UC" })));
      await assertFails(setDoc(doc(as("cust"), "orders/x"), order({}, { productId: "pkg", productName: "جواهر Free Fire" })));   // no package chosen
    });
    it("per-unit: price follows the typed amount, within min / max", async () => {
      await assertSucceeds(setDoc(doc(as("cust"), "orders/u1"), unitOrder(750)));
      await assertSucceeds(setDoc(doc(as("cust"), "orders/u2"), unitOrder(60)));
      await assertFails(setDoc(doc(as("cust"), "orders/x"), unitOrder(50)));       // below minimum
      await assertFails(setDoc(doc(as("cust"), "orders/x"), unitOrder(20000)));    // above maximum
      await assertFails(setDoc(doc(as("cust"), "orders/x"), unitOrder(750, { totalDzd: 100 }, { unitPriceDzd: 100 })));
      await assertFails(setDoc(doc(as("cust"), "orders/x"), unitOrder(750, { totalDzd: 250 * 7.5 * 2, totalUsd: 1.2 * 7.5 * 2 }, { quantity: 2 })));
    });
    it("fixed products keep working and cannot smuggle an amount", async () => {
      await assertSucceeds(setDoc(doc(as("cust"), "orders/f1"), order()));
      await assertFails(setDoc(doc(as("cust"), "orders/x"), order({}, { amount: 1000, unit: "جوهرة" })));
    });
    it("sellers save packages / per-unit products; malformed pricing refused", async () => {
      const base = (o) => ({ ...product(o), createdAt: serverTimestamp() });
      await assertSucceeds(setDoc(doc(as("st1"), "products/n1"), base({ pricing: "packages", unit: "جوهرة",
        packages: [{ amount: 100, priceDzd: 250, priceUsd: 1 }], inputLabel: "ID اللاعب" })));
      await assertSucceeds(setDoc(doc(as("st1"), "products/n2"), base({ pricing: "perUnit", unit: "شدة", unitBase: 100, minAmount: 60, maxAmount: 5000 })));
      await assertFails(setDoc(doc(as("st1"), "products/n3"), base({ pricing: "packages", packages: [] })));
      await assertFails(setDoc(doc(as("st1"), "products/n4"), base({ pricing: "perUnit", unitBase: 0, minAmount: 1, maxAmount: 5 })));
      await assertFails(setDoc(doc(as("st1"), "products/n5"), base({ pricing: "perUnit", unitBase: 100, minAmount: 500, maxAmount: 100 })));
      await assertFails(setDoc(doc(as("st1"), "products/n6"), base({ pricing: "auction" })));
    });
  });

  describe("orders (prices checked against the database)", () => {
    it("customer places a correct order", async () => { await assertSucceeds(setDoc(doc(as("cust"), "orders/new1"), order())); });
    it("tampered price is rejected", async () => {
      await assertFails(setDoc(doc(as("cust"), "orders/x"), order({ totalUsd: 0.01 })));
      await assertFails(setDoc(doc(as("cust"), "orders/x"), order({}, { unitPriceUsd: 0.1 })));
      await assertFails(setDoc(doc(as("cust"), "orders/x"), order({ totalUsd: 1.5 })));   // wrong quantity math
    });
    it("cannot create an already paid order", async () => { await assertFails(setDoc(doc(as("cust"), "orders/x"), order({ status: "paid" }))); });
    it("cannot pick another seller", async () => { await assertFails(setDoc(doc(as("cust"), "orders/x"), order({ staffId: "st2" }))); });
    it("cannot order for someone else", async () => { await assertFails(setDoc(doc(as("cust2"), "orders/x"), order())); });
    it("cannot order hidden products", async () => {
      await assertFails(setDoc(doc(as("cust"), "orders/x"), order({}, { productId: "hidden" })));
    });
    it("unverified e-mail cannot order", async () => {
      await assertFails(setDoc(doc(as("cust", { email_verified: false }), "orders/x"), order()));
    });
    it("stock: order must decrement stock exactly in the same batch", async () => {
      const db = as("cust");
      await assertFails(setDoc(doc(db, "orders/s1"), order({}, { productId: "stocked" })));
      const b = writeBatch(db);
      b.set(doc(db, "orders/s1"), order({}, { productId: "stocked" }));
      b.update(doc(db, "products/stocked"), { stock: 3, lastOrderId: "s1" });
      await assertSucceeds(b.commit());
      const cheat = writeBatch(db);
      cheat.set(doc(db, "orders/s2"), order({}, { productId: "stocked" }));
      cheat.update(doc(db, "products/stocked"), { stock: 100, lastOrderId: "s2" });
      await assertFails(cheat.commit());
    });
    it("cannot over-order stock", async () => {
      const db = as("cust"); const b = writeBatch(db);
      b.set(doc(db, "orders/s3"), order({ totalUsd: 15, totalDzd: 2700 }, { productId: "stocked", quantity: 10 }));
      b.update(doc(db, "products/stocked"), { stock: -5, lastOrderId: "s3" });
      await assertFails(b.commit());
    });
    it("isolation: own / assigned / owner only", async () => {
      await assertSucceeds(getDoc(doc(as("cust"), "orders/o1")));
      await assertFails(getDoc(doc(as("cust2"), "orders/o1")));
      await assertSucceeds(getDoc(doc(as("st1"), "orders/o1")));
      await assertFails(getDoc(doc(as("st2"), "orders/o1")));
      await assertFails(getDocs(collection(as("cust"), "orders")));
      await assertSucceeds(getDocs(query(collection(as("cust"), "orders"), where("customerId", "==", "cust"))));
    });
    it("suspended account loses access", async () => {
      await env.withSecurityRulesDisabled(async (c) => setDoc(doc(c.firestore(), "orders/ob"), { ...order({ customerId: "banned" }), createdAt: new Date() }));
      await assertFails(getDoc(doc(as("banned"), "orders/ob")));
    });
    it("customer cannot change status; assigned seller with permission can", async () => {
      await assertFails(updateDoc(doc(as("cust"), "orders/o1"), { status: "paid", updatedAt: serverTimestamp(), paidAt: serverTimestamp() }));
      await assertSucceeds(updateDoc(doc(as("st1"), "orders/o1"), { status: "paid", updatedAt: serverTimestamp(), paidAt: serverTimestamp(), commissionPct: 0 }));
    });
    it("seller cannot change totals or reassign", async () => {
      await assertFails(updateDoc(doc(as("st1"), "orders/o1"), { totalUsd: 0, updatedAt: serverTimestamp() }));
      await assertFails(updateDoc(doc(as("st1"), "orders/o1"), { staffId: "st2", updatedAt: serverTimestamp() }));
    });
    it("seller without order permission cannot update", async () => {
      await env.withSecurityRulesDisabled(async (c) => updateDoc(doc(c.firestore(), "orders/o1"), { staffId: "st2" }));
      await assertFails(updateDoc(doc(as("st2"), "orders/o1"), { status: "paid", updatedAt: serverTimestamp(), paidAt: serverTimestamp() }));
    });
    it("owner reassigns an order", async () => {
      await assertSucceeds(updateDoc(doc(as("owner"), "orders/o1"), { staffId: "st2", updatedAt: serverTimestamp() }));
      await assertFails(updateDoc(doc(as("owner"), "orders/o1"), { staffId: "cust", updatedAt: serverTimestamp() }));
    });
  });

  describe("conversations & messages", () => {
    const msg = (o = {}) => ({ senderId: "cust", body: "hello", attachment: null, createdAt: serverTimestamp(), ...o });
    it("customer opens a conversation for his new order with its seller", async () => {
      const db = as("cust"); const b = writeBatch(db);
      b.set(doc(db, "orders/n1"), order());
      b.set(doc(db, "conversations/cn"), { customerId: "cust", staffId: "st1", orderId: "n1", productId: null, sectionId: "s_ff",
        subject: "طلب", lastMessage: "hi", lastMessageAt: serverTimestamp(), lastSenderId: "cust", createdAt: serverTimestamp() });
      b.set(doc(db, "conversations/cn/messages/m1"), { senderId: "cust", body: "hi", attachment: null, createdAt: serverTimestamp() });
      await assertSucceeds(b.commit());
    });
    it("cannot attach someone else's order or choose another seller", async () => {
      await assertFails(setDoc(doc(as("cust2"), "conversations/x"), { customerId: "cust2", staffId: "st1", orderId: "o1", productId: null, sectionId: null,
        subject: "x", lastMessage: null, lastMessageAt: serverTimestamp(), lastSenderId: "cust2", createdAt: serverTimestamp() }));
      await assertFails(setDoc(doc(as("cust"), "conversations/x"), { customerId: "cust", staffId: "st2", orderId: null, productId: null, sectionId: "s_ff",
        subject: "x", lastMessage: null, lastMessageAt: serverTimestamp(), lastSenderId: "cust", createdAt: serverTimestamp() }));
    });
    it("support conversation (no section) goes to the owner", async () => {
      await assertSucceeds(setDoc(doc(as("cust"), "conversations/sup"), { customerId: "cust", staffId: null, orderId: null, productId: null, sectionId: null,
        subject: "الدعم", lastMessage: null, lastMessageAt: serverTimestamp(), lastSenderId: "cust", createdAt: serverTimestamp() }));
    });
    it("participants message; outsiders and impostors cannot", async () => {
      await assertSucceeds(addDoc(collection(as("cust"), "conversations/c1/messages"), msg()));
      await assertSucceeds(addDoc(collection(as("st1"), "conversations/c1/messages"), msg({ senderId: "st1" })));
      await assertFails(addDoc(collection(as("cust"), "conversations/c1/messages"), msg({ senderId: "st1" })));
      await assertFails(addDoc(collection(as("cust2"), "conversations/c1/messages"), msg({ senderId: "cust2" })));
      await assertFails(getDocs(collection(as("cust2"), "conversations/c1/messages")));
      await assertFails(addDoc(collection(as("cust"), "conversations/c1/messages"), msg({ createdAt: new Date(2000, 1, 1) })));
    });
    it("attachments live in this conversation only; size limited", async () => {
      await assertSucceeds(setDoc(doc(as("cust"), "conversations/c1/files/f1"), { data: "aGVsbG8=", mime: "application/pdf", name: "a.pdf", createdBy: "cust", createdAt: serverTimestamp() }));
      await assertFails(setDoc(doc(as("cust2"), "conversations/c1/files/f2"), { data: "aGVsbG8=", mime: "application/pdf", name: "a.pdf", createdBy: "cust2", createdAt: serverTimestamp() }));
      await assertFails(setDoc(doc(as("cust"), "conversations/c1/files/f3"), { data: "x", mime: "application/x-msdownload", name: "a.exe", createdBy: "cust", createdAt: serverTimestamp() }));
      await assertFails(addDoc(collection(as("cust"), "conversations/c1/messages"), msg({ body: null, attachment: { path: "conversations/other/files/f1", type: "pdf", name: "x" } })));
      await assertSucceeds(addDoc(collection(as("cust"), "conversations/c1/messages"), msg({ body: null, attachment: { path: "conversations/c1/files/f1", type: "pdf", name: "a.pdf" } })));
    });
    it("last-message update by participants only", async () => {
      await assertSucceeds(updateDoc(doc(as("cust"), "conversations/c1"), { lastMessage: "x", lastMessageAt: serverTimestamp(), lastSenderId: "cust" }));
      await assertFails(updateDoc(doc(as("cust"), "conversations/c1"), { staffId: "st2" }));
      await assertFails(updateDoc(doc(as("cust2"), "conversations/c1"), { lastMessage: "x", lastMessageAt: serverTimestamp(), lastSenderId: "cust2" }));
    });
  });

  describe("instant notifications", () => {
    const tok = (o = {}) => ({ uid: "cust", token: "fcm-token-0123456789abcdefghij", app: "store", updatedAt: serverTimestamp(), ...o });
    it("a user registers and removes his own device token", async () => {
      await assertSucceeds(setDoc(doc(as("cust"), "pushTokens/t1"), tok()));
      await assertSucceeds(getDoc(doc(as("cust"), "pushTokens/t1")));
      await assertSucceeds(setDoc(doc(as("cust"), "pushTokens/t1"), tok({ app: "store" })));
      await assertSucceeds(deleteDoc(doc(as("cust"), "pushTokens/t1")));
    });
    it("nobody registers, reads, lists or deletes someone else's tokens", async () => {
      await assertFails(setDoc(doc(as("cust"), "pushTokens/t2"), tok({ uid: "cust2" })));
      await assertSucceeds(setDoc(doc(as("cust2"), "pushTokens/t2"), tok({ uid: "cust2" })));
      await assertFails(getDoc(doc(as("cust"), "pushTokens/t2")));
      await assertSucceeds(getDoc(doc(as("owner"), "pushTokens/t2")));
      await assertFails(deleteDoc(doc(as("owner"), "pushTokens/t2")));
      await assertSucceeds(getDocs(collection(as("owner"), "pushTokens")));   // owner: database explorer (read-only)
      await assertFails(getDocs(collection(as("st1"), "pushTokens")));
      await assertFails(setDoc(doc(as("cust"), "pushTokens/t2"), tok()));
      await assertFails(deleteDoc(doc(as("cust"), "pushTokens/t2")));
    });
    it("token documents are strictly validated; suspended users cannot register", async () => {
      await assertFails(setDoc(doc(as("cust"), "pushTokens/t3"), tok({ app: "other" })));
      await assertFails(setDoc(doc(as("cust"), "pushTokens/t3"), { ...tok(), extra: 1 }));
      await assertFails(setDoc(doc(as("cust"), "pushTokens/t3"), tok({ token: "x" })));
      await assertFails(setDoc(doc(as("banned"), "pushTokens/t3"), tok({ uid: "banned" })));
      await assertFails(setDoc(doc(env.unauthenticatedContext().firestore(), "pushTokens/t3"), tok()));
    });
    it("config is readable when signed in and never writable from the app", async () => {
      await env.withSecurityRulesDisabled(async (c) => { await setDoc(doc(c.firestore(), "config/push"), { url: "https://x.workers.dev" }); });
      await assertSucceeds(getDoc(doc(as("cust"), "config/push")));
      await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "config/push")));
      await assertFails(setDoc(doc(as("owner"), "config/push"), { url: "https://evil.example" }));
    });
  });

  describe("seller invitations", () => {
    const perms = { manageProducts: true, updateOrders: true, manageP2p: false, viewStats: false };
    const invite = (o = {}) => ({ email: "seller@t.dev", name: "Ali", perms, sections: ["s_pubg"], createdBy: "owner",
      createdAt: serverTimestamp(), acceptedBy: null, acceptedAt: null, ...o });
    const prof = (o = {}) => ({ email: "seller@t.dev", fullName: "Ali", avatarUrl: null, role: "staff", active: true, perms,
      sections: ["s_pubg"], createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...o });
    const seed = async (o = {}) => env.withSecurityRulesDisabled(async (c) => {
      await setDoc(doc(c.firestore(), "invites/seller@t.dev"), { ...invite(o), createdAt: new Date() });
    });
    const sellerDb = (verified = true) => env.authenticatedContext("newseller", { email: "seller@t.dev", email_verified: verified }).firestore();
    const accept = (db, userWrite) => {
      const b = writeBatch(db);
      userWrite(b);
      b.update(doc(db, "invites/seller@t.dev"), { acceptedBy: "newseller", acceptedAt: serverTimestamp() });
      return b;
    };

    it("only the owner creates, lists and deletes invitations", async () => {
      await assertSucceeds(setDoc(doc(as("owner"), "invites/seller@t.dev"), invite()));
      await assertSucceeds(getDocs(collection(as("owner"), "invites")));
      await assertFails(setDoc(doc(as("st1"), "invites/x@t.dev"), invite({ email: "x@t.dev", createdBy: "st1" })));
      await assertFails(setDoc(doc(as("cust"), "invites/cust@t.dev"), invite({ email: "cust@t.dev", createdBy: "cust" })));
      await assertFails(setDoc(doc(as("owner"), "invites/BAD@t.dev"), invite({ email: "BAD@t.dev" })));
      await assertFails(setDoc(doc(as("owner"), "invites/seller@t.dev"), invite({ acceptedBy: "someone" })));
      await assertFails(getDocs(collection(as("cust"), "invites")));
      await assertSucceeds(deleteDoc(doc(as("owner"), "invites/seller@t.dev")));
    });
    it("the invited (verified) e-mail signs up directly as a seller with the chosen permissions", async () => {
      await seed();
      const db = sellerDb();
      await assertSucceeds(getDoc(doc(db, "invites/seller@t.dev")));
      await assertSucceeds(accept(db, (b) => b.set(doc(db, "users/newseller"), prof())).commit());
      const again = sellerDb();   // the invitation is now used: cannot be consumed a second time
      await assertFails(accept(again, (b) => b.set(doc(again, "users/x"), prof())).commit());
    });
    it("cannot take more permissions or sections than invited, nor skip consuming the invite", async () => {
      await seed();
      const db = sellerDb();
      await assertFails(accept(db, (b) => b.set(doc(db, "users/newseller"), prof({ perms: { ...perms, manageP2p: true } }))).commit());
      await assertFails(accept(db, (b) => b.set(doc(db, "users/newseller"), prof({ sections: ["s_pubg", "s_ff"] }))).commit());
      await assertFails(accept(db, (b) => b.set(doc(db, "users/newseller"), prof({ role: "owner" }))).commit());
      await assertFails(setDoc(doc(db, "users/newseller"), prof()));
    });
    it("unverified e-mail, other e-mails and used invites are refused", async () => {
      await seed();
      const un = sellerDb(false);
      await assertFails(accept(un, (b) => b.set(doc(un, "users/newseller"), prof())).commit());
      const other = env.authenticatedContext("newseller", { email: "other@t.dev", email_verified: true }).firestore();
      await assertFails(getDoc(doc(other, "invites/seller@t.dev")));
      await seed({ acceptedBy: "someone" });
      const db = sellerDb();
      await assertFails(accept(db, (b) => b.set(doc(db, "users/newseller"), prof())).commit());
    });
    it("an existing customer with an invited e-mail is promoted once", async () => {
      await seed();
      await env.withSecurityRulesDisabled(async (c) => {
        await setDoc(doc(c.firestore(), "users/newseller"), { ...prof({ role: "customer", perms: P, sections: [] }), createdAt: new Date(), updatedAt: new Date() });
      });
      const db = sellerDb();
      await assertSucceeds(accept(db, (b) => b.update(doc(db, "users/newseller"),
        { role: "staff", perms, sections: ["s_pubg"], updatedAt: serverTimestamp() })).commit());
      await assertFails(updateDoc(doc(db, "users/newseller"), { role: "owner", updatedAt: serverTimestamp() }));
    });
    it("while accepting, the seller takes his unassigned sections only", async () => {
      await seed({ sections: ["s_pubg", "s_ff"] });
      const db = sellerDb();
      const ok = accept(db, (b) => b.set(doc(db, "users/newseller"), prof({ sections: ["s_pubg", "s_ff"] })));
      ok.update(doc(db, "sections/s_pubg"), { assignedStaffId: "newseller" });
      await assertSucceeds(ok.commit());
      await seed({ sections: ["s_pubg", "s_ff"] });
      const db2 = env.authenticatedContext("seller2", { email: "seller@t.dev", email_verified: true }).firestore();
      const steal = writeBatch(db2);
      steal.set(doc(db2, "users/seller2"), prof({ sections: ["s_pubg", "s_ff"] }));
      steal.update(doc(db2, "invites/seller@t.dev"), { acceptedBy: "seller2", acceptedAt: serverTimestamp() });
      steal.update(doc(db2, "sections/s_ff"), { assignedStaffId: "seller2" });   // already assigned to st1
      await assertFails(steal.commit());
      await assertFails(updateDoc(doc(as("st1"), "sections/s_pubg"), { assignedStaffId: "st1" }));
    });
  });

  describe("manual payments & commission", () => {
    beforeEach(async () => {
      await env.withSecurityRulesDisabled(async (c) => {
        await setDoc(doc(c.firestore(), "config/payments"), { commissionPct: 10, updatedAt: new Date() });
      });
    });
    const methods = (o = {}) => ({ methods: [{ type: "baridimob", title: "بريدي موب", account: "00799999000123456789", holder: "Ali", note: null }],
      updatedAt: serverTimestamp(), ...o });
    it("each seller manages only his own payment methods; the owner manages the store's", async () => {
      await assertSucceeds(setDoc(doc(as("st1"), "paymentMethods/st1"), methods()));
      await assertFails(setDoc(doc(as("st1"), "paymentMethods/st2"), methods()));
      await assertFails(setDoc(doc(as("st1"), "paymentMethods/store"), methods()));
      await assertFails(setDoc(doc(as("cust"), "paymentMethods/cust"), methods()));
      await assertSucceeds(setDoc(doc(as("owner"), "paymentMethods/store"), methods()));
      await assertFails(setDoc(doc(as("st1"), "paymentMethods/st1"), methods({ methods: Array(9).fill({ type: "x" }) })));
      await assertSucceeds(getDoc(doc(as("cust"), "paymentMethods/st1")));
      await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "paymentMethods/st1")));
    });
    it("the customer declares a manual payment on his open order only", async () => {
      const claim = { paymentClaim: { method: "بريدي موب", at: serverTimestamp() }, updatedAt: serverTimestamp() };
      await assertSucceeds(updateDoc(doc(as("cust"), "orders/o1"), claim));
      await assertFails(updateDoc(doc(as("cust2"), "orders/o1"), claim));
      await assertFails(updateDoc(doc(as("cust"), "orders/o1"), { ...claim, status: "paid" }));
      await assertFails(updateDoc(doc(as("cust"), "orders/o1"), { status: "paid", updatedAt: serverTimestamp(), paidAt: serverTimestamp() }));
    });
    it("confirming payment stamps the owner's current commission — no other value", async () => {
      const paid = (o = {}) => ({ status: "paid", updatedAt: serverTimestamp(), paidAt: serverTimestamp(), ...o });
      await assertFails(updateDoc(doc(as("st1"), "orders/o1"), paid()));
      await assertFails(updateDoc(doc(as("st1"), "orders/o1"), paid({ commissionPct: 0 })));
      await assertSucceeds(updateDoc(doc(as("st1"), "orders/o1"), paid({ commissionPct: 10 })));
      await assertFails(updateDoc(doc(as("st1"), "orders/o1"), { commissionPct: 1, updatedAt: serverTimestamp() }));
      await assertSucceeds(updateDoc(doc(as("st1"), "orders/o1"), { status: "delivered", updatedAt: serverTimestamp(), deliveredAt: serverTimestamp() }));
    });
    it("only the owner sets the commission (0–50%)", async () => {
      const cfg = (o = {}) => ({ commissionPct: 12.5, updatedAt: serverTimestamp(), ...o });
      await assertSucceeds(setDoc(doc(as("owner"), "config/payments"), cfg()));
      await assertFails(setDoc(doc(as("owner"), "config/payments"), cfg({ commissionPct: 80 })));
      await assertFails(setDoc(doc(as("owner"), "config/payments"), cfg({ commissionPct: -1 })));
      await assertFails(setDoc(doc(as("st1"), "config/payments"), cfg({ commissionPct: 0 })));
    });
    it("commission settlements: owner records, seller sees only his own", async () => {
      const st = (o = {}) => ({ sellerId: "st1", amountDzd: 5000, amountUsd: 0, note: "BaridiMob", createdBy: "owner", createdAt: serverTimestamp(), ...o });
      await assertSucceeds(setDoc(doc(as("owner"), "settlements/a"), st()));
      await assertFails(setDoc(doc(as("st1"), "settlements/b"), st({ createdBy: "st1" })));
      await assertFails(setDoc(doc(as("owner"), "settlements/c"), st({ sellerId: "cust" })));
      await assertFails(setDoc(doc(as("owner"), "settlements/d"), st({ amountDzd: 0 })));
      await assertSucceeds(getDocs(query(collection(as("st1"), "settlements"), where("sellerId", "==", "st1"))));
      await assertFails(getDocs(query(collection(as("st2"), "settlements"), where("sellerId", "==", "st1"))));
      await assertFails(updateDoc(doc(as("owner"), "settlements/a"), { amountDzd: 1 }));
    });
  });

  describe("catalog & permissions", () => {
    it("anyone reads the catalog", async () => { await assertSucceeds(getDoc(doc(env.unauthenticatedContext().firestore(), "categories/gaming"))); });
    it("products: permission + section + valid fields", async () => {
      await assertFails(addDoc(collection(as("cust"), "products"), product()));
      await assertSucceeds(addDoc(collection(as("st1"), "products"), product()));
      await assertFails(addDoc(collection(as("st1"), "products"), product({ sectionId: "s_pubg" })));
      await assertFails(addDoc(collection(as("st2"), "products"), product()));
      await assertFails(addDoc(collection(as("owner"), "products"), product({ priceUsd: -1 })));
      await assertFails(addDoc(collection(as("owner"), "products"), product({ hacked: true })));
      await assertFails(updateDoc(doc(as("cust"), "products/visible"), { priceUsd: 0 }));
    });
    it("hidden products invisible to customers", async () => {
      await assertFails(getDoc(doc(as("cust"), "products/hidden")));
      await assertSucceeds(getDoc(doc(as("st1"), "products/hidden")));
    });
    it("P2P rates need the P2P permission", async () => {
      const rate = { code: "USDT", name: "Tether", network: "TRC20", buyPriceDzd: 255, sellPriceDzd: 245, minAmount: 10, maxAmount: 5000,
        active: true, sortOrder: 0, updatedAt: serverTimestamp() };
      await assertFails(setDoc(doc(as("st2"), "p2pAssets/usdt"), rate));
      await assertSucceeds(setDoc(doc(as("stp2p"), "p2pAssets/usdt"), rate));
    });
    it("only the owner edits sections and seeds categories", async () => {
      await assertFails(updateDoc(doc(as("st1"), "sections/s_ff"), { assignedStaffId: "st2" }));
      await assertSucceeds(updateDoc(doc(as("owner"), "sections/s_ff"), { assignedStaffId: "st2" }));
      await assertSucceeds(setDoc(doc(as("owner"), "categories/p2p"), { slug: "p2p", name: "P2P", description: null, colorKey: "green", iconKey: "p2p", sortOrder: 4, active: true }));
      await assertFails(setDoc(doc(as("owner"), "categories/hack"), { slug: "hack", name: "x", sortOrder: 9, active: true }));
      await assertFails(deleteDoc(doc(as("owner"), "categories/gaming")));
    });
    it("catalog images: team only, images only", async () => {
      const img = { data: "aGVsbG8=", mime: "image/jpeg", createdBy: "st1", createdAt: serverTimestamp() };
      await assertSucceeds(setDoc(doc(as("st1"), "media/m1"), img));
      await assertFails(setDoc(doc(as("cust"), "media/m2"), { ...img, createdBy: "cust" }));
      await assertFails(setDoc(doc(as("st1"), "media/m3"), { ...img, mime: "text/html" }));
    });
    it("everything else is closed", async () => {
      await assertFails(getDoc(doc(as("owner"), "secrets/x")));
      await assertFails(setDoc(doc(as("owner"), "anything/x"), { a: 1 }));
    });
  });
});
