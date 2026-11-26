package com.medk.app.data

import android.net.Uri
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.medk.app.core.AppContainer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Conversations + messages + attachments, all in Firestore (free plan). */
open class ChatRepository(private val c: AppContainer) {
    private val db get() = c.firestore
    private val profileCache = mutableMapOf<String, Profile>()

    private fun convs() = db.collection("conversations")
    private fun msgs(id: String) = convs().document(id).collection("messages")

    open suspend fun conversations(): List<Conversation> {
        val uid = c.auth.currentUid ?: return emptyList()
        val q = when (c.auth.profile?.roleEnum ?: Role.CUSTOMER) {
            Role.CUSTOMER -> convs().whereEqualTo("customerId", uid)
            Role.STAFF -> convs().whereEqualTo("staffId", uid)
            Role.OWNER -> convs()
        }
        return q.orderBy("lastMessageAt", Query.Direction.DESCENDING).limit(200).get().await().documents.map { it.toConversation() }
    }

    open suspend fun conversation(id: String): Conversation = convs().document(id).get().await().toConversation()

    open suspend fun conversationForOrder(orderId: String): Conversation? {
        val uid = c.auth.currentUid ?: return null
        val q = when (c.auth.profile?.roleEnum ?: Role.CUSTOMER) {
            Role.CUSTOMER -> convs().whereEqualTo("orderId", orderId).whereEqualTo("customerId", uid)
            Role.STAFF -> convs().whereEqualTo("orderId", orderId).whereEqualTo("staffId", uid)
            Role.OWNER -> convs().whereEqualTo("orderId", orderId)
        }
        return q.limit(1).get().await().documents.firstOrNull()?.toConversation()
    }

    open suspend fun messages(conversationId: String): List<Message> =
        msgs(conversationId).orderBy("createdAt").limit(500).get().await().documents.map { it.toMessage(conversationId) }

    /** Realtime stream of messages added after subscription. */
    open fun newMessages(conversationId: String): Flow<Message> = callbackFlow {
        var first = true
        val reg = msgs(conversationId).orderBy("createdAt", Query.Direction.DESCENDING).limit(20)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                if (first) { first = false; return@addSnapshotListener }
                snap.documentChanges.filter { it.type == DocumentChange.Type.ADDED }
                    .forEach { trySend(it.document.toMessage(conversationId)) }
            }
        awaitClose { reg.remove() }
    }

    /** Message + conversation "last message" update (+ optional file document) in one atomic batch. */
    private suspend fun post(conversationId: String, body: String?, file: Pair<MediaRepository.ChatFile, Map<String, Any?>>?): Message {
        val uid = c.auth.currentUid ?: error("not signed in")
        val batch = db.batch()
        file?.let { (f, data) -> batch.set(db.document(f.path), data) }
        val ref = msgs(conversationId).document()
        val attachment = file?.first?.let { mapOf("path" to it.path, "type" to it.type, "name" to it.name) }
        batch.set(ref, mapOf("senderId" to uid, "body" to body, "attachment" to attachment, "createdAt" to FieldValue.serverTimestamp()))
        val preview = body?.takeIf { it.isNotBlank() } ?: if (file?.first?.type == "image") "📷 صورة" else "📄 ملف PDF"
        batch.update(convs().document(conversationId), mapOf("lastMessage" to preview.take(200),
            "lastMessageAt" to FieldValue.serverTimestamp(), "lastSenderId" to uid))
        batch.commit().await()
        c.push.notify("message", "conversationId" to conversationId, "messageId" to ref.id)
        return Message(ref.id, conversationId, uid, body, attachment?.get("path"), attachment?.get("type"), attachment?.get("name"),
            java.time.OffsetDateTime.now().toString())
    }

    open suspend fun sendText(conversationId: String, text: String): Message = post(conversationId, text.trim(), null)

    /** Images (compressed) and small PDFs are stored in Firestore; video is not available on the free plan. */
    open suspend fun sendAttachment(conversationId: String, uri: Uri, caption: String?): Message =
        post(conversationId, caption?.trim()?.takeIf { it.isNotEmpty() }, c.media.prepareChatFile(conversationId, uri))

    /** "chatfile:" reference for images, local content:// URI for PDFs. */
    open suspend fun attachmentUrl(path: String): String = c.media.chatFileUri(path)

    /** Opens/reuses a conversation with the seller of a product/section (or support when both null). */
    open suspend fun start(productId: String?, sectionId: String?, subject: String?): String {
        val uid = c.auth.currentUid ?: error("not signed in")
        val sec = productId?.let { db.collection("products").document(it).get().await().getString("sectionId") } ?: sectionId
        val staffId = sec?.let { db.collection("sections").document(it).get().await().getString("assignedStaffId") }
        convs().whereEqualTo("customerId", uid).whereEqualTo("orderId", null)
            .whereEqualTo("productId", productId).whereEqualTo("sectionId", sec).limit(1).get().await()
            .documents.firstOrNull()?.let { return it.id }
        val ref = convs().document()
        ref.set(mapOf("customerId" to uid, "staffId" to staffId, "orderId" to null, "productId" to productId, "sectionId" to sec,
            "subject" to (subject ?: "الدعم الفني").take(140), "lastMessage" to null,
            "lastMessageAt" to FieldValue.serverTimestamp(), "lastSenderId" to uid, "createdAt" to FieldValue.serverTimestamp())).await()
        return ref.id
    }

    open suspend fun assignStaff(conversationId: String, staffId: String?) {
        val conv = conversation(conversationId)
        conv.orderId?.let { c.orders.assignStaff(it, staffId) } ?: convs().document(conversationId).update("staffId", staffId).await()
    }

    /** Public cards (name/avatar/role) of other participants. */
    open suspend fun profiles(ids: Collection<String>): Map<String, Profile> {
        val missing = ids.filter { it !in profileCache }.distinct()
        missing.chunked(10).forEach { chunk ->
            runCatching {
                db.collection("publicProfiles").whereIn(FieldPath.documentId(), chunk).get().await().documents
                    .forEach { profileCache[it.id] = it.toPublicProfile() }
            }
        }
        return ids.mapNotNull { id -> profileCache[id]?.let { id to it } }.toMap()
    }
}
