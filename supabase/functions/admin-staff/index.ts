// Owner-only staff management. Firebase Admin operations need privileged
// credentials, so they live here and never in the Android app.
// Actions: create | set_password | set_active | set_role
import { adminDb, cors, firebaseAuth, HttpError, json, verifyCaller } from "../_shared/firebase.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  try {
    const caller = await verifyCaller(req);
    const db = adminDb();
    const { data: me } = await db.from("profiles").select("role,is_active").eq("id", caller.uid).maybeSingle();
    if (!me || me.role !== "owner" || !me.is_active) throw new HttpError(403, "owner only");

    const body = await req.json();
    const auth = firebaseAuth();
    switch (body.action) {
      case "create": {
        const email = String(body.email ?? "").trim();
        const password = String(body.password ?? "");
        const fullName = String(body.full_name ?? "").trim();
        if (!email || password.length < 8) throw new HttpError(400, "email and a password of at least 8 characters are required");
        const user = await auth.createUser({ email, password, displayName: fullName || undefined, emailVerified: true });
        await auth.setCustomUserClaims(user.uid, { role: "authenticated" });
        const { data, error } = await db.from("profiles").upsert({
          id: user.uid, email, full_name: fullName || null, role: "staff", is_active: true,
          can_manage_products: !!body.can_manage_products, can_update_orders: body.can_update_orders !== false,
          can_manage_p2p: !!body.can_manage_p2p, can_view_stats: !!body.can_view_stats,
        }).select("*").single();
        if (error) throw new HttpError(500, error.message);
        return json({ profile: data });
      }
      case "set_password": {
        const password = String(body.password ?? "");
        if (password.length < 8) throw new HttpError(400, "password must be at least 8 characters");
        await assertStaff(db, body.uid);
        await auth.updateUser(body.uid, { password });
        await auth.revokeRefreshTokens(body.uid);
        return json({ ok: true });
      }
      case "set_active": {
        // staff or customers (never the owner account itself)
        await assertNotOwner(db, body.uid);
        const active = !!body.active;
        await auth.updateUser(body.uid, { disabled: !active });
        if (!active) await auth.revokeRefreshTokens(body.uid);
        const { error } = await db.from("profiles").update({ is_active: active }).eq("id", body.uid);
        if (error) throw new HttpError(500, error.message);
        return json({ ok: true });
      }
      case "set_role": {
        const role = String(body.role);
        if (!["customer", "staff"].includes(role)) throw new HttpError(400, "invalid role");
        if (body.uid === caller.uid) throw new HttpError(400, "cannot change your own role");
        await assertNotOwner(db, body.uid);
        const { error } = await db.from("profiles").update({ role }).eq("id", body.uid);
        if (error) throw new HttpError(500, error.message);
        return json({ ok: true });
      }
      default:
        throw new HttpError(400, "unknown action");
    }
  } catch (e) {
    const status = e instanceof HttpError ? e.status : 500;
    return json({ error: (e as Error).message }, status);
  }
});

// deno-lint-ignore no-explicit-any
async function assertNotOwner(db: any, uid: string) {
  const { data } = await db.from("profiles").select("role").eq("id", uid).maybeSingle();
  if (!data || data.role === "owner") throw new HttpError(400, "cannot change an owner account");
}

// deno-lint-ignore no-explicit-any
async function assertStaff(db: any, uid: string) {
  const { data } = await db.from("profiles").select("role").eq("id", uid).maybeSingle();
  if (!data || data.role !== "staff") throw new HttpError(400, "target is not a staff member");
}
