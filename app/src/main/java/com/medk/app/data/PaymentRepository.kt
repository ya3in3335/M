package com.medk.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.medk.app.core.AppContainer
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * In-app USDT payments. Money logic lives on the server: the app only asks for an invoice, shows balances
 * (read-only, protected by rules) and sends withdrawal requests / owner decisions.
 */
open class PaymentRepository(private val c: AppContainer) {
    private val db get() = c.firestore

    open val available: Boolean get() = c.firebaseConfigured && !c.isDemo

    /** Returns the hosted payment page URL for an open order of the current customer. */
    open suspend fun invoiceUrl(orderId: String): String =
        c.push.post("/pay", JSONObject().put("orderId", orderId)).getString("url")

    open suspend fun config(): PaymentsConfig {
        val d = db.document("config/payments").get().await()
        return PaymentsConfig(d.getBoolean("enabled") ?: true, d.getDouble("commissionPct") ?: 10.0)
    }

    open suspend fun saveConfig(cfg: PaymentsConfig) {
        require(cfg.commissionPct in 0.0..50.0) { "commission_range" }
        db.document("config/payments").set(mapOf("enabled" to cfg.enabled, "commissionPct" to cfg.commissionPct,
            "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    open suspend fun wallet(uid: String): Wallet {
        val d = db.collection("wallets").document(uid).get().await()
        return Wallet(d.getDouble("balanceUsd") ?: 0.0, d.getDouble("earnedUsd") ?: 0.0)
    }

    open suspend fun entries(uid: String): List<WalletEntry> =
        db.collection("wallets").document(uid).collection("entries").orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
            .get().await().documents.map {
                WalletEntry(it.id, it.getString("type") ?: "", it.getDouble("amountUsd") ?: 0.0, it.getString("reference"), it.getString("createdAt") ?: "")
            }

    /** Own withdrawals (seller) or every withdrawal (owner). */
    open suspend fun withdrawals(uid: String? = null): List<Withdrawal> {
        val q = if (uid != null) db.collection("withdrawals").whereEqualTo("uid", uid) else db.collection("withdrawals")
        return q.limit(200).get().await().documents.map {
            Withdrawal(it.id, it.getString("uid") ?: "", it.getString("name") ?: "", it.getDouble("amountUsd") ?: 0.0,
                it.getString("address") ?: "", it.getString("status") ?: "pending", it.getString("txid"), it.getString("createdAt") ?: "")
        }.sortedByDescending { it.createdAt }
    }

    open suspend fun requestWithdrawal(amountUsd: Double, address: String) {
        c.push.post("/withdraw", JSONObject().put("amountUsd", amountUsd).put("address", address.trim()))
    }

    open suspend fun decide(id: String, paid: Boolean, txid: String?) {
        c.push.post("/withdrawal", JSONObject().put("id", id).put("action", if (paid) "paid" else "reject").put("txid", txid ?: ""))
    }
}
