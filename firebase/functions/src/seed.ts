import { db, FieldValue } from "./core";

const CATEGORIES = [
  { id: "gaming", name: "شحن الألعاب", description: "جواهر، شدات، عملات داخل الألعاب", colorKey: "orange", sortOrder: 1 },
  { id: "subscriptions", name: "اشتراكات التطبيقات", description: "Netflix - YouTube - Spotify وأكثر", colorKey: "blue", sortOrder: 2 },
  { id: "accounts", name: "شراء الحسابات", description: "حسابات الألعاب والتطبيقات", colorKey: "purple", sortOrder: 3 },
  { id: "p2p", name: "شراء وبيع العملات الرقمية P2P", description: "USDT والعملات الرقمية المدعومة", colorKey: "green", sortOrder: 4 },
  { id: "other", name: "خدمات أخرى", description: "بطاقات الهدايا، تراخيص البرامج، VPN والخدمات الرقمية المتنوعة", colorKey: "cyan", sortOrder: 5 },
];
const SECTIONS: Record<string, string[]> = {
  gaming: ["Free Fire", "PUBG Mobile", "Call of Duty Mobile", "Roblox", "ألعاب أخرى"],
  subscriptions: ["Netflix", "YouTube Premium", "Spotify", "تطبيقات أخرى"],
  accounts: ["حسابات الألعاب", "حسابات التطبيقات"],
  p2p: ["العملات الرقمية"],
  other: ["بطاقات الهدايا", "Google Play", "Apple Gift Card", "تراخيص البرامج", "اشتراكات VPN", "منتجات رقمية أخرى"],
};

/** Structural catalog only (no products). Runs once, when the first owner signs in. */
export async function seedCatalogIfEmpty() {
  const existing = await db.collection("categories").limit(1).get();
  if (!existing.empty) return;
  const batch = db.batch();
  for (const c of CATEGORIES) {
    batch.set(db.doc(`categories/${c.id}`), { slug: c.id, name: c.name, description: c.description, colorKey: c.colorKey,
      iconKey: c.id, sortOrder: c.sortOrder, active: true });
    SECTIONS[c.id].forEach((name, i) => batch.set(db.collection("sections").doc(), {
      categoryId: c.id, name, description: null, imageUrl: null, assignedStaffId: null, sortOrder: i + 1, active: true,
      createdAt: FieldValue.serverTimestamp() }));
  }
  await batch.commit();
}
