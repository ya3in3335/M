// Cloud Functions integration tests against the emulators (auth + firestore + functions).
import assert from "assert";
import { initializeApp as initClient, deleteApp } from "firebase/app";
import { getAuth, connectAuthEmulator, createUserWithEmailAndPassword, signInWithEmailAndPassword, signOut } from "firebase/auth";
import { getFunctions, connectFunctionsEmulator, httpsCallable } from "firebase/functions";
import admin from "firebase-admin";

const PROJECT = "demo-medk";
if (!admin.apps.length) admin.initializeApp({ projectId: PROJECT });
const adb = admin.firestore();
const aauth = admin.auth();

const app = initClient({ apiKey: "fake", projectId: PROJECT, authDomain: "x" }, "fn-tests");
const auth = getAuth(app); connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
const fns = getFunctions(app, "europe-west1"); connectFunctionsEmulator(fns, "127.0.0.1", 5001);
const call = (name, data = {}) => httpsCallable(fns, name)(data).then((r) => r.data);

async function login(email, verify = false) {
  try { await createUserWithEmailAndPassword(auth, email, "password123"); } catch { await signInWithEmailAndPassword(auth, email, "password123"); }
  if (verify) { await aauth.updateUser(auth.currentUser.uid, { emailVerified: true }); await signInWithEmailAndPassword(auth, email, "password123"); }
  await call("bootstrap", { full_name: email.split("@")[0] });
  await auth.currentUser.getIdToken(true);
  return auth.currentUser.uid;
}
const code = async (p) => { try { await p; return "ok"; } catch (e) { return e.code; } };

let owner, seller, customer, ffSection, pubgSection, ffProduct, pubgProduct;

describe("cloud functions", function () {
  this.timeout(60000);
  before(async () => {
    // start from an empty project
    await fetch(`http://127.0.0.1:8080/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: "DELETE" });
    await fetch(`http://127.0.0.1:9099/emulator/v1/projects/${PROJECT}/accounts`, { method: "DELETE" });
  });
  after(async () => { await signOut(auth); await deleteApp(app); });

  it("unverified owner e-mail is NOT promoted", async () => {
    await login("owner@test.dev", false);
    const u = (await adb.doc(`users/${auth.currentUser.uid}`).get()).data();
    assert.equal(u.role, "customer");
    await adb.doc(`users/${auth.currentUser.uid}`).delete();
  });

  it("verified owner e-mail becomes owner and seeds the catalog", async () => {
    owner = await login("owner@test.dev", true);
    const u = (await adb.doc(`users/${owner}`).get()).data();
    assert.equal(u.role, "owner");
    assert.equal((await aauth.getUser(owner)).customClaims.role, "owner");
    assert.equal((await adb.collection("categories").get()).size, 5);
    const secs = await adb.collection("sections").get();
    ffSection = secs.docs.find((d) => d.get("name") === "Free Fire").id;
    pubgSection = secs.docs.find((d) => d.get("name") === "PUBG Mobile").id;
  });

  it("owner creates a seller with permissions", async () => {
    const r = await call("adminCreateStaff", { email: "seller@test.dev", password: "password123", full_name: "Seller",
      perms: { manage_products: true, update_orders: true } });
    seller = r.profile.id;
    await call("adminSetPermissions", { uid: seller, perms: { manage_products: true, update_orders: true }, sections: [ffSection] });
    await adb.doc(`sections/${ffSection}`).update({ assignedStaffId: seller });
    ffProduct = (await adb.collection("products").add({ categoryId: "gaming", sectionId: ffSection, name: "FF 100", priceUsd: 1, priceDzd: 270,
      stock: 5, available: true, images: [], createdAt: new Date(), updatedAt: new Date() })).id;
    pubgProduct = (await adb.collection("products").add({ categoryId: "gaming", sectionId: pubgSection, name: "PUBG 60", priceUsd: 2, priceDzd: 540,
      stock: null, available: true, images: [], createdAt: new Date(), updatedAt: new Date() })).id;
  });

  it("customer cannot use owner functions", async () => {
    customer = await login("buyer@test.dev");
    assert.equal(await code(call("adminSetRole", { uid: customer, role: "staff" })), "functions/permission-denied");
    assert.equal(await code(call("adminSetPermissions", { uid: seller, perms: {}, sections: [] })), "functions/permission-denied");
    assert.equal(await code(call("dashboardStats")), "functions/permission-denied");
  });

  it("checkout ignores client prices, splits by seller and decrements stock", async () => {
    const res = await call("checkout", { items: [
      { product_id: ffProduct, quantity: 2, price_usd: 0 },   // tampered price is ignored
      { product_id: pubgProduct, quantity: 1 } ] });
    assert.equal(res.length, 2);
    const orders = await Promise.all(res.map((o) => adb.doc(`orders/${o.order_id}`).get()));
    const ff = orders.find((o) => o.get("staffId") === seller);
    assert.equal(ff.get("totalUsd"), 2);
    assert.equal(orders.find((o) => o.get("staffId") === null).get("totalUsd"), 2);
    assert.equal((await adb.doc(`products/${ffProduct}`).get()).get("stock"), 3);
    const ownerNotes = await adb.collection(`users/${owner}/notifications`).get();
    assert.ok(ownerNotes.size >= 2, "owner notified of each new order");
  });

  it("stock cannot go negative", async () => {
    assert.equal(await code(call("checkout", { items: [{ product_id: ffProduct, quantity: 50 }] })), "functions/failed-precondition");
  });

  it("customer cannot change order status", async () => {
    const o = (await adb.collection("orders").where("customerId", "==", customer).limit(1).get()).docs[0];
    assert.equal(await code(call("setOrderStatus", { order_id: o.id, status: "paid" })), "functions/permission-denied");
  });

  it("assigned seller updates status; customer is notified", async () => {
    const o = (await adb.collection("orders").where("staffId", "==", seller).limit(1).get()).docs[0];
    await signOut(auth); await signInWithEmailAndPassword(auth, "seller@test.dev", "password123");
    await call("bootstrap"); await auth.currentUser.getIdToken(true);
    await call("setOrderStatus", { order_id: o.id, status: "paid" });
    const after = (await o.ref.get()).data();
    assert.equal(after.status, "paid"); assert.ok(after.paidAt);
    const notes = await adb.collection(`users/${customer}/notifications`).get();
    assert.ok(notes.docs.some((n) => n.get("body").includes("تم الدفع")));
  });

  it("seller without statistics permission cannot read stats", async () => {
    assert.equal(await code(call("dashboardStats")), "functions/permission-denied");
  });

  it("seller cannot touch another seller's order", async () => {
    const o = (await adb.collection("orders").where("staffId", "==", null).limit(1).get()).docs[0];
    assert.equal(await code(call("setOrderStatus", { order_id: o.id, status: "delivered" })), "functions/permission-denied");
  });

  it("owner suspends the customer → account disabled", async () => {
    await signOut(auth); await signInWithEmailAndPassword(auth, "owner@test.dev", "password123");
    await call("adminSetActive", { uid: customer, active: false });
    assert.equal((await aauth.getUser(customer)).disabled, true);
    assert.equal(await code(call("adminSetActive", { uid: owner, active: false })), "functions/failed-precondition");
    const stats = await call("dashboardStats");
    assert.equal(stats.total_orders, 2);
  });

  it("checkout is rate limited", async () => {
    await signOut(auth);
    await login("spammer@test.dev");
    let last = "ok";
    for (let i = 0; i < 12; i++) last = await code(call("checkout", { items: [{ product_id: pubgProduct, quantity: 1 }] }));
    assert.equal(last, "functions/resource-exhausted");
  });
});
