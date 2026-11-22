// Server-side helpers. Runs ONLY inside Supabase Edge Functions.
// Secrets required (supabase secrets set ...):
//   FIREBASE_SERVICE_ACCOUNT  – JSON of a Firebase service account
//   OWNER_EMAILS              – comma separated e-mails promoted to "owner" on first login
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are injected automatically by Supabase.
import { cert, getApps, initializeApp } from "npm:firebase-admin@12.7.0/app";
import { getAuth } from "npm:firebase-admin@12.7.0/auth";
import { createClient } from "npm:@supabase/supabase-js@2.46.2";

export const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

export function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
}

export function firebaseAuth() {
  if (getApps().length === 0) {
    const sa = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
    if (!sa) throw new Error("FIREBASE_SERVICE_ACCOUNT secret is not configured");
    initializeApp({ credential: cert(JSON.parse(sa)) });
  }
  return getAuth();
}

export function adminDb() {
  return createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

/** Verifies the Firebase ID token sent as "Authorization: Bearer <token>". */
export async function verifyCaller(req: Request) {
  const header = req.headers.get("Authorization") ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!token) throw new HttpError(401, "missing token");
  try {
    return await firebaseAuth().verifyIdToken(token, true);
  } catch (_) {
    throw new HttpError(401, "invalid token");
  }
}

export class HttpError extends Error {
  constructor(public status: number, message: string) { super(message); }
}
