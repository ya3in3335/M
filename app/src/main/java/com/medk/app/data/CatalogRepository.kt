package com.medk.app.data

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.medk.app.core.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Catalog on Firestore. Customers only ever query available products (enforced by security rules). */
open class CatalogRepository(private val c: AppContainer) {
    private val db get() = c.firestore

    private var categoriesCache: List<Category>? = null
    private var sectionsCache: List<Section>? = null

    /** Staff/owner can see hidden products; customers must filter on `available == true`. */
    private val isTeam get() = c.auth.profile?.roleEnum?.let { it != Role.CUSTOMER } == true
    private fun products(): Query =
        if (isTeam) db.collection("products") else db.collection("products").whereEqualTo("available", true)

    open suspend fun categories(force: Boolean = false): List<Category> {
        if (!force) categoriesCache?.let { return it }
        return db.collection("categories").get().await().documents.map { it.toCategory() }.sortedBy { it.sortOrder }
            .also { categoriesCache = it }
    }

    open suspend fun category(slug: String) = categories().firstOrNull { it.slug == slug }
        ?: categories(force = true).firstOrNull { it.slug == slug }

    open suspend fun allSections(force: Boolean = false): List<Section> {
        if (!force) sectionsCache?.let { return it }
        return db.collection("sections").get().await().documents.map { it.toSection() }.sortedBy { it.sortOrder }
            .also { sectionsCache = it }
    }

    open suspend fun sections(categoryId: String, force: Boolean = false) =
        allSections(force).filter { it.categoryId == categoryId }

    open suspend fun section(id: String) = allSections().firstOrNull { it.id == id }
        ?: allSections(force = true).firstOrNull { it.id == id }

    open fun invalidate() { categoriesCache = null; sectionsCache = null }

    private fun List<Product>.newest() = sortedByDescending { it.createdAt }

    open suspend fun productsBySection(sectionId: String): List<Product> =
        products().whereEqualTo("sectionId", sectionId).get().await().documents.map { it.toProduct() }.newest()

    open suspend fun productsByCategory(categoryId: String): List<Product> =
        products().whereEqualTo("categoryId", categoryId).get().await().documents.map { it.toProduct() }.newest()

    open suspend fun latestProducts(limit: Long = 10): List<Product> =
        db.collection("products").whereEqualTo("available", true).orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit).get().await().documents.map { it.toProduct() }

    open suspend fun allProducts(): List<Product> =
        products().get().await().documents.map { it.toProduct() }.newest()

    open suspend fun product(id: String): Product {
        val d = db.collection("products").document(id).get().await()
        if (!d.exists()) error("المنتج غير موجود")
        return d.toProduct()
    }

    /** The catalog is small, so search runs locally over the visible products. */
    open suspend fun search(query: String): List<Product> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return allProducts().filter { it.name.contains(q, true) || it.description.orEmpty().contains(q, true) }.take(50)
    }

    open suspend fun banners(): List<Banner> =
        db.collection("banners").whereEqualTo("active", true).get().await().documents.map { it.toBanner() }.sortedBy { it.sortOrder }

    open suspend fun p2pAssets(): List<P2pAsset> =
        db.collection("p2pAssets").get().await().documents.map { it.toP2p() }.sortedBy { it.sortOrder }

    // ---------- favorites (users/{uid}/favorites/{productId})
    protected val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    open val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    open suspend fun loadFavorites(): List<Product> {
        val uid = c.auth.currentUid ?: return emptyList()
        val ids = db.collection("users").document(uid).collection("favorites").get().await().documents.map { it.id }
        _favoriteIds.value = ids.toSet()
        return ids.chunked(10).flatMap { chunk ->
            runCatching { products().whereIn(FieldPath.documentId(), chunk).get().await().documents.map { it.toProduct() } }
                .getOrDefault(emptyList())
        }
    }

    open suspend fun toggleFavorite(productId: String) {
        val uid = c.auth.currentUid ?: return
        val ref = db.collection("users").document(uid).collection("favorites").document(productId)
        if (productId in _favoriteIds.value) {
            ref.delete().await(); _favoriteIds.value -= productId
        } else {
            ref.set(mapOf("createdAt" to FieldValue.serverTimestamp())).await(); _favoriteIds.value += productId
        }
    }
}
