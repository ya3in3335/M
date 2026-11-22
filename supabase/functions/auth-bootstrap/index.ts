// Called by the app right after every Firebase sign-in.
// 1. Verifies the Firebase ID token (server side).
// 2. Ensures the custom claim {role: "authenticated"} required by Supabase third-party auth.
// 3. Creates the profile row if missing (never changes an existing role).
import { adminDb, cors, firebaseAuth, HttpError, json, verifyCaller } from "../_shared/firebase.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  try {
    const decoded = await verifyCaller(req);
    const auth = firebaseAuth();
    let claimsUpdated = false;
    if (decoded.role !== "authenticated") {
      const user = await auth.getUser(decoded.uid);
      await auth.setCustomUserClaims(decoded.uid, { ...(user.customClaims ?? {}), role: "authenticated" });
      claimsUpdated = true;
    }

    const body = await req.json().catch(() => ({}));
    const db = adminDb();
    const { data: existing, error: selErr } = await db.from("profiles").select("*").eq("id", decoded.uid).maybeSingle();
    if (selErr) throw new HttpError(500, selErr.message);

    let profile = existing;
    if (!profile) {
      const owners = (Deno.env.get("OWNER_EMAILS") ?? "").split(",").map((s) => s.trim().toLowerCase()).filter(Boolean);
      const email = (decoded.email ?? "").toLowerCase();
      const role = email && decoded.email_verified !== false && owners.includes(email) ? "owner" : "customer";
      const fullName = String(body.full_name ?? decoded.name ?? "").slice(0, 120) || null;
      const { data, error } = await db.from("profiles").insert({
        id: decoded.uid, email: decoded.email ?? null, full_name: fullName,
        avatar_url: decoded.picture ?? null, role,
        can_manage_products: role === "owner",
      }).select("*").single();
      if (error) throw new HttpError(500, error.message);
      profile = data;
    }
    if (!profile.is_active) return json({ error: "account_disabled" }, 403);
    return json({ profile, claims_updated: claimsUpdated });
  } catch (e) {
    const status = e instanceof HttpError ? e.status : 500;
    return json({ error: (e as Error).message }, status);
  }
});
