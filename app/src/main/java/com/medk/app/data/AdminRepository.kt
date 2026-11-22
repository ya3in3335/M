package com.medk.app.data

import android.net.Uri
import com.medk.app.core.AppContainer
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** Owner / staff management operations. Privileged Firebase operations go through the admin-staff Edge Function. */
open class AdminRepository(private val c: AppContainer) {
    private val db get() = c.supabase

    open suspend fun stats(): DashboardStats =
        c.json.decodeFromString(db.postgrest.rpc("dashboard_stats").data)

    // ---------------- staff
    open suspend fun staff(): List<Profile> =
        db.from("profiles").select { filter { isIn("role", listOf("staff", "owner")) }; order("created_at", Order.ASCENDING) }.decodeList()

    open suspend fun createStaff(name: String, email: String, password: String, manageProducts: Boolean, updateOrders: Boolean): Profile {
        val res = c.edge.call("admin-staff", buildJsonObject {
            put("action", "create"); put("full_name", name); put("email", email); put("password", password)
            put("can_manage_products", manageProducts); put("can_update_orders", updateOrders)
        })
        return c.json.decodeFromJsonElement(res["profile"]!!.jsonObject)
    }

    open suspend fun setStaffPassword(uid: String, password: String) {
        c.edge.call("admin-staff", buildJsonObject { put("action", "set_password"); put("uid", uid); put("password", password) })
    }

    open suspend fun setStaffActive(uid: String, active: Boolean) {
        c.edge.call("admin-staff", buildJsonObject { put("action", "set_active"); put("uid", uid); put("active", active) })
    }

    open suspend fun setPermissions(uid: String, manageProducts: Boolean, updateOrders: Boolean, sectionIds: Set<String>) {
        db.from("profiles").update({
            set("can_manage_products", manageProducts); set("can_update_orders", updateOrders)
        }) { filter { eq("id", uid) } }
        db.from("staff_sections").delete { filter { eq("staff_id", uid) } }
        if (sectionIds.isNotEmpty()) db.from("staff_sections").insert(sectionIds.map { StaffSection(uid, it) })
    }

    open suspend fun staffSections(uid: String): Set<String> =
        db.from("staff_sections").select { filter { eq("staff_id", uid) } }.decodeList<StaffSection>().map { it.sectionId }.toSet()

    /** Sections the current staff member may work in (owner: all). */
    open suspend fun mySectionIds(profile: Profile): Set<String>? {
        if (profile.roleEnum == Role.OWNER) return null
        val explicit = staffSections(profile.id)
        val assigned = c.catalog.allSections(force = true).filter { it.assignedStaffId == profile.id }.map { it.id }
        return explicit + assigned
    }

    // ---------------- catalog
    open suspend fun updateCategory(cat: Category) {
        db.from("categories").update({
            set("name", cat.name); set("description", cat.description)
            set("sort_order", cat.sortOrder); set("is_active", cat.isActive)
        }) { filter { eq("id", cat.id) } }
        c.catalog.invalidate()
    }

    open suspend fun saveSection(id: String?, input: SectionInput) {
        if (id == null) db.from("sections").insert(input) else db.from("sections").update(input) { filter { eq("id", id) } }
        c.catalog.invalidate()
    }

    open suspend fun deleteSection(id: String) { db.from("sections").delete { filter { eq("id", id) } }; c.catalog.invalidate() }

    open suspend fun saveProduct(id: String?, input: ProductInput) {
        if (id == null) db.from("products").insert(input) else db.from("products").update(input) { filter { eq("id", id) } }
    }

    open suspend fun deleteProduct(id: String) { db.from("products").delete { filter { eq("id", id) } } }

    open suspend fun saveBanner(id: String?, input: BannerInput) {
        if (id == null) db.from("banners").insert(input) else db.from("banners").update(input) { filter { eq("id", id) } }
    }
    open suspend fun allBanners(): List<Banner> = db.from("banners").select { order("sort_order", Order.ASCENDING) }.decodeList()
    open suspend fun deleteBanner(id: String) { db.from("banners").delete { filter { eq("id", id) } } }

    open suspend fun saveP2p(id: String?, input: P2pAssetInput) {
        if (id == null) db.from("p2p_assets").insert(input) else db.from("p2p_assets").update(input) { filter { eq("id", id) } }
    }
    open suspend fun deleteP2p(id: String) { db.from("p2p_assets").delete { filter { eq("id", id) } } }

    /** Uploads to the public product-images bucket and returns the public URL. */
    open suspend fun uploadImage(uri: Uri, folder: String = "products"): String {
        val resolver = c.context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        require(mime.startsWith("image/")) { "يجب اختيار صورة" }
        val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } } ?: error("تعذر قراءة الصورة")
        require(bytes.size <= 10 * 1024 * 1024) { "حجم الصورة يتجاوز 10 ميغابايت" }
        val path = "$folder/${UUID.randomUUID()}.${mime.substringAfter('/').substringBefore('+')}"
        val bucket = db.storage.from("product-images")
        bucket.upload(path, bytes) { upsert = false; contentType = ContentType.parse(mime) }
        return bucket.publicUrl(path)
    }
}
