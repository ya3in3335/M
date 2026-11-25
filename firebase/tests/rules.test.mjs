// Security-rules tests: every test is an attack or a legitimate action.
import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc, addDoc, collection, getDocs, query, where, serverTimestamp, deleteDoc } from "firebase/firestore";
import { ref, uploadBytes, getBytes } from "firebase/storage";
import { readFileSync } from "fs";

let env;
const P = { manageProducts: false, updateOrders: true, manageP2p: false, viewStats: false };
const users = {
  owner: { role: "owner", active: true, perms: { ...P, manageProducts: true, manageP2p: true, viewStats: true }, sections: [] },
  cust: { role: "customer", active: true, perms: P, sections: [] },
  cust2: { role: "customer", active: true, perms: P, sections: [] },
  banned: { role: "customer", active: false, perms: P, sections: [] },
  st1: { role: "staff", active: true, perms: { ...P, manageProducts: true }, sections: ["s_ff"] },
  st2: { role: "staff", active: true, perms: P, sections: [] },
  stp2p: { role: "staff", active: true, perms: { ...P, manageP2p: true }, sections: [] },
};
const as = (uid) => env.authenticatedContext(uid, { role: users[uid]?.role ?? "customer" }).firestore();
const asStorage = (uid) => env.authenticatedContext(uid, { role: users[uid]?.role ?? "customer" }).storage();
const product = (o = {}) => ({ categoryId: "gaming", sectionId: "s_ff", name: "100 Diamonds", description: null, priceUsd: 1, priceDzd: 270,
  stock: null, available: true, images: [], createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...o });

describe("security rules", () => {
before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-medk",
    firestore: { rules: readFileSync("../firestore.rules", "utf8"), host: "127.0.0.1", port: 8080 },
    storage: { rules: readFileSync("../storage.rules", "utf8"), host: "127.0.0.1", port: 9199 },
  });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const [id, u] of Object.entries(users)) await setDoc(doc(db, `users/${id}`), { email: `${id}@t.dev`, fullName: id, avatarUrl: null, ...u });
    await setDoc(doc(db, "categories/gaming"), { slug: "gaming", name: "شحن الألعاب", sortOrder: 1, active: true });
    await setDoc(doc(db, "sections/s_ff"), { categoryId: "gaming", name: "Free Fire", assignedStaffId: "st1", sortOrder: 1, active: true });
    await setDoc(doc(db, "sections/s_pubg"), { categoryId: "gaming", name: "PUBG", assignedStaffId: null, sortOrder: 2, active: true });
    await setDoc(doc(db, "products/visible"), { ...product(), createdAt: new Date(), updatedAt: new Date() });
    await setDoc(doc(db, "products/hidden"), { ...product({ available: false }), createdAt: new Date(), updatedAt: new Date() });
    await setDoc(doc(db, "orders/o1"), { customerId: "cust", staffId: "st1", status: "open", totalUsd: 5, reference: "MK-1" });
    await setDoc(doc(db, "conversations/c1"), { customerId: "cust", staffId: "st1", orderId: "o1", subject: "x" });
    await setDoc(doc(db, "users/cust/notifications/n1"), { title: "t", body: "b", read: false });
  });
});

describe("profiles & roles", () => {
  it("customer can rename himself", async () => { await assertSucceeds(updateDoc(doc(as("cust"), "users/cust"), { fullName: "New" })); });
  it("customer cannot make himself owner", async () => { await assertFails(updateDoc(doc(as("cust"), "users/cust"), { role: "owner" })); });
  it("customer cannot grant himself permissions", async () => { await assertFails(updateDoc(doc(as("st2"), "users/st2"), { "perms.manageP2p": true })); });
  it("customer cannot reactivate a suspended account", async () => { await assertFails(updateDoc(doc(as("banned"), "users/banned"), { active: true })); });
  it("customer cannot read another user's private profile", async () => { await assertFails(getDoc(doc(as("cust"), "users/cust2"))); });
  it("customer cannot list users", async () => { await assertFails(getDocs(collection(as("cust"), "users"))); });
  it("owner can list users", async () => { await assertSucceeds(getDocs(collection(as("owner"), "users"))); });
  it("a forged 'owner' claim without owner profile grants nothing", async () => {
    const fake = env.authenticatedContext("cust", { role: "owner" }).firestore();
    await assertFails(getDocs(collection(fake, "users")));
  });
  it("nobody can create a profile from the client", async () => { await assertFails(setDoc(doc(as("cust"), "users/x"), { role: "owner", active: true })); });
});

describe("orders & conversations (server-only writes)", () => {
  it("customer reads his own order", async () => { await assertSucceeds(getDoc(doc(as("cust"), "orders/o1"))); });
  it("other customer cannot read it", async () => { await assertFails(getDoc(doc(as("cust2"), "orders/o1"))); });
  it("assigned seller reads it", async () => { await assertSucceeds(getDoc(doc(as("st1"), "orders/o1"))); });
  it("other seller cannot read it", async () => { await assertFails(getDoc(doc(as("st2"), "orders/o1"))); });
  it("suspended customer loses access", async () => {
    await env.withSecurityRulesDisabled(async (c) => setDoc(doc(c.firestore(), "orders/o2"), { customerId: "banned", staffId: null, status: "open" }));
    await assertFails(getDoc(doc(as("banned"), "orders/o2")));
  });
  it("customer cannot create an order (price tampering)", async () => {
    await assertFails(setDoc(doc(as("cust"), "orders/evil"), { customerId: "cust", status: "paid", totalUsd: 0 }));
  });
  it("customer cannot mark his order as paid", async () => { await assertFails(updateDoc(doc(as("cust"), "orders/o1"), { status: "paid" })); });
  it("seller cannot update order directly (must use function)", async () => { await assertFails(updateDoc(doc(as("st1"), "orders/o1"), { status: "delivered" })); });
  it("customer list query restricted to his orders works", async () => {
    await assertSucceeds(getDocs(query(collection(as("cust"), "orders"), where("customerId", "==", "cust"))));
  });
  it("customer cannot list all orders", async () => { await assertFails(getDocs(collection(as("cust"), "orders"))); });
  it("customer cannot create conversations directly", async () => {
    await assertFails(addDoc(collection(as("cust"), "conversations"), { customerId: "cust", staffId: "st2" }));
  });
});

describe("messages", () => {
  const msg = (o = {}) => ({ senderId: "cust", body: "hello", attachment: null, createdAt: serverTimestamp(), ...o });
  it("participant can send a message", async () => { await assertSucceeds(addDoc(collection(as("cust"), "conversations/c1/messages"), msg())); });
  it("assigned seller can reply", async () => { await assertSucceeds(addDoc(collection(as("st1"), "conversations/c1/messages"), msg({ senderId: "st1" }))); });
  it("cannot impersonate another sender", async () => { await assertFails(addDoc(collection(as("cust"), "conversations/c1/messages"), msg({ senderId: "st1" }))); });
  it("outsider cannot send", async () => { await assertFails(addDoc(collection(as("cust2"), "conversations/c1/messages"), msg({ senderId: "cust2" }))); });
  it("outsider cannot read", async () => { await assertFails(getDocs(collection(as("cust2"), "conversations/c1/messages"))); });
  it("cannot backdate a message", async () => {
    await assertFails(addDoc(collection(as("cust"), "conversations/c1/messages"), msg({ createdAt: new Date(2000, 1, 1) })));
  });
  it("attachment must live in this conversation's folder", async () => {
    await assertFails(addDoc(collection(as("cust"), "conversations/c1/messages"),
      msg({ body: null, attachment: { path: "chat/other/file.pdf", type: "pdf", name: "x" } })));
    await assertSucceeds(addDoc(collection(as("cust"), "conversations/c1/messages"),
      msg({ body: null, attachment: { path: "chat/c1/abc.pdf", type: "pdf", name: "invoice.pdf" } })));
  });
  it("messages cannot be edited", async () => {
    const r = await addDoc(collection(as("cust"), "conversations/c1/messages"), msg());
    await assertFails(updateDoc(doc(as("cust"), r.path), { body: "edited" }));
  });
});

describe("catalog & permissions", () => {
  it("anyone reads categories", async () => { await assertSucceeds(getDoc(doc(env.unauthenticatedContext().firestore(), "categories/gaming"))); });
  it("customer cannot create products", async () => { await assertFails(addDoc(collection(as("cust"), "products"), product())); });
  it("seller with product permission + section can create", async () => { await assertSucceeds(addDoc(collection(as("st1"), "products"), product())); });
  it("seller cannot create in a section he does not own", async () => { await assertFails(addDoc(collection(as("st1"), "products"), product({ sectionId: "s_pubg" }))); });
  it("seller without product permission cannot create", async () => { await assertFails(addDoc(collection(as("st2"), "products"), product())); });
  it("negative price rejected", async () => { await assertFails(addDoc(collection(as("owner"), "products"), product({ priceUsd: -1 }))); });
  it("unknown fields rejected", async () => { await assertFails(addDoc(collection(as("owner"), "products"), product({ hacked: true }))); });
  it("customer cannot read hidden product", async () => { await assertFails(getDoc(doc(as("cust"), "products/hidden"))); });
  it("team can read hidden product", async () => { await assertSucceeds(getDoc(doc(as("st1"), "products/hidden"))); });
  it("customer cannot change a price", async () => { await assertFails(updateDoc(doc(as("cust"), "products/visible"), { priceUsd: 0 })); });
  it("seller without P2P permission cannot change rates", async () => {
    await assertFails(setDoc(doc(as("st2"), "p2pAssets/usdt"), { code: "USDT", name: "Tether", network: null, buyPriceDzd: 1, sellPriceDzd: 1,
      minAmount: null, maxAmount: null, active: true, sortOrder: 0, updatedAt: serverTimestamp() }));
  });
  it("seller with P2P permission can change rates", async () => {
    await assertSucceeds(setDoc(doc(as("stp2p"), "p2pAssets/usdt"), { code: "USDT", name: "Tether", network: "TRC20", buyPriceDzd: 255, sellPriceDzd: 245,
      minAmount: 10, maxAmount: 5000, active: true, sortOrder: 0, updatedAt: serverTimestamp() }));
  });
  it("only owner edits sections", async () => {
    await assertFails(updateDoc(doc(as("st1"), "sections/s_ff"), { assignedStaffId: "st2" }));
    await assertSucceeds(updateDoc(doc(as("owner"), "sections/s_ff"), { assignedStaffId: "st2" }));
  });
  it("categories cannot be created or deleted", async () => {
    await assertFails(setDoc(doc(as("owner"), "categories/new"), { name: "x", sortOrder: 9, active: true }));
    await assertFails(deleteDoc(doc(as("owner"), "categories/gaming")));
  });
});

describe("notifications & private data", () => {
  it("user can mark his notification read", async () => { await assertSucceeds(updateDoc(doc(as("cust"), "users/cust/notifications/n1"), { read: true })); });
  it("user cannot rewrite its content", async () => { await assertFails(updateDoc(doc(as("cust"), "users/cust/notifications/n1"), { title: "fake" })); });
  it("user cannot forge notifications", async () => { await assertFails(addDoc(collection(as("cust"), "users/cust2/notifications"), { title: "x" })); });
  it("rate-limit bookkeeping is invisible", async () => { await assertFails(getDoc(doc(as("owner"), "_private/ratelimits/checkout/cust"))); });
});

describe("storage", () => {
  const pdf = new Uint8Array([37, 80, 68, 70]);
  it("participant uploads a chat PDF", async () => {
    await assertSucceeds(uploadBytes(ref(asStorage("cust"), "chat/c1/a.pdf"), pdf, { contentType: "application/pdf" }));
  });
  it("outsider cannot upload or read chat files", async () => {
    await assertFails(uploadBytes(ref(asStorage("cust2"), "chat/c1/b.pdf"), pdf, { contentType: "application/pdf" }));
    await assertFails(getBytes(ref(asStorage("cust2"), "chat/c1/a.pdf")));
  });
  it("executables are rejected", async () => {
    await assertFails(uploadBytes(ref(asStorage("cust"), "chat/c1/x.apk"), pdf, { contentType: "application/vnd.android.package-archive" }));
  });
  it("customer cannot upload catalog images", async () => {
    await assertFails(uploadBytes(ref(asStorage("cust"), "catalog/products/p.png"), pdf, { contentType: "image/png" }));
  });
  it("seller with product permission uploads product images", async () => {
    await assertSucceeds(uploadBytes(ref(asStorage("st1"), "catalog/products/p.png"), pdf, { contentType: "image/png" }));
  });
  it("only owner uploads banners", async () => {
    await assertFails(uploadBytes(ref(asStorage("st1"), "catalog/banners/b.png"), pdf, { contentType: "image/png" }));
    await assertSucceeds(uploadBytes(ref(asStorage("owner"), "catalog/banners/b.png"), pdf, { contentType: "image/png" }));
  });
});
});
