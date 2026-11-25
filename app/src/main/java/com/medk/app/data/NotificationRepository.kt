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
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/** In-app notifications table + realtime → optional Android system notifications. */
open class NotificationRepository(private val c: AppContainer) {
    protected val _unread = MutableStateFlow(0)
    open val unread: StateFlow<Int> = _unread.asStateFlow()

    /** Conversation the user is currently viewing (no system notification for it). */
    var activeConversationId: String? = null


    private fun col(uid: String) = c.firestore.collection("users").document(uid).collection("notifications")
    private var registration: ListenerRegistration? = null

    open suspend fun list(): List<AppNotification> {
        val uid = c.auth.currentUid ?: return emptyList()
        val l = col(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(100).get().await().documents.map { it.toNotification(uid) }
        _unread.value = l.count { !it.isRead }
        return l
    }

    open suspend fun markAllRead() {
        val uid = c.auth.currentUid ?: return
        val unread = col(uid).whereEqualTo("read", false).limit(400).get().await()
        if (!unread.isEmpty) c.firestore.batch().apply { unread.documents.forEach { update(it.reference, "read", true) } }.commit().await()
        _unread.value = 0
    }

    open suspend fun markRead(id: String) {
        val uid = c.auth.currentUid ?: return
        col(uid).document(id).update("read", true).await()
        _unread.value = (_unread.value - 1).coerceAtLeast(0)
    }

    /** Listens for new notifications while the app is running and shows them as system notifications. */
    open fun start(uid: String) {
        stop()
        var first = true
        registration = col(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(30).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            _unread.value = snap.documents.count { it.getBoolean("read") == false }
            if (first) { first = false; return@addSnapshotListener }
            snap.documentChanges.filter { it.type == DocumentChange.Type.ADDED && !it.document.metadata.hasPendingWrites() }.forEach { ch ->
                val n = ch.document.toNotification(uid)
                if (n.conversationId != null && n.conversationId == activeConversationId) return@forEach
                c.appScope.launch { if (c.settings.notificationsEnabled.first()) show(n) }
            }
        }
    }

    open fun stop() {
        registration?.remove(); registration = null
        _unread.value = 0
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
