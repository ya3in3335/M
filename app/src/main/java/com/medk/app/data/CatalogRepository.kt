package com.medk.app.data

import com.medk.app.core.AppContainer
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CatalogRepository(private val c: AppContainer) {
    private val db get() = c.supabase

    private var categoriesCache: List<Category>? = null
    private var sectionsCache: List<Section>? = null

    suspend fun categories(force: Boolean = false): List<Category> {
        if (!force) categoriesCache?.let { return it }
        return db.from("categories").select { order("sort_order", Order.ASCENDING) }
            .decodeList<Category>().also { categoriesCache = it }
    }

    suspend fun category(slug: String) = categories().firstOrNull { it.slug == slug }
        ?: categories(force = true).firstOrNull { it.slug == slug }

    suspend fun allSections(force: Boolean = false): List<Section> {
        if (!force) sectionsCache?.let { return it }
        return db.from("sections").select { order("sort_order", Order.ASCENDING) }
            .decodeList<Section>().also { sectionsCache = it }
    }

    suspend fun sections(categoryId: String, force: Boolean = false) =
        allSections(force).filter { it.categoryId == categoryId }

    suspend fun section(id: String) = allSections().firstOrNull { it.id == id }
        ?: allSections(force = true).firstOrNull { it.id == id }

    fun invalidate() { categoriesCache = null; sectionsCache = null }

    suspend fun productsBySection(sectionId: String): List<Product> =
        db.from("products").select {
            filter { eq("section_id", sectionId) }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    suspend fun productsByCategory(categoryId: String): List<Product> =
        db.from("products").select {
            filter { eq("category_id", categoryId) }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    suspend fun latestProducts(limit: Long = 10): List<Product> =
        db.from("products").select {
            filter { eq("is_available", true) }
            order("created_at", Order.DESCENDING); limit(limit)
        }.decodeList()

    suspend fun allProducts(): List<Product> =
        db.from("products").select { order("created_at", Order.DESCENDING) }.decodeList()

    suspend fun product(id: String): Product =
        db.from("products").select { filter { eq("id", id) } }.decodeSingle()

    suspend fun search(query: String): List<Product> {
        val q = query.trim().replace("%", "").replace(",", " ")
        if (q.isEmpty()) return emptyList()
        return db.from("products").select {
            filter { or { ilike("name", "%$q%"); ilike("description", "%$q%") } }
            order("created_at", Order.DESCENDING); limit(50)
        }.decodeList()
    }

    suspend fun banners(): List<Banner> =
        db.from("banners").select { filter { eq("is_active", true) }; order("sort_order", Order.ASCENDING) }.decodeList()

    suspend fun p2pAssets(): List<P2pAsset> =
        db.from("p2p_assets").select { order("sort_order", Order.ASCENDING) }.decodeList()

    // ---------- favorites
    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    suspend fun loadFavorites(): List<Product> {
        val uid = c.auth.currentUid ?: return emptyList()
        val ids = db.from("favorites").select { filter { eq("user_id", uid) } }.decodeList<Favorite>().map { it.productId }
        _favoriteIds.value = ids.toSet()
        if (ids.isEmpty()) return emptyList()
        return db.from("products").select { filter { isIn("id", ids) } }.decodeList()
    }

    suspend fun toggleFavorite(productId: String) {
        val uid = c.auth.currentUid ?: return
        if (productId in _favoriteIds.value) {
            db.from("favorites").delete { filter { eq("user_id", uid); eq("product_id", productId) } }
            _favoriteIds.value -= productId
        } else {
            db.from("favorites").insert(Favorite(uid, productId))
            _favoriteIds.value += productId
        }
    }
}
