package com.medk.app.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.messaging.FirebaseMessaging
import com.medk.app.BuildConfig
import com.medk.app.core.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Instant (FCM) notifications on the free plan.
 *
 * The app never sends a notification itself: it only tells the MED-K push server (a Cloudflare Worker)
 * "I just created order X / message Y". The server verifies the caller's Firebase ID token, re-reads the
 * document from Firestore, checks the caller really is its author, decides who must be notified and sends it.
 * The server URL is read from Firestore (config/push), so no rebuild is needed when it changes.
 */
open class PushRepository(private val c: AppContainer) {

    @Volatile private var serverUrl: String? = null
    @Volatile private var tokenDocId: String? = null

    /** True once this device is registered and the server exists → listener-based local alerts are muted. */
    open val isActive: Boolean get() = serverUrl != null && tokenDocId != null

    private val enabled get() = c.firebaseConfigured && !c.isDemo

    private fun hash(token: String) =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Called after sign-in and whenever FCM rotates the token. */
    open fun register(uid: String, token: String? = null) {
        if (!enabled) return
        c.appScope.launch {
            runCatching {
                serverUrl = c.firestore.document("config/push").get().await().getString("url")?.trimEnd('/')
                val t = token ?: FirebaseMessaging.getInstance().token.await()
                val id = hash(t)
                c.firestore.collection("pushTokens").document(id).set(mapOf(
                    "uid" to uid, "token" to t, "app" to if (BuildConfig.ADMIN_APP) "admin" else "store",
                    "updatedAt" to FieldValue.serverTimestamp(),
                )).await()
                tokenDocId = id
            }.onFailure { Log.w(TAG, "push register failed", it) }
        }
    }

    /** Called before sign-out so this phone stops receiving the previous user's notifications. */
    open suspend fun unregister() {
        val id = tokenDocId ?: return
        tokenDocId = null
        runCatching { c.firestore.collection("pushTokens").document(id).delete().await() }
            .onFailure { Log.w(TAG, "push unregister failed", it) }
    }

    /** Fire-and-forget: ask the server to notify the other party about an event. Never throws. */
    open fun notify(type: String, vararg pairs: Pair<String, String>) {
        if (!enabled) return
        c.appScope.launch {
            runCatching {
                val base = serverUrl ?: c.firestore.document("config/push").get().await().getString("url")?.trimEnd('/')
                    ?.also { serverUrl = it } ?: return@launch
                val idToken = c.firebaseAuth?.currentUser?.getIdToken(false)?.await()?.token ?: return@launch
                val body = JSONObject().put("type", type).apply { pairs.forEach { (k, v) -> put(k, v) } }.toString()
                withContext(Dispatchers.IO) {
                    val conn = URL("$base/notify").openConnection() as HttpURLConnection
                    try {
                        conn.requestMethod = "POST"
                        conn.connectTimeout = 10_000; conn.readTimeout = 15_000
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.setRequestProperty("Authorization", "Bearer $idToken")
                        conn.outputStream.use { it.write(body.toByteArray()) }
                        val code = conn.responseCode
                        if (code !in 200..299) Log.w(TAG, "notify $type → HTTP $code")
                    } finally { conn.disconnect() }
                }
            }.onFailure { Log.w(TAG, "notify $type failed", it) }
        }
    }

    companion object { private const val TAG = "MedkPush" }
}
