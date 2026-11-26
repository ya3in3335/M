package com.medk.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import android.util.LruCache
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FieldValue
import com.medk.app.core.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Free (Spark) plan has no Cloud Storage, so images and small PDFs are compressed
 * and stored inside Firestore documents (≤ ~650 KB each):
 *  - catalog images → media/{id}, referenced as "media:<id>"
 *  - chat files     → conversations/{cid}/files/{fid}, referenced as "conversations/<cid>/files/<fid>"
 */
class MediaRepository(private val c: AppContainer) {
    private val db get() = c.firestore
    private val cache = object : LruCache<String, ByteArray>(24 * 1024 * 1024) { override fun sizeOf(k: String, v: ByteArray) = v.size }
    private val mimes = mutableMapOf<String, String>()

    companion object {
        const val MAX_BYTES = 650_000
        fun chatRef(path: String) = "chatfile:" + path.removePrefix("conversations/").replace("/files/", "/")
    }

    /** Loads bytes for "media:<id>" or "chatfile:<cid>/<fid>". */
    suspend fun load(ref: String): Pair<ByteArray, String> {
        cache.get(ref)?.let { return it to (mimes[ref] ?: "image/jpeg") }
        val doc = when {
            ref.startsWith("media:") -> db.collection("media").document(ref.removePrefix("media:"))
            ref.startsWith("chatfile:") -> ref.removePrefix("chatfile:").split("/").let { (cid, fid) ->
                db.collection("conversations").document(cid).collection("files").document(fid)
            }
            else -> error("unknown media reference")
        }.get().await()
        val bytes = Base64.decode(doc.getString("data") ?: error("missing data"), Base64.DEFAULT)
        val mime = doc.getString("mime") ?: "image/jpeg"
        cache.put(ref, bytes); mimes[ref] = mime
        return bytes to mime
    }

    /** Compresses an image to JPEG (max side [maxSide]) until it fits the Firestore limit. */
    suspend fun compressImage(uri: Uri, maxSide: Int): ByteArray = withContext(Dispatchers.IO) {
        val ctx = c.context
        val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
                val s = info.size; val scale = maxOf(s.width, s.height).toFloat() / maxSide
                if (scale > 1f) dec.setTargetSize((s.width / scale).toInt(), (s.height / scale).toInt())
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val raw = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("تعذر قراءة الصورة")
            val scale = maxOf(raw.width, raw.height).toFloat() / maxSide
            if (scale > 1f) Bitmap.createScaledBitmap(raw, (raw.width / scale).toInt(), (raw.height / scale).toInt(), true) else raw
        }
        var quality = 85
        var out: ByteArray
        do {
            out = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            quality -= 10
        } while (out.size > MAX_BYTES && quality >= 35)
        if (out.size > MAX_BYTES) error("الصورة كبيرة جداً")
        out
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    /** Catalog image (product/section/banner) → "media:<id>". */
    suspend fun uploadCatalogImage(uri: Uri): String {
        val bytes = compressImage(uri, 1000)
        val ref = db.collection("media").document()
        ref.set(mapOf("data" to b64(bytes), "mime" to "image/jpeg", "createdBy" to c.auth.currentUid,
            "createdAt" to FieldValue.serverTimestamp())).await()
        cache.put("media:${ref.id}", bytes)
        return "media:${ref.id}"
    }

    data class ChatFile(val path: String, val type: String, val name: String)

    /** Prepares a chat attachment document (written in the same batch as its message). */
    suspend fun prepareChatFile(conversationId: String, uri: Uri): Pair<ChatFile, Map<String, Any?>> {
        val resolver = c.context.contentResolver
        val mime = resolver.getType(uri) ?: ""
        val name = withContext(Dispatchers.IO) {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        } ?: "file"
        val (bytes, type, storedMime) = when {
            mime.startsWith("image/") -> Triple(compressImage(uri, 1280), "image", "image/jpeg")
            mime == "application/pdf" -> {
                val b = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } } ?: error("تعذر قراءة الملف")
                if (b.size > MAX_BYTES) error("حجم ملف PDF يتجاوز 650 كيلوبايت (حد الخطة المجانية)")
                Triple(b, "pdf", "application/pdf")
            }
            mime.startsWith("video/") -> error("إرسال الفيديو غير متاح في الخطة المجانية. أرسل صورة أو PDF.")
            else -> error("نوع الملف غير مدعوم")
        }
        val fid = db.collection("conversations").document(conversationId).collection("files").document().id
        val path = "conversations/$conversationId/files/$fid"
        cache.put(chatRef(path), bytes); mimes[chatRef(path)] = storedMime
        return ChatFile(path, type, name.take(200)) to mapOf(
            "data" to b64(bytes), "mime" to storedMime, "name" to name.take(200),
            "createdBy" to c.auth.currentUid, "createdAt" to FieldValue.serverTimestamp(),
        )
    }

    /** Image → "chatfile:" reference for the image loader; PDF → a local content:// URI to open. */
    suspend fun chatFileUri(path: String): String {
        val ref = chatRef(path)
        val (bytes, mime) = load(ref)
        if (mime.startsWith("image/")) return ref
        val dir = File(c.context.cacheDir, "chat").apply { mkdirs() }
        val f = File(dir, path.substringAfterLast('/') + ".pdf")
        if (!f.exists()) withContext(Dispatchers.IO) { f.writeBytes(bytes) }
        return FileProvider.getUriForFile(c.context, "${c.context.packageName}.updates", f).toString()
    }
}

/** Coil fetcher so that NetImage/AsyncImage can display "media:" and "chatfile:" references. */
class MedkMediaFetcher(private val ref: String, private val options: coil3.request.Options) : coil3.fetch.Fetcher {
    override suspend fun fetch(): coil3.fetch.FetchResult {
        val (bytes, mime) = com.medk.app.MedkApp.container.media.load(ref)
        return coil3.fetch.SourceFetchResult(
            source = coil3.decode.ImageSource(okio.Buffer().write(bytes), options.fileSystem),
            mimeType = mime, dataSource = coil3.decode.DataSource.NETWORK,
        )
    }

    class Factory : coil3.fetch.Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: coil3.request.Options, imageLoader: coil3.ImageLoader): coil3.fetch.Fetcher? =
            if (data.scheme == "media" || data.scheme == "chatfile") MedkMediaFetcher(data.toString(), options) else null
    }
}

