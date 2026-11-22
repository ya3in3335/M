package com.medk.app.data

import com.medk.app.core.AppContainer
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order as SortOrder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

open class OrderRepository(private val c: AppContainer) {
    private val db get() = c.supabase
    private val withItems = Columns.raw("*, order_items(*)")

    /** Orders visible to the current user (RLS scopes customers/staff/owner). */
    open suspend fun visibleOrders(status: OrderStatus? = null, onlyMine: Boolean = false): List<Order> {
        val uid = c.auth.currentUid
        return db.from("orders").select(withItems) {
            filter {
                if (status != null) eq("status", status.key)
                if (onlyMine && uid != null) eq("customer_id", uid)
            }
            order("created_at", SortOrder.DESCENDING)
        }.decodeList()
    }

    open suspend fun order(id: String): Order =
        db.from("orders").select(withItems) { filter { eq("id", id) } }.decodeSingle()

    open suspend fun updateStatus(orderId: String, status: OrderStatus) {
        db.from("orders").update({ set("status", status.key) }) { filter { eq("id", orderId) } }
    }

    open suspend fun assignStaff(orderId: String, staffId: String?) {
        db.from("orders").update({ set("staff_id", staffId) }) { filter { eq("id", orderId) } }
    }

    /** Server-side checkout: one order + conversation per assigned sales representative. */
    open suspend fun checkout(lines: List<CartLine>, note: String?): List<CreatedOrder> {
        val items = buildJsonArray {
            lines.forEach { l -> add(buildJsonObject { put("product_id", l.productId); put("quantity", l.quantity) }) }
        }
        return db.postgrest.rpc("create_orders_from_cart", buildJsonObject {
            put("p_items", items); put("p_note", note?.takeIf { it.isNotBlank() })
        }).decodeList()
    }
}
