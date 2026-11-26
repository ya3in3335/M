package com.medk.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.medk.app.core.AppContainer
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

/**
 * Orders on the free plan: written by the app, validated by the security rules
 * (price, totals, seller and stock are compared with the database on Google's servers).
 */
open class OrderRepository(private val c: AppContainer) {
    private val db get() = c.firestore
    private val rnd = SecureRandom()

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
        val o = order(orderId)
        val now = FieldValue.serverTimestamp()
        val patch = mutableMapOf<String, Any>("status" to status.key, "updatedAt" to now)
        if (status != OrderStatus.OPEN && o.paidAt == null) patch["paidAt"] = now
        if ((status == OrderStatus.DELIVERED || status == OrderStatus.CLOSED) && o.deliveredAt == null) patch["deliveredAt"] = now
        if (status == OrderStatus.CLOSED) patch["closedAt"] = now
        db.collection("orders").document(orderId).update(patch).await()
    }

    open suspend fun assignStaff(orderId: String, staffId: String?) {
        val batch = db.batch()
        batch.update(db.collection("orders").document(orderId), mapOf("staffId" to staffId, "updatedAt" to FieldValue.serverTimestamp()))
        db.collection("conversations").whereEqualTo("orderId", orderId).get().await().documents
            .forEach { batch.update(it.reference, "staffId", staffId) }
        batch.commit().await()
    }

    private fun reference(): String {
        val abc = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return "MK-" + (1..8).map { abc[rnd.nextInt(abc.length)] }.joinToString("")
    }

    /**
     * One order + conversation per cart line, each in its own atomic batch.
     * Prices are re-read from the database; the rules reject anything that does not match.
     */
    open suspend fun checkout(lines: List<CartLine>, note: String?): List<CreatedOrder> {
        val uid = c.auth.currentUid ?: error("not signed in")
        c.auth.ensureEmailVerified()
        val created = mutableListOf<CreatedOrder>()
        val failures = mutableListOf<String>()
        for (line in lines) {
            try {
                val p = db.collection("products").document(line.productId).get().await().toProduct()
                if (!p.isAvailable) error("no available products")
                if (p.stock != null && p.stock < line.quantity) error("insufficient stock")
                val staffId = p.sectionId?.let { sid -> db.collection("sections").document(sid).get().await().getString("assignedStaffId") }
                val sectionName = p.sectionId?.let { c.catalog.section(it)?.name }
                val ref = reference()
                val usd = p.priceUsd * line.quantity
                val dzd = p.priceDzd * line.quantity
                val now = FieldValue.serverTimestamp()
                val orderRef = db.collection("orders").document()
                val convRef = db.collection("conversations").document()
                val body = "مرحباً، أرغب في إتمام الطلب $ref: ${p.name} × ${line.quantity}. الإجمالي: ${"%.2f".format(java.util.Locale.US, usd)} USD / ${dzd.toLong()} DZD" +
                    (note?.takeIf { it.isNotBlank() }?.let { "\nملاحظة: $it" } ?: "")
                val batch = db.batch()
                batch.set(orderRef, mapOf(
                    "reference" to ref, "customerId" to uid, "staffId" to staffId, "sectionId" to p.sectionId, "status" to "open",
                    "totalUsd" to usd, "totalDzd" to dzd, "note" to note?.takeIf { it.isNotBlank() }?.take(500),
                    "items" to listOf(mapOf("productId" to p.id, "productName" to p.name, "imageUrl" to p.cover, "quantity" to line.quantity,
                        "unitPriceUsd" to p.priceUsd, "unitPriceDzd" to p.priceDzd)),
                    "createdAt" to now, "updatedAt" to now, "paidAt" to null, "deliveredAt" to null, "closedAt" to null,
                ))
                if (p.stock != null) batch.update(db.collection("products").document(p.id),
                    mapOf("stock" to p.stock - line.quantity, "lastOrderId" to orderRef.id))
                batch.set(convRef, mapOf(
                    "customerId" to uid, "staffId" to staffId, "orderId" to orderRef.id, "productId" to null, "sectionId" to p.sectionId,
                    "subject" to ("طلب $ref" + (sectionName?.let { " — $it" } ?: "")).take(140),
                    "lastMessage" to body.take(200), "lastMessageAt" to now, "lastSenderId" to uid, "createdAt" to now,
                ))
                batch.set(convRef.collection("messages").document(), mapOf("senderId" to uid, "body" to body, "attachment" to null, "createdAt" to now))
                batch.commit().await()
                created += CreatedOrder(orderRef.id, convRef.id, ref)
            } catch (e: Exception) {
                failures += line.name
                if (created.isEmpty() && line == lines.last()) throw e
            }
        }
        if (created.isEmpty()) error("no available products")
        return created
    }
}
