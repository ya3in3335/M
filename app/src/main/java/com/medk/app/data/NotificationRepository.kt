package com.medk.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.medk.app.MainActivity
import com.medk.app.MedkApp
import com.medk.app.R
import com.medk.app.core.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/** In-app notifications table + realtime → optional Android system notifications. */
open class NotificationRepository(private val c: AppContainer) {
    protected val _unread = MutableStateFlow(0)
    open val unread: StateFlow<Int> = _unread.asStateFlow()

    /** Conversation the user is currently viewing (no system notification for it). */
    var activeConversationId: String? = null


    // Free plan: no server writes notifications. They are derived live from the user's
    // orders and conversations; "read" state is kept on the device.
    private val prefs by lazy { c.context.getSharedPreferences("medk_notifications", android.content.Context.MODE_PRIVATE) }
    private var regs = listOf<ListenerRegistration>()
    private var orderDocs: List<Order> = emptyList()
    private var convDocs: List<Conversation> = emptyList()
    private val shown = mutableSetOf<String>()
    private var startedAt = ""
    private var uidNow: String? = null

    private fun lastSeen(uid: String) = prefs.getString("seen_$uid", "") ?: ""
    private fun readIds(uid: String) = prefs.getStringSet("read_$uid", emptySet()) ?: emptySet()

    private fun build(uid: String): List<AppNotification> {
        val role = c.auth.profile?.roleEnum ?: Role.CUSTOMER
        val seen = lastSeen(uid); val read = readIds(uid)
        val fromOrders = orderDocs.mapNotNull { o ->
            if (role == Role.CUSTOMER) {
                if (o.status == "open") null
                else AppNotification("o-${o.id}-${o.status}", uid, "تحديث الطلب ${o.reference}",
                    "حالة طلبك الآن: ${o.statusEnum.label}", null, o.id, false, o.updatedAt ?: o.createdAt)
            } else AppNotification("on-${o.id}", uid, if (role == Role.OWNER) "طلب جديد في المتجر" else "طلب جديد",
                "الطلب ${o.reference} — ${com.medk.app.core.usd(o.totalUsd)}", null, o.id, false, o.createdAt)
        }
        val fromChats = convDocs.filter { it.lastMessage != null && it.lastSenderId != null && it.lastSenderId != uid }.map { cv ->
            AppNotification("c-${cv.id}-${cv.lastMessageAt}", uid, "رسالة جديدة", cv.lastMessage, cv.id, cv.orderId, false, cv.lastMessageAt ?: "")
        }
        return (fromOrders + fromChats).sortedByDescending { it.createdAt }.take(100)
            .map { it.copy(isRead = it.createdAt <= seen || it.id in read) }
    }

    private fun publish(uid: String) {
        val list = build(uid)
        _unread.value = list.count { !it.isRead }
        list.filter { !it.isRead && it.createdAt > startedAt && shown.add(it.id) }.forEach { n ->
            if (n.conversationId != null && n.conversationId == activeConversationId) return@forEach
            // With instant (FCM) notifications active the server delivers alerts; avoid duplicates.
            if (c.push.isActive) return@forEach
            c.appScope.launch { if (c.settings.notificationsEnabled.first()) show(n) }
        }
    }

    private fun queries(uid: String): Pair<Query, Query> {
        val orders = c.firestore.collection("orders"); val convs = c.firestore.collection("conversations")
        return when (c.auth.profile?.roleEnum ?: Role.CUSTOMER) {
            Role.CUSTOMER -> orders.whereEqualTo("customerId", uid) to convs.whereEqualTo("customerId", uid)
            Role.STAFF -> orders.whereEqualTo("staffId", uid) to convs.whereEqualTo("staffId", uid)
            Role.OWNER -> orders to convs
        }.let { (o, cv) -> o.orderBy("createdAt", Query.Direction.DESCENDING).limit(50) to
            cv.orderBy("lastMessageAt", Query.Direction.DESCENDING).limit(50) }
    }

    open suspend fun list(): List<AppNotification> {
        val uid = c.auth.currentUid ?: return emptyList()
        if (regs.isEmpty()) {
            val (oq, cq) = queries(uid)
            orderDocs = oq.get().await().documents.map { it.toOrder() }
            convDocs = cq.get().await().documents.map { it.toConversation() }
        }
        return build(uid).also { l -> _unread.value = l.count { !it.isRead } }
    }

    open suspend fun markAllRead() {
        val uid = c.auth.currentUid ?: return
        prefs.edit().putString("seen_$uid", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString())
            .putStringSet("read_$uid", emptySet()).apply()
        _unread.value = 0
    }

    open suspend fun markRead(id: String) {
        val uid = c.auth.currentUid ?: return
        prefs.edit().putStringSet("read_$uid", readIds(uid) + id).apply()
        _unread.value = (_unread.value - 1).coerceAtLeast(0)
    }

    /** Live listeners on the user's orders and conversations (system notifications for new events). */
    open fun start(uid: String) {
        stop()
        uidNow = uid
        startedAt = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()
        val (oq, cq) = queries(uid)
        regs = listOf(
            oq.addSnapshotListener { s, e -> if (e == null && s != null) { orderDocs = s.documents.map { it.toOrder() }; publish(uid) } },
            cq.addSnapshotListener { s, e -> if (e == null && s != null) { convDocs = s.documents.map { it.toConversation() }; publish(uid) } },
        )
    }

    open fun stop() {
        regs.forEach { it.remove() }; regs = emptyList()
        orderDocs = emptyList(); convDocs = emptyList(); shown.clear(); uidNow = null
        _unread.value = 0
    }

    /** Shows an instant notification received while the app is open (respects the user's setting). */
    fun showPush(n: AppNotification) {
        c.appScope.launch { if (c.settings.notificationsEnabled.first()) show(n) }
    }

    @SuppressLint("MissingPermission")
    protected fun show(n: AppNotification) {
        val ctx = c.context
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            n.conversationId?.let { putExtra(MainActivity.EXTRA_CONVERSATION, it) }
            n.orderId?.let { putExtra(MainActivity.EXTRA_ORDER, it) }
        }
        val pi = PendingIntent.getActivity(ctx, n.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notif = NotificationCompat.Builder(ctx, MedkApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(n.title).setContentText(n.body)
            .setAutoCancel(true).setContentIntent(pi).build()
        NotificationManagerCompat.from(ctx).notify(n.id.hashCode(), notif)
    }
}
