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
      await setDoc(doc(db, "products/hidden"), { ...product({ available: false }), createdAt: new Date(), updatedAt: new Date() });
      await setDoc(doc(db, "orders/o1"), { ...order(), createdAt: new Date(), updatedAt: new Date() });
      await setDoc(doc(db, "conversations/c1"), { customerId: "cust", staffId: "st1", orderId: "o1", subject: "x", lastMessage: null, lastMessageAt: new Date(), lastSenderId: "cust" });
    });
  });

  describe("sign-up & roles", () => {
    const profile = (o = {}) => ({ email: "new@t.dev", fullName: "New", avatarUrl: null, role: "customer", active: true,
      perms: P, sections: [], createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...o });
    it("new user creates his customer profile", async () => {
      await assertSucceeds(setDoc(doc(env.authenticatedContext("new", { email: "new@t.dev" }).firestore(), "users/new"), profile()));
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
      await assertSucceeds(updateDoc(doc(as("st1"), "orders/o1"), { status: "paid", updatedAt: serverTimestamp(), paidAt: serverTimestamp() }));
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
      await assertFails(getDoc(doc(as("owner"), "pushTokens/t2")));
      await assertFails(getDocs(collection(as("owner"), "pushTokens")));
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

  describe("payments & wallets", () => {
    beforeEach(async () => {
      await env.withSecurityRulesDisabled(async (c) => {
        const db = c.firestore();
        await setDoc(doc(db, "wallets/st1"), { balanceUsd: 10 });
        await setDoc(doc(db, "wallets/st1/entries/sale-o1"), { type: "sale", amountUsd: 10 });
        await setDoc(doc(db, "wallets/platform"), { balanceUsd: 1 });
        await setDoc(doc(db, "withdrawals/w1"), { uid: "st1", amountUsd: 5, status: "pending" });
      });
    });
    it("a seller sees only his own wallet, history and withdrawals", async () => {
      await assertSucceeds(getDoc(doc(as("st1"), "wallets/st1")));
      await assertSucceeds(getDocs(collection(as("st1"), "wallets/st1/entries")));
      await assertSucceeds(getDocs(query(collection(as("st1"), "withdrawals"), where("uid", "==", "st1"))));
      await assertFails(getDoc(doc(as("st2"), "wallets/st1")));
      await assertFails(getDoc(doc(as("st1"), "wallets/platform")));
      await assertFails(getDocs(collection(as("st1"), "withdrawals")));
      await assertFails(getDoc(doc(as("cust"), "withdrawals/w1")));
    });
    it("the owner sees all money documents", async () => {
      await assertSucceeds(getDoc(doc(as("owner"), "wallets/st1")));
      await assertSucceeds(getDoc(doc(as("owner"), "wallets/platform")));
      await assertSucceeds(getDocs(collection(as("owner"), "withdrawals")));
    });
    it("nobody writes balances or withdrawals from the app (not even the owner)", async () => {
      await assertFails(setDoc(doc(as("st1"), "wallets/st1"), { balanceUsd: 9999 }));
      await assertFails(updateDoc(doc(as("owner"), "wallets/st1"), { balanceUsd: 9999 }));
      await assertFails(setDoc(doc(as("st1"), "wallets/st1/entries/x"), { amountUsd: 9999 }));
      await assertFails(setDoc(doc(as("st1"), "withdrawals/w2"), { uid: "st1", amountUsd: 5, status: "pending" }));
      await assertFails(updateDoc(doc(as("owner"), "withdrawals/w1"), { status: "paid" }));
    });
    it("order payment info cannot be forged by anyone", async () => {
      await assertFails(updateDoc(doc(as("cust"), "orders/o1"), { payment: { status: "finished" } }));
      await assertFails(updateDoc(doc(as("st1"), "orders/o1"), { payment: { status: "finished" } }));
      await assertFails(updateDoc(doc(as("owner"), "orders/o1"), { payment: { status: "finished" } }));
    });
    it("only the owner sets the commission (0–50%)", async () => {
      const cfg = (o = {}) => ({ commissionPct: 12.5, enabled: true, updatedAt: serverTimestamp(), ...o });
      await assertSucceeds(setDoc(doc(as("owner"), "config/payments"), cfg()));
      await assertFails(setDoc(doc(as("owner"), "config/payments"), cfg({ commissionPct: 80 })));
      await assertFails(setDoc(doc(as("owner"), "config/payments"), cfg({ commissionPct: -1 })));
      await assertFails(setDoc(doc(as("owner"), "config/payments"), { ...cfg(), sellerId: "x" }));
      await assertFails(setDoc(doc(as("st1"), "config/payments"), cfg()));
      await assertSucceeds(getDoc(doc(as("cust"), "config/payments")));
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
