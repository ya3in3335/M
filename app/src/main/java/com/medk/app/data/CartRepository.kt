package com.medk.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

private val Context.cartStore by preferencesDataStore("medk_cart")

/** Shopping cart persisted locally on the device. */
class CartRepository(private val context: Context, private val json: Json) {
    private val key = stringPreferencesKey("lines")

    val lines: Flow<List<CartLine>> = context.cartStore.data.map { decode(it[key]) }
    val count: Flow<Int> = lines.map { l -> l.sumOf { it.quantity } }

    private fun decode(s: String?): List<CartLine> =
        if (s.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString<List<CartLine>>(s) }.getOrDefault(emptyList())

    private suspend fun mutate(block: (MutableList<CartLine>) -> Unit) {
        context.cartStore.edit { prefs ->
            val list = decode(prefs[key]).toMutableList()
            block(list)
            prefs[key] = json.encodeToString(list.toList())
        }
    }

    suspend fun add(product: Product, quantity: Int, sectionName: String?) = mutate { list ->
        val i = list.indexOfFirst { it.productId == product.id }
        val max = product.stock ?: 99
        if (i >= 0) list[i] = list[i].copy(quantity = (list[i].quantity + quantity).coerceAtMost(max),
            priceUsd = product.priceUsd, priceDzd = product.priceDzd, name = product.name, image = product.cover)
        else list += CartLine(product.id, product.name, product.cover, product.priceUsd, product.priceDzd,
            quantity.coerceIn(1, max), product.sectionId, sectionName)
    }

    suspend fun setQuantity(productId: String, q: Int) = mutate { list ->
        val i = list.indexOfFirst { it.productId == productId }
        if (i >= 0) { if (q <= 0) list.removeAt(i) else list[i] = list[i].copy(quantity = q.coerceAtMost(99)) }
    }

    suspend fun remove(productId: String) = mutate { list -> list.removeAll { it.productId == productId } }
    suspend fun removeAll(ids: Collection<String>) = mutate { list -> list.removeAll { it.productId in ids } }
    suspend fun clear() = mutate { it.clear() }
}
