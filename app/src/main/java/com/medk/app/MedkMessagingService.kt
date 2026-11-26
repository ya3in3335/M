package com.medk.app

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.medk.app.data.AppNotification

/**
 * Receives instant notifications. When the app is in the background Android shows them by itself
 * (the server sets the channel); when it is open we show them here, except for the chat already on screen.
 */
class MedkMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val c = MedkApp.container
        c.auth.currentUid?.let { c.push.register(it, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val c = MedkApp.container
        val n = message.notification ?: return
        val conv = message.data["conversation_id"]
        if (conv != null && conv == c.notifications.activeConversationId) return
        c.notifications.showPush(
            AppNotification(
                id = message.data["tag"] ?: message.messageId ?: System.currentTimeMillis().toString(),
                userId = c.auth.currentUid ?: "", title = n.title ?: "MED-K", body = n.body,
                conversationId = conv, orderId = message.data["order_id"], isRead = false, createdAt = "",
            )
        )
    }
}
