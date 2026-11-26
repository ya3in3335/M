package com.medk.app.ui.admin

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.GeoPoint
import com.medk.app.MedkApp
import com.medk.app.core.friendly
import com.medk.app.ui.components.EmptyBox
import com.medk.app.ui.components.LocalToast
import com.medk.app.ui.components.MedkCard
import com.medk.app.ui.components.MedkTextField
import com.medk.app.ui.components.MedkTopBar
import com.medk.app.ui.components.Pill
import com.medk.app.ui.components.SecondaryButton
import com.medk.app.ui.components.StateView
import com.medk.app.ui.components.loadVm
import com.medk.app.ui.theme.MedkColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Owner-only, read-only database explorer. Everything goes through the normal SDK, so the security rules
 * still apply: only the owner account can read these collections.
 */
object Db {
    const val ROOT = "admin/db"
    const val COLLECTION = "admin/db/c?path={path}"
    const val DOC = "admin/db/d?path={path}"
    fun collection(path: String) = "admin/db/c?path=${Uri.encode(path)}"
    fun doc(path: String) = "admin/db/d?path=${Uri.encode(path)}"

    val ROOTS = listOf(
        "users" to "المستخدمون", "publicProfiles" to "البطاقات العامة", "invites" to "دعوات البائعين",
        "orders" to "الطلبات", "conversations" to "المحادثات", "products" to "المنتجات", "categories" to "الأقسام الرئيسية",
        "sections" to "الأقسام الفرعية", "paymentMethods" to "طرق الدفع", "settlements" to "دفعات العمولة",
        "banners" to "البانرات", "p2pAssets" to "أسعار P2P", "media" to "الصور", "pushTokens" to "أجهزة الإشعارات", "config" to "الإعدادات",
    )
    /** Known sub-collections per parent collection. */
    val SUBS = mapOf("users" to listOf("favorites"), "conversations" to listOf("messages", "files"))
    val TITLE_FIELDS = listOf("reference", "fullName", "displayName", "name", "title", "email", "subject", "body", "status", "role", "type")
}

private fun short(v: Any?): String = when (v) {
    null -> "null"
    is String -> if (v.length > 300) v.take(120) + "… (${v.length} حرف)" else "\"$v\""
    is Timestamp -> v.toDate().toInstant().toString().replace("T", " ").take(19)
    is DocumentReference -> "→ ${v.path}"
    is GeoPoint -> "(${v.latitude}, ${v.longitude})"
    is com.google.firebase.firestore.Blob -> "<blob ${v.toBytes().size} B>"
    else -> v.toString()
}

private fun pretty(v: Any?, indent: String = ""): String = when (v) {
    is Map<*, *> -> if (v.isEmpty()) "{}" else v.entries.joinToString("\n", "{\n", "\n$indent}") { (k, x) -> "$indent  $k: ${pretty(x, "$indent  ")}" }
    is List<*> -> if (v.isEmpty()) "[]" else v.joinToString("\n", "[\n", "\n$indent]") { x -> "$indent  ${pretty(x, "$indent  ")}" }
    else -> short(v)
}

private fun summary(d: DocumentSnapshot): String =
    Db.TITLE_FIELDS.firstNotNullOfOrNull { f -> d.get(f)?.let { short(it).trim('"') } } ?: "${d.data?.size ?: 0} حقل"

// ============================================================================ collections
@Composable
fun DatabaseScreen(nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("db-root") {
        Db.ROOTS.map { (id, label) ->
            Triple(id, label, runCatching { c.firestore.collection(id).count().get(AggregateSource.SERVER).await().count }.getOrNull())
        }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("قاعدة البيانات", onBack = { nav.popBackStack() }, subtitle = "عرض فقط — للمالك وحده")
        StateView(state, { vm.load() }) { list ->
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.first }) { (id, label, count) ->
                    MedkCard(onClick = { nav.navigate(Db.collection(id)) }, padding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Folder, null, tint = MedkColors.Violet)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.titleSmall)
                                Text(id, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Pill(count?.toString() ?: "—", MedkColors.Info)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================ documents of a collection
@Composable
fun DatabaseCollectionScreen(path: String, nav: NavHostController) {
    val c = MedkApp.container
    val docs = remember(path) { mutableStateListOf<DocumentSnapshot>() }
    var loading by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    fun more() {
        if (loading || end) return
        loading = true
        scope.launch {
            runCatching {
                var q = c.firestore.collection(path).orderBy(FieldPath.documentId()).limit(40)
                docs.lastOrNull()?.let { q = q.startAfter(it) }
                q.get().await().documents
            }.onSuccess { docs.addAll(it); if (it.size < 40) end = true; error = null }
             .onFailure { error = it.friendly() }
            loading = false
        }
    }
    androidx.compose.runtime.LaunchedEffect(path) { if (docs.isEmpty()) more() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar(path.substringAfterLast('/'), onBack = { nav.popBackStack() }, subtitle = path)
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MedkTextField(search, { search = it.trim() }, "فتح مستند بالمعرّف (ID)", Modifier.weight(1f), leading = Icons.Filled.Search)
            IconButton({
                if (search.isBlank()) return@IconButton
                scope.launch {
                    val d = runCatching { c.firestore.collection(path).document(search).get().await() }.getOrNull()
                    if (d?.exists() == true) nav.navigate(Db.doc(d.reference.path)) else toast("لا يوجد مستند بهذا المعرّف")
                }
            }) { Icon(Icons.Filled.Search, "بحث") }
        }
        if (error != null && docs.isEmpty()) EmptyBox(Icons.Filled.Storage, "تعذر القراءة", error)
        else if (!loading && docs.isEmpty()) EmptyBox(Icons.Filled.Storage, "المجموعة فارغة")
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(docs, key = { it.id }) { d ->
                MedkCard(onClick = { nav.navigate(Db.doc(d.reference.path)) }, padding = PaddingValues(10.dp)) {
                    Column {
                        Text(summary(d), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(d.id, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            if (!end && docs.isNotEmpty()) item { SecondaryButton(if (loading) "جارٍ التحميل…" else "تحميل المزيد", { more() }, enabled = !loading, height = 40.dp) }
        }
    }
}

// ============================================================================ one document
@Composable
fun DatabaseDocScreen(path: String, nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("db-doc-$path") { c.firestore.document(path).get().await() }
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val toast = LocalToast.current
    val parent = path.split('/').let { it[it.size - 2] }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar(path.substringAfterLast('/'), onBack = { nav.popBackStack() }, subtitle = path, actions = {
            IconButton({ (state as? com.medk.app.core.UiState.Success)?.data?.let { d ->
                clipboard.setText(AnnotatedString(pretty(d.data ?: emptyMap<String, Any>()))); toast("تم النسخ")
            } }) { Icon(Icons.Filled.ContentCopy, "نسخ") }
        })
        StateView(state, { vm.load() }) { d ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!d.exists()) EmptyBox(Icons.Filled.Storage, "المستند غير موجود")
                Db.SUBS[parent]?.takeIf { path.split('/').size == 2 }?.let { subs ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        subs.forEach { s -> SecondaryButton("📁 $s", { nav.navigate(Db.collection("$path/$s")) }, Modifier.weight(1f), height = 38.dp) }
                    }
                }
                (d.data ?: emptyMap()).toSortedMap().forEach { (k, v) ->
                    MedkCard(padding = PaddingValues(10.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(k, style = MaterialTheme.typography.labelLarge, color = MedkColors.Violet, fontFamily = FontFamily.Monospace)
                            SelectionContainer {
                                Text(pretty(v), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                            // references to other documents are clickable
                            if (v is String && k.endsWith("Id") && v.isNotBlank()) {
                                val target = when (k) {
                                    "customerId", "staffId", "createdBy", "uid", "sellerId", "senderId", "lastSenderId", "acceptedBy", "assignedStaffId" -> "users/$v"
                                    "orderId", "lastOrderId" -> "orders/$v"; "productId" -> "products/$v"; "sectionId" -> "sections/$v"
                                    "categoryId" -> "categories/$v"; else -> null
                                }
                                if (target != null) Text("فتح ← $target", style = MaterialTheme.typography.labelSmall, color = MedkColors.Info,
                                    modifier = Modifier.padding(top = 4.dp).clickable { nav.navigate(Db.doc(target)) })
                            }
                        }
                    }
                }
            }
        }
    }
}
