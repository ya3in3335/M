package com.medk.app.data

import android.net.Uri
import android.provider.OpenableColumns
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.storage.StorageMetadata
import com.medk.app.core.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/** Conversations (read-only for clients) + messages subcollection + private Storage attachments. */
open class ChatRepository(private val c: AppContainer) {
    private val db get() = c.firestore
    private val urlCache = mutableMapOf<String, String>()
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

    private suspend fun post(conversationId: String, body: String?, attachment: Map<String, String?>?): Message {
        val uid = c.auth.currentUid ?: error("not signed in")
        val ref = msgs(conversationId).document()
        ref.set(mapOf("senderId" to uid, "body" to body, "attachment" to attachment, "createdAt" to FieldValue.serverTimestamp())).await()
        return Message(ref.id, conversationId, uid, body, attachment?.get("path"), attachment?.get("type"), attachment?.get("name"),
            java.time.OffsetDateTime.now().toString())
    }

    open suspend fun sendText(conversationId: String, text: String): Message = post(conversationId, text.trim(), null)

    /** Uploads an image/video/PDF to private storage (chat/{conversation}/...) then posts the message. */
    open suspend fun sendAttachment(conversationId: String, uri: Uri, caption: String?): Message {
        val resolver = c.context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val type = when {
            mime.startsWith("image/") -> "image"
            mime.startsWith("video/") -> "video"
            mime == "application/pdf" -> "pdf"
            else -> error("نوع الملف غير مدعوم")
        }
        val name = withContext(Dispatchers.IO) {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) {
                    if (cur.getLong(1) > 50L * 1024 * 1024) error("حجم الملف يتجاوز 50 ميغابايت")
                    cur.getString(0)
                } else null
            }
        } ?: "file"
        val ext = name.substringAfterLast('.', "").filter { it.isLetterOrDigit() }.take(5).ifEmpty { mime.substringAfter('/').take(5) }
        val path = "chat/$conversationId/${UUID.randomUUID()}.$ext"
        c.storage.reference.child(path).putFile(uri, StorageMetadata.Builder().setContentType(mime).build()).await()
        return post(conversationId, caption?.trim()?.takeIf { it.isNotEmpty() }, mapOf("path" to path, "type" to type, "name" to name.take(200)))
    }

    /** Download URL for a private attachment (only participants are allowed to obtain it). */
    open suspend fun attachmentUrl(path: String): String =
        urlCache[path] ?: c.storage.reference.child(path).downloadUrl.await().toString().also { urlCache[path] = it }

    /** Opens/reuses a conversation with the seller of a product/section (or support when both null). */
    open suspend fun start(productId: String?, sectionId: String?, subject: String?): String =
        c.cloud.call("startConversation", mapOf("product_id" to productId, "section_id" to sectionId, "subject" to subject))["conversation_id"] as String

    open suspend fun assignStaff(conversationId: String, staffId: String?) {
        val conv = conversation(conversationId)
        conv.orderId?.let { c.orders.assignStaff(it, staffId) } ?: error("لا يمكن إسناد محادثة بدون طلب")
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
