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

    /** Adds a line; packages / perUnit products are separate lines per package, amount and typed input. */
    suspend fun add(product: Product, quantity: Int, sectionName: String?, packageIndex: Int? = null, amount: Int? = null,
                    input: String? = null) = mutate { list ->
        val (usd, dzd) = product.unitPrices(packageIndex, amount) ?: return@mutate
        val units = when { product.isPackages -> product.packages[packageIndex!!].amount; product.isPerUnit -> amount; else -> null }
        val max = product.stock ?: 99
        val q = if (product.isPerUnit) 1 else quantity.coerceIn(1, max)
        val line = CartLine(product.id, product.name, product.cover, usd, dzd, q, product.sectionId, sectionName,
            amount = units, packageIndex = packageIndex.takeIf { product.isPackages }, unit = product.unit.takeIf { units != null },
            input = input?.trim()?.ifBlank { null }?.take(100), inputLabel = product.inputLabel)
        val i = list.indexOfFirst { it.key == line.key }
        when {
            i < 0 -> list += line
            product.isPerUnit -> list[i] = line
            else -> list[i] = list[i].copy(quantity = (list[i].quantity + q).coerceAtMost(max), priceUsd = usd, priceDzd = dzd,
                name = product.name, image = product.cover)
        }
    }

    suspend fun setQuantity(key: String, q: Int) = mutate { list ->
        val i = list.indexOfFirst { it.key == key }
        if (i >= 0) { if (q <= 0) list.removeAt(i) else if (list[i].amount == null || list[i].packageIndex != null)
            list[i] = list[i].copy(quantity = q.coerceAtMost(99)) }
    }

    suspend fun remove(key: String) = mutate { list -> list.removeAll { it.key == key } }
    suspend fun removeAll(keys: Collection<String>) = mutate { list -> list.removeAll { it.key in keys } }
    suspend fun clear() = mutate { it.clear() }
}
