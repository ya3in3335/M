package com.medk.app.data

import android.net.Uri
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.medk.app.core.AppContainer
import kotlinx.coroutines.tasks.await

/**
 * Owner / seller management on the free plan: direct Firestore writes, each one
 * validated by the security rules (only the owner may change roles, permissions and activation).
 */
open class AdminRepository(private val c: AppContainer) {
    private val db get() = c.firestore
    private val users get() = db.collection("users")

    /** Dashboard numbers computed with Firestore aggregation queries (count / sum). */
    open suspend fun stats(): DashboardStats {
        val uid = c.auth.currentUid ?: error("not signed in")
        val owner = c.auth.profile?.roleEnum == Role.OWNER
        if (!owner && c.auth.profile?.canViewStats != true) error("forbidden")
        val orders = if (owner) db.collection("orders") else db.collection("orders").whereEqualTo("staffId", uid)
        // Each figure is independent: a missing index or a transient error only zeroes that tile
        // instead of failing the whole dashboard.
        suspend fun count(q: com.google.firebase.firestore.Query): Int = runCatching {
            q.count().get(com.google.firebase.firestore.AggregateSource.SERVER).await().count.toInt()
        }.getOrElse { android.util.Log.w("MedkStats", "count failed", it); 0 }
        val paidLike = orders.whereIn("status", listOf("paid", "delivered", "closed"))
        suspend fun revenue(field: String): Double = runCatching {
            val f = com.google.firebase.firestore.AggregateField.sum(field)
            paidLike.aggregate(f).get(com.google.firebase.firestore.AggregateSource.SERVER).await().getDouble(f) ?: 0.0
        }.recoverCatching {
            // Fallback without a composite index: sum client-side (bounded).
            android.util.Log.w("MedkStats", "sum($field) aggregation failed, summing locally", it)
            paidLike.limit(2000).get().await().documents.sumOf { d -> d.getDouble(field) ?: 0.0 }
        }.getOrElse { android.util.Log.w("MedkStats", "revenue failed", it); 0.0 }
        val convs = if (owner) db.collection("conversations") else db.collection("conversations").whereEqualTo("staffId", uid)
        return DashboardStats(
            totalOrders = count(orders), openOrders = count(orders.whereEqualTo("status", "open")),
            paidOrders = count(orders.whereEqualTo("status", "paid")), deliveredOrders = count(orders.whereEqualTo("status", "delivered")),
            closedOrders = count(orders.whereEqualTo("status", "closed")),
            revenueUsd = revenue("totalUsd"),
            revenueDzd = revenue("totalDzd"),
            openConversations = count(convs),
            unassignedOrders = if (owner) count(db.collection("orders").whereEqualTo("staffId", null)
                .whereIn("status", listOf("open", "paid", "delivered"))) else 0,
            products = count(db.collection("products")),
            staff = if (owner) count(users.whereEqualTo("role", "staff")) else 0,
            customers = if (owner) count(users.whereEqualTo("role", "customer")) else 0,
        )
    }

    // ---------------- people
    open suspend fun staff(): List<Profile> =
        users.whereIn("role", listOf("staff", "owner")).get().await().documents.map { it.toProfile() }.sortedBy { it.createdAt }

    open suspend fun customers(): List<Profile> =
        users.whereEqualTo("role", "customer").orderBy("createdAt", Query.Direction.DESCENDING).limit(500)
            .get().await().documents.map { it.toProfile() }

    private fun permsMap(p: StaffPermissions) = mapOf(
        "manageProducts" to p.manageProducts, "updateOrders" to p.updateOrders, "manageP2p" to p.manageP2p, "viewStats" to p.viewStats,
    )

    /**
     * Creates the seller's login with a secondary Firebase app (so the owner stays signed in),
     * then writes the seller profile — allowed by the rules only for the owner.
     */
    open suspend fun createStaff(name: String, email: String, password: String, perms: StaffPermissions): Profile {
        val main = com.google.firebase.FirebaseApp.getInstance()
        val app2 = com.google.firebase.FirebaseApp.getApps(c.context).firstOrNull { it.name == "medk-staff-creator" }
            ?: com.google.firebase.FirebaseApp.initializeApp(c.context, main.options, "medk-staff-creator")
        val auth2 = com.google.firebase.auth.FirebaseAuth.getInstance(app2)
        val user = auth2.createUserWithEmailAndPassword(email.trim(), password).await().user ?: error("تعذر إنشاء الحساب")
        runCatching { user.updateProfile(com.google.firebase.auth.userProfileChangeRequest { displayName = name }).await() }
        auth2.signOut()
        val batch = db.batch()
        batch.set(users.document(user.uid), mapOf(
            "email" to email.trim(), "fullName" to name, "avatarUrl" to null, "role" to "staff", "active" to true,
            "perms" to permsMap(perms), "sections" to emptyList<String>(),
            "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
        batch.set(db.collection("publicProfiles").document(user.uid), mapOf("displayName" to name, "avatarUrl" to null, "role" to "staff"))
        batch.commit().await()
        return Profile(user.uid, email.trim(), name, role = "staff", canManageProducts = perms.manageProducts, canUpdateOrders = perms.updateOrders,
            canManageP2p = perms.manageP2p, canViewStats = perms.viewStats)
    }

    /** Suspends / reactivates an account: the rules then deny every read and write. */
    open suspend fun setUserActive(uid: String, active: Boolean) {
        users.document(uid).update(mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    /** Promotes a customer to seller or demotes a seller to customer. */
    open suspend fun setRole(uid: String, role: Role) {
        val snap = users.document(uid).get().await()
        val r = role.name.lowercase()
        val batch = db.batch()
        batch.update(users.document(uid), mapOf("role" to r, "perms" to DEFAULT_PERMS, "sections" to emptyList<String>(),
            "updatedAt" to FieldValue.serverTimestamp()))
        batch.set(db.collection("publicProfiles").document(uid), mapOf("displayName" to snap.getString("fullName"),
            "avatarUrl" to snap.getString("avatarUrl"), "role" to r))
        batch.commit().await()
    }

    open suspend fun setPermissions(uid: String, perms: StaffPermissions, sectionIds: Set<String>) {
        users.document(uid).update(mapOf("perms" to permsMap(perms), "sections" to sectionIds.toList(),
            "updatedAt" to FieldValue.serverTimestamp())).await()
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

    /** Compresses the image and stores it in Firestore; returns a "media:<id>" reference. */
    @Suppress("UNUSED_PARAMETER")
    open suspend fun uploadImage(uri: Uri, folder: String = "products"): String {
        require(c.context.contentResolver.getType(uri)?.startsWith("image/") == true) { "يجب اختيار صورة" }
        return c.media.uploadCatalogImage(uri)
    }
}
