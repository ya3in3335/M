package com.medk.app.data

import com.google.firebase.firestore.FieldValue
import com.medk.app.core.AppContainer
import kotlinx.coroutines.tasks.await

/**
 * Manual payments: the customer pays the seller directly (BaridiMob, CCP, RedotPay…), sends the receipt in the
 * order chat and declares it; the seller confirms ("paid"). The app owner's commission % is stamped on the order
 * at confirmation (enforced by the security rules) and each seller's due commission is tracked here.
 */
open class PaymentRepository(private val c: AppContainer) {
    private val db get() = c.firestore

    open val available: Boolean get() = c.firebaseConfigured && !c.isDemo

    // ----------------------------------------------------------------- commission %
    open suspend fun commissionPct(): Double =
        db.document("config/payments").get().await().getDouble("commissionPct") ?: 0.0

    open suspend fun saveCommissionPct(pct: Double) {
        require(pct in 0.0..50.0) { "commission_range" }
        db.document("config/payments").set(mapOf("commissionPct" to pct, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    // ----------------------------------------------------------------- payment methods
    /** Methods of a seller (uid) or of the store ("store": orders without a seller). */
    @Suppress("UNCHECKED_CAST")
    open suspend fun methods(ownerId: String): List<PaymentMethod> =
        (db.collection("paymentMethods").document(ownerId).get().await().get("methods") as? List<Map<String, Any?>>).orEmpty().map {
            PaymentMethod(it["type"] as? String ?: "other", it["title"] as? String ?: "", it["account"] as? String ?: "",
                it["holder"] as? String, it["note"] as? String)
        }

    open suspend fun saveMethods(ownerId: String, list: List<PaymentMethod>) {
        db.collection("paymentMethods").document(ownerId).set(mapOf(
            "methods" to list.take(8).map { m ->
                mapOf("type" to m.type, "title" to m.title.trim().take(40), "account" to m.account.trim().take(120),
                    "holder" to m.holder?.trim()?.take(80)?.ifBlank { null }, "note" to m.note?.trim()?.take(200)?.ifBlank { null })
            },
            "updatedAt" to FieldValue.serverTimestamp(),
        )).await()
    }

    /** Where the customer of this order must pay. */
    open suspend fun methodsForOrder(order: Order): List<PaymentMethod> = methods(order.staffId ?: "store")

    // ----------------------------------------------------------------- customer declares payment
    open suspend fun claimPayment(orderId: String, method: String) {
        db.collection("orders").document(orderId).update(mapOf(
            "paymentClaim" to mapOf("method" to method.take(60), "at" to FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp(),
        )).await()
        c.push.notify("paymentClaim", "orderId" to orderId)
    }

    // ----------------------------------------------------------------- commissions & settlements
    open suspend fun settlements(sellerId: String): List<Settlement> =
        db.collection("settlements").whereEqualTo("sellerId", sellerId).get().await().documents.map {
            Settlement(it.id, it.getString("sellerId") ?: "", it.getDouble("amountDzd") ?: 0.0, it.getDouble("amountUsd") ?: 0.0,
                it.getString("note"), it.getTimestamp("createdAt")?.toDate()?.toInstant()?.toString() ?: "")
        }.sortedByDescending { it.createdAt }

    open suspend fun addSettlement(sellerId: String, dzd: Double, usd: Double, note: String?) {
        db.collection("settlements").add(mapOf("sellerId" to sellerId, "amountDzd" to dzd, "amountUsd" to usd,
            "note" to note?.trim()?.take(200)?.ifBlank { null }, "createdBy" to c.auth.currentUid,
            "createdAt" to FieldValue.serverTimestamp())).await()
    }

    /** Commission due by a seller: confirmed orders (paid / delivered / closed) × stamped % − settlements. */
    open suspend fun summary(sellerId: String): CommissionSummary {
        val orders = db.collection("orders").whereEqualTo("staffId", sellerId).limit(2000).get().await().documents
            .map { it.toOrder() }.filter { it.status != "open" }
        val paid = settlements(sellerId)
        return CommissionSummary(
            confirmedOrders = orders.size,
            salesDzd = orders.sumOf { it.totalDzd }, salesUsd = orders.sumOf { it.totalUsd },
            commissionDzd = orders.sumOf { it.totalDzd * (it.commissionPct ?: 0.0) / 100 },
            commissionUsd = orders.sumOf { it.totalUsd * (it.commissionPct ?: 0.0) / 100 },
            paidDzd = paid.sumOf { it.amountDzd }, paidUsd = paid.sumOf { it.amountUsd },
        )
    }
}
