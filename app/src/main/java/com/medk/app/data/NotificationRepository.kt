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
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** In-app notifications table + realtime → optional Android system notifications. */
class NotificationRepository(private val c: AppContainer) {
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    /** Conversation the user is currently viewing (no system notification for it). */
    var activeConversationId: String? = null

    private var channel: RealtimeChannel? = null
    private var job: Job? = null

    suspend fun list(): List<AppNotification> {
        val uid = c.auth.currentUid ?: return emptyList()
        val l = c.supabase.from("notifications").select {
            filter { eq("user_id", uid) }; order("created_at", Order.DESCENDING); limit(100)
        }.decodeList<AppNotification>()
        _unread.value = l.count { !it.isRead }
        return l
    }

    suspend fun markAllRead() {
        val uid = c.auth.currentUid ?: return
        c.supabase.from("notifications").update({ set("is_read", true) }) { filter { eq("user_id", uid); eq("is_read", false) } }
        _unread.value = 0
    }

    suspend fun markRead(id: String) {
        c.supabase.from("notifications").update({ set("is_read", true) }) { filter { eq("id", id) } }
        _unread.value = (_unread.value - 1).coerceAtLeast(0)
    }

    fun start(uid: String) {
        stop()
        job = c.appScope.launch {
            runCatching { list() }
            val ch = c.supabase.channel("notif-$uid")
            channel = ch
            val flow = ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "notifications"
                filter("user_id", FilterOperator.EQ, uid)
            }
            launch {
                flow.collect { action ->
                    val n = runCatching { action.decodeRecord<AppNotification>() }.getOrNull() ?: return@collect
                    _unread.value += 1
                    if (n.conversationId != null && n.conversationId == activeConversationId) return@collect
                    if (c.settings.notificationsEnabled.first()) show(n)
                }
            }
            runCatching { ch.subscribe() }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        val ch = channel; channel = null
        if (ch != null) c.appScope.launch { runCatching { c.supabase.realtime.removeChannel(ch) } }
        _unread.value = 0
    }

    @SuppressLint("MissingPermission")
    private fun show(n: AppNotification) {
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
