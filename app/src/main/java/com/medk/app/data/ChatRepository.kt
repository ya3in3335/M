package com.medk.app.data

import android.net.Uri
import android.provider.OpenableColumns
import com.medk.app.core.AppContainer
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID
import kotlin.time.Duration.Companion.hours

class ChatRepository(private val c: AppContainer) {
    private val db get() = c.supabase
    private val bucket = "chat-attachments"
    private val urlCache = mutableMapOf<String, String>()
    private val profileCache = mutableMapOf<String, Profile>()

    suspend fun conversations(): List<Conversation> =
        db.from("conversations").select { order("last_message_at", Order.DESCENDING) }.decodeList()

    suspend fun conversation(id: String): Conversation =
        db.from("conversations").select { filter { eq("id", id) } }.decodeSingle()

    suspend fun conversationForOrder(orderId: String): Conversation? =
        db.from("conversations").select { filter { eq("order_id", orderId) }; limit(1) }.decodeList<Conversation>().firstOrNull()

    suspend fun messages(conversationId: String): List<Message> =
        db.from("messages").select {
            filter { eq("conversation_id", conversationId) }
            order("created_at", Order.ASCENDING); limit(500)
        }.decodeList()

    /** Realtime stream of new messages in a conversation. */
    fun newMessages(conversationId: String): Flow<Message> = callbackFlow {
        val ch = db.channel("conv-$conversationId-${UUID.randomUUID()}")
        val flow = ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter("conversation_id", FilterOperator.EQ, conversationId)
        }
        val job = launch { flow.collect { a -> runCatching { a.decodeRecord<Message>() }.getOrNull()?.let { trySend(it) } } }
        ch.subscribe()
        awaitClose { job.cancel(); c.appScope.launch { runCatching { db.realtime.removeChannel(ch) } } }
    }

    suspend fun sendText(conversationId: String, text: String): Message {
        val uid = c.auth.currentUid ?: error("not signed in")
        return db.from("messages").insert(MessageInput(conversationId, uid, text.trim())) { select() }.decodeSingle()
    }

    /** Uploads an image/video/PDF to private storage then posts the message. */
    suspend fun sendAttachment(conversationId: String, uri: Uri, caption: String?): Message {
        val uid = c.auth.currentUid ?: error("not signed in")
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
                    val size = cur.getLong(1)
                    if (size > 50L * 1024 * 1024) error("حجم الملف يتجاوز 50 ميغابايت")
                    cur.getString(0)
                } else null
            }
        } ?: "file"
        val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } } ?: error("تعذر قراءة الملف")
        val ext = name.substringAfterLast('.', "").takeIf { it.length in 1..5 } ?: mime.substringAfter('/')
        val path = "$conversationId/${UUID.randomUUID()}.$ext"
        db.storage.from(bucket).upload(path, bytes) { upsert = false; contentType = ContentType.parse(mime) }
        return db.from("messages").insert(
            MessageInput(conversationId, uid, caption?.trim()?.takeIf { it.isNotEmpty() }, path, type, name)
        ) { select() }.decodeSingle()
    }

    /** Short-lived signed URL for a private attachment. */
    suspend fun attachmentUrl(path: String): String =
        urlCache[path] ?: db.storage.from(bucket).createSignedUrl(path, 2.hours).also { urlCache[path] = it }

    /** Opens/reuses a conversation with the seller of a product/section (or support when both null). */
    suspend fun start(productId: String?, sectionId: String?, subject: String?): String {
        val res = db.postgrest.rpc("start_conversation", buildJsonObject {
            put("p_product", productId); put("p_section", sectionId); put("p_subject", subject)
        })
        return c.json.parseToJsonElement(res.data).jsonPrimitive.content
    }

    suspend fun assignStaff(conversationId: String, staffId: String?) {
        db.from("conversations").update({ set("staff_id", staffId) }) { filter { eq("id", conversationId) } }
    }

    suspend fun profiles(ids: Collection<String>): Map<String, Profile> {
        val missing = ids.filter { it !in profileCache }
        if (missing.isNotEmpty()) runCatching {
            db.from("profiles").select { filter { isIn("id", missing) } }.decodeList<Profile>().forEach { profileCache[it.id] = it }
        }
        return ids.mapNotNull { id -> profileCache[id]?.let { id to it } }.toMap()
    }
}
