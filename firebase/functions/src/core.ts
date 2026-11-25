import { initializeApp, getApps } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { defineBoolean, defineString } from "firebase-functions/params";

if (getApps().length === 0) initializeApp();
export const db = getFirestore();
export const auth = getAuth();
export { FieldValue, Timestamp };

/** Comma-separated e-mails promoted to owner on first sign-in (must be verified). */
export const OWNER_EMAILS = defineString("OWNER_EMAILS", { default: "" });
/** Reject calls that do not carry a valid App Check token (Play Integrity). */
export const ENFORCE_APP_CHECK = defineBoolean("ENFORCE_APP_CHECK", { default: true });
export const REGION = "europe-west1";

export type Role = "customer" | "staff" | "owner";
export interface Perms { manageProducts: boolean; updateOrders: boolean; manageP2p: boolean; viewStats: boolean }
export interface UserDoc {
  email: string | null; fullName: string | null; avatarUrl: string | null;
  role: Role; active: boolean; perms: Perms; sections: string[];
  createdAt: Timestamp; updatedAt?: Timestamp;
}
export const NO_PERMS: Perms = { manageProducts: false, updateOrders: true, manageP2p: false, viewStats: false };
export const OWNER_PERMS: Perms = { manageProducts: true, updateOrders: true, manageP2p: true, viewStats: true };

export const fail = (code: ConstructorParameters<typeof HttpsError>[0], msg: string): never => { throw new HttpsError(code, msg); };

export interface Caller { uid: string; user: UserDoc }

/** Authenticated + active account required. */
export async function caller(req: CallableRequest<unknown>): Promise<Caller> {
  if (!req.auth) fail("unauthenticated", "not authenticated");
  const uid = req.auth!.uid;
  const snap = await db.doc(`users/${uid}`).get();
  if (!snap.exists) fail("failed-precondition", "profile missing — call bootstrap first");
  const user = snap.data() as UserDoc;
  if (!user.active) fail("permission-denied", "account_disabled");
  return { uid, user };
}

export function requireOwner(c: Caller) { if (c.user.role !== "owner") fail("permission-denied", "owner only"); }
export function hasPerm(c: Caller, p: keyof Perms) { return c.user.role === "owner" || (c.user.role === "staff" && !!c.user.perms?.[p]); }

/** Fixed-window rate limiter stored in _private/ratelimits. */
export async function rateLimit(uid: string, key: string, max: number, windowSec: number) {
  const ref = db.doc(`_private/ratelimits/${key}/${uid}`);
  await db.runTransaction(async (tx) => {
    const s = await tx.get(ref);
    const now = Date.now();
    const d = s.exists ? s.data() as { start: number; count: number } : { start: now, count: 0 };
    if (now - d.start > windowSec * 1000) { d.start = now; d.count = 0; }
    if (d.count >= max) fail("resource-exhausted", "too many requests");
    tx.set(ref, { start: d.start, count: d.count + 1 });
  });
}

// ---------------------------------------------------------------- validation
export function str(v: unknown, name: string, max: number, required = true): string | null {
  if (v === undefined || v === null || v === "") { if (required) fail("invalid-argument", `${name} required`); return null; }
  if (typeof v !== "string" || v.length > max) fail("invalid-argument", `${name} invalid`);
  return (v as string).trim();
}
export function bool(v: unknown, def = false): boolean { return typeof v === "boolean" ? v : def; }
export function id(v: unknown, name: string, required = true): string | null {
  const s = str(v, name, 128, required);
  if (s !== null && !/^[A-Za-z0-9_-]{1,128}$/.test(s)) fail("invalid-argument", `${name} invalid`);
  return s;
}

export async function notify(userId: string | null | undefined, title: string, body: string,
                             extra: { conversationId?: string; orderId?: string } = {}) {
  if (!userId) return;
  await db.collection(`users/${userId}/notifications`).add({
    title, body, conversationId: extra.conversationId ?? null, orderId: extra.orderId ?? null,
    read: false, createdAt: FieldValue.serverTimestamp(),
  });
}

export async function ownerIds(): Promise<string[]> {
  const q = await db.collection("users").where("role", "==", "owner").where("active", "==", true).get();
  return q.docs.map((d) => d.id);
}

export function callOpts() {
  return { region: REGION, enforceAppCheck: ENFORCE_APP_CHECK.value(), consumeAppCheckToken: false };
}
