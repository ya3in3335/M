package com.medk.app.data

import android.net.Uri
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.storage.StorageMetadata
import com.medk.app.core.AppContainer
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Owner / seller management. Account, role and permission changes go through Cloud Functions;
 * catalog edits are direct Firestore writes validated by the security rules.
 */
open class AdminRepository(private val c: AppContainer) {
    private val db get() = c.firestore
    private val users get() = db.collection("users")

    @Suppress("UNCHECKED_CAST")
    open suspend fun stats(): DashboardStats {
        val m = c.cloud.call("dashboardStats")
        fun i(k: String) = (m[k] as? Number)?.toInt() ?: 0
        fun d(k: String) = (m[k] as? Number)?.toDouble() ?: 0.0
        return DashboardStats(i("total_orders"), i("open_orders"), i("paid_orders"), i("delivered_orders"), i("closed_orders"),
            d("revenue_usd"), d("revenue_dzd"), i("open_conversations"), i("unassigned_orders"), i("products"), i("staff"), i("customers"))
    }

    // ---------------- people
    open suspend fun staff(): List<Profile> =
        users.whereIn("role", listOf("staff", "owner")).get().await().documents.map { it.toProfile() }.sortedBy { it.createdAt }

    open suspend fun customers(): List<Profile> =
        users.whereEqualTo("role", "customer").orderBy("createdAt", Query.Direction.DESCENDING).limit(500)
            .get().await().documents.map { it.toProfile() }

    private fun permsMap(p: StaffPermissions) = mapOf(
        "manage_products" to p.manageProducts, "update_orders" to p.updateOrders,
        "manage_p2p" to p.manageP2p, "view_stats" to p.viewStats,
    )

    @Suppress("UNCHECKED_CAST")
    open suspend fun createStaff(name: String, email: String, password: String, perms: StaffPermissions): Profile {
        val res = c.cloud.call("adminCreateStaff", mapOf("full_name" to name, "email" to email, "password" to password, "perms" to permsMap(perms)))
        return profileFromFunction(res["profile"] as Map<String, Any?>)
    }

    open suspend fun setStaffPassword(uid: String, password: String) {
        c.cloud.call("adminSetPassword", mapOf("uid" to uid, "password" to password))
    }

    /** Activates / suspends any seller or customer account (Firebase Auth + profile). */
    open suspend fun setUserActive(uid: String, active: Boolean) {
        c.cloud.call("adminSetActive", mapOf("uid" to uid, "active" to active))
    }

    /** Promotes a customer to seller or demotes a seller to customer. */
    open suspend fun setRole(uid: String, role: Role) {
        c.cloud.call("adminSetRole", mapOf("uid" to uid, "role" to role.name.lowercase()))
    }

    open suspend fun setPermissions(uid: String, perms: StaffPermissions, sectionIds: Set<String>) {
        c.cloud.call("adminSetPermissions", mapOf("uid" to uid, "perms" to permsMap(perms), "sections" to sectionIds.toList()))
    }

    @Suppress("UNCHECKED_CAST")
    open suspend fun staffSections(uid: String): Set<String> =
        (users.document(uid).get().await().get("sections") as? List<String>).orEmpty().toSet()

    /** Sections the current seller may work in (owner: all). */
    open suspend fun mySectionIds(profile: Profile): Set<String>? {
        if (profile.roleEnum == Role.OWNER) return null
        val explicit = staffSections(profile.id)
        val assigned = c.catalog.allSections(force = true).filter { it.assignedStaffId == profile.id }.map { it.id }
        return explicit + assigned
    }

    // ---------------- catalog
    open suspend fun updateCategory(cat: Category) {
        db.collection("categories").document(cat.id).update(mapOf(
            "name" to cat.name, "description" to cat.description, "sortOrder" to cat.sortOrder, "active" to cat.isActive,
        )).await()
        c.catalog.invalidate()
    }

    open suspend fun saveSection(id: String?, input: SectionInput) {
        val data = mapOf(
            "categoryId" to input.categoryId, "name" to input.name, "description" to input.description, "imageUrl" to input.imageUrl,
            "assignedStaffId" to input.assignedStaffId, "sortOrder" to input.sortOrder, "active" to input.isActive,
        )
        if (id == null) db.collection("sections").add(data + ("createdAt" to FieldValue.serverTimestamp())).await()
        else db.collection("sections").document(id).update(data).await()
        c.catalog.invalidate()
    }

    open suspend fun deleteSection(id: String) { db.collection("sections").document(id).delete().await(); c.catalog.invalidate() }

    open suspend fun saveProduct(id: String?, input: ProductInput) {
        val data = mapOf(
            "categoryId" to input.categoryId, "sectionId" to input.sectionId, "name" to input.name, "description" to input.description,
            "priceUsd" to input.priceUsd, "priceDzd" to input.priceDzd, "stock" to input.stock, "available" to input.isAvailable,
            "images" to input.images, "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (id == null) db.collection("products").add(data + ("createdAt" to FieldValue.serverTimestamp())).await()
        else db.collection("products").document(id).update(data).await()
    }

    open suspend fun deleteProduct(id: String) { db.collection("products").document(id).delete().await() }

    open suspend fun saveBanner(id: String?, input: BannerInput) {
        val data = mapOf("title" to input.title, "subtitle" to input.subtitle, "imageUrl" to input.imageUrl,
            "categorySlug" to input.categorySlug, "sortOrder" to input.sortOrder, "active" to input.isActive)
        if (id == null) db.collection("banners").add(data).await() else db.collection("banners").document(id).set(data).await()
    }
    open suspend fun allBanners(): List<Banner> = db.collection("banners").get().await().documents.map { it.toBanner() }.sortedBy { it.sortOrder }
    open suspend fun deleteBanner(id: String) { db.collection("banners").document(id).delete().await() }

    open suspend fun saveP2p(id: String?, input: P2pAssetInput) {
        val data = mapOf(
            "code" to input.code, "name" to input.name, "network" to input.network, "buyPriceDzd" to input.buyPriceDzd,
            "sellPriceDzd" to input.sellPriceDzd, "minAmount" to input.minAmount, "maxAmount" to input.maxAmount,
            "active" to input.isActive, "updatedAt" to FieldValue.serverTimestamp(),
        )
        val col = db.collection("p2pAssets")
        if (id == null) col.add(data + ("sortOrder" to col.get().await().size())).await() else col.document(id).update(data).await()
    }
    open suspend fun deleteP2p(id: String) { db.collection("p2pAssets").document(id).delete().await() }

    /** Uploads to catalog/{folder}/ (public read) and returns the download URL. */
    open suspend fun uploadImage(uri: Uri, folder: String = "products"): String {
        val resolver = c.context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        require(mime in setOf("image/jpeg", "image/png", "image/webp", "image/gif")) { "يجب اختيار صورة JPG أو PNG أو WEBP" }
        val ref = c.storage.reference.child("catalog/$folder/${UUID.randomUUID()}.${mime.substringAfter('/')}")
        ref.putFile(uri, StorageMetadata.Builder().setContentType(mime).build()).await()
        return ref.downloadUrl.await().toString()
    }
}
