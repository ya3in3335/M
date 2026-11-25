package com.medk.app.data

import com.google.firebase.firestore.Query
import com.medk.app.core.AppContainer
import kotlinx.coroutines.tasks.await

/** Orders are created and changed only by Cloud Functions; the app reads them. */
open class OrderRepository(private val c: AppContainer) {
    private val db get() = c.firestore

    /** Orders visible to the current user: own orders, assigned orders (seller) or all (owner). */
    open suspend fun visibleOrders(status: OrderStatus? = null, onlyMine: Boolean = false): List<Order> {
        val uid = c.auth.currentUid ?: return emptyList()
        val role = c.auth.profile?.roleEnum ?: Role.CUSTOMER
        val base = db.collection("orders")
        val q: Query = when {
            onlyMine || role == Role.CUSTOMER -> base.whereEqualTo("customerId", uid)
            role == Role.STAFF -> base.whereEqualTo("staffId", uid)
            else -> base
        }
        return q.orderBy("createdAt", Query.Direction.DESCENDING).limit(300).get().await().documents
            .map { it.toOrder() }.filter { status == null || it.status == status.key }
    }

    open suspend fun order(id: String): Order {
        val d = db.collection("orders").document(id).get().await()
        if (!d.exists()) error("الطلب غير موجود")
        return d.toOrder()
    }

    open suspend fun updateStatus(orderId: String, status: OrderStatus) {
        c.cloud.call("setOrderStatus", mapOf("order_id" to orderId, "status" to status.key))
    }

    open suspend fun assignStaff(orderId: String, staffId: String?) {
        c.cloud.call("assignOrder", mapOf("order_id" to orderId, "staff_id" to staffId))
    }

    /** Server-side checkout: one order + conversation per assigned seller; prices are read on the server. */
    open suspend fun checkout(lines: List<CartLine>, note: String?): List<CreatedOrder> =
        c.cloud.callList("checkout", mapOf(
            "items" to lines.map { mapOf("product_id" to it.productId, "quantity" to it.quantity) },
            "note" to note?.takeIf { it.isNotBlank() },
        )).map { CreatedOrder(it["order_id"] as String, it["conversation_id"] as String, it["reference"] as String) }
}
