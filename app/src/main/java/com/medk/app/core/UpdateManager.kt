package com.medk.app.core

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.medk.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class AppUpdate(val versionCode: Int, val versionName: String, val url: String, val notes: String? = null, val required: Boolean = false)

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val percent: Int) : DownloadState
    data class Ready(val file: File) : DownloadState
    data class Failed(val message: String) : DownloadState
}

private val Context.updateStore by preferencesDataStore("medk_updates")

/**
 * Daily update check against the latest GitHub Release of the app repository.
 * Release tag "vX.Y.N" → versionCode N; asset name = BuildConfig.UPDATE_ASSET.
 * A release whose notes contain "[required]" forces the update.
 */
class UpdateManager(private val context: Context, private val json: Json, private val scope: CoroutineScope) {
    private val lastCheckKey = longPreferencesKey("last_check")
    private val pendingKey = stringPreferencesKey("pending")
    private val mutex = Mutex()

    private val _update = MutableStateFlow<AppUpdate?>(null)
    val update: StateFlow<AppUpdate?> = _update.asStateFlow()
    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download.asStateFlow()
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /** Checks at most once every 30 min (each app start) unless [force]; re-exposes a previously found update. */
    fun checkIfDue(force: Boolean = false) {
        if (!AppFlags.updateChecks) return
        scope.launch {
            mutex.withLock {
                val prefs = context.updateStore.data.first()
                prefs[pendingKey]?.let { s ->
                    runCatching { json.decodeFromString<AppUpdate>(s) }.getOrNull()
                        ?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }?.let { _update.value = it }
                }
                val last = prefs[lastCheckKey] ?: 0L
                if (!force && System.currentTimeMillis() - last < 30L * 60 * 1000) return@withLock
                _checking.value = true
                val found = runCatching { fetchLatest() }.getOrNull()
                _checking.value = false
                if (found != null || !force) context.updateStore.edit { p ->
                    p[lastCheckKey] = System.currentTimeMillis()
                    if (found != null) p[pendingKey] = json.encodeToString(AppUpdate.serializer(), found) else p.remove(pendingKey)
                }
                _update.value = found
            }
        }
    }

    private suspend fun fetchLatest(): AppUpdate? = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "MED-K-Android")
        conn.connectTimeout = 10_000; conn.readTimeout = 15_000
        try {
            if (conn.responseCode != 200) return@withContext null
            val obj = json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
            val tag = obj["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@withContext null
            val code = Regex("(\\d+)$").find(tag)?.value?.toIntOrNull() ?: return@withContext null
            if (code <= BuildConfig.VERSION_CODE) return@withContext null
            val asset = obj["assets"]?.jsonArray?.map { it.jsonObject }
                ?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == BuildConfig.UPDATE_ASSET } ?: return@withContext null
            val notes = obj["body"]?.jsonPrimitive?.contentOrNull
            AppUpdate(code, tag.removePrefix("v"), asset["browser_download_url"]!!.jsonPrimitive.content,
                notes?.replace("[required]", "")?.trim()?.take(600), notes?.contains("[required]") == true)
        } finally { conn.disconnect() }
    }

    /** Downloads the APK with DownloadManager and reports progress. */
    fun startDownload() {
        val u = _update.value ?: return
        if (_download.value is DownloadState.Downloading) return
        scope.launch {
            try {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("storage unavailable")
                val file = File(dir, "medk-update-${u.versionCode}.apk")
                if (file.exists()) file.delete()
                val dm = context.getSystemService(DownloadManager::class.java)
                val id = dm.enqueue(DownloadManager.Request(Uri.parse(u.url))
                    .setTitle("تحديث MED-K ${u.versionName}")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationUri(Uri.fromFile(file)))
                _download.value = DownloadState.Downloading(0)
                while (true) {
                    delay(500)
                    val st = dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                        if (!c.moveToFirst()) null else Triple(
                            c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                            c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                            c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))
                    } ?: error("download cancelled")
                    when (st.first) {
                        DownloadManager.STATUS_SUCCESSFUL -> { _download.value = DownloadState.Ready(file); install(file); return@launch }
                        DownloadManager.STATUS_FAILED -> error("download failed")
                        else -> if (st.third > 0) _download.value = DownloadState.Downloading((st.second * 100 / st.third).toInt())
                    }
                }
            } catch (e: Exception) {
                _download.value = DownloadState.Failed("تعذر تنزيل التحديث. تحقق من الاتصال وأعد المحاولة.")
            }
        }
    }

    /** Opens the system installer (asks once for "install unknown apps" permission). */
    fun install(file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
