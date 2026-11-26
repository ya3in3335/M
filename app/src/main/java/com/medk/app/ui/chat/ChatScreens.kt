package com.medk.app.ui.chat

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Close
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.medk.app.MedkApp
import com.medk.app.core.UiState
import com.medk.app.core.formatShort
import com.medk.app.core.formatTime
import com.medk.app.core.friendly
import com.medk.app.data.Conversation
import com.medk.app.data.Message
import com.medk.app.data.Order
import com.medk.app.data.Profile
import com.medk.app.data.Role
import com.medk.app.ui.components.Avatar
import com.medk.app.ui.components.EmptyBox
import com.medk.app.ui.components.ErrorBox
import com.medk.app.ui.components.LoadingBox
import com.medk.app.ui.components.LocalToast
import com.medk.app.ui.components.MedkCard
import com.medk.app.ui.components.MedkTopBar
import com.medk.app.ui.components.PriceBlock
import com.medk.app.ui.components.StateView
import com.medk.app.ui.components.StatusChip
import com.medk.app.ui.components.loadVm
import com.medk.app.ui.navigation.Routes
import com.medk.app.ui.openSupport
import com.medk.app.ui.theme.MedkColors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.medk.app.ui.theme.MedkShapes

// ========================================================= conversation list
data class ConvItem(val conv: Conversation, val other: Profile?, val order: Order?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(nav: NavHostController, onMenu: (() -> Unit)?, showBack: Boolean = onMenu == null) {
    val c = MedkApp.container
    val me = c.auth.profile
    val vm = loadVm("conversations") {
        coroutineScope {
            val convs = c.chat.conversations()
            val ordersD = async { runCatching { c.orders.visibleOrders() }.getOrDefault(emptyList()).associateBy { it.id } }
            val ids = convs.flatMap { listOfNotNull(it.customerId, it.staffId) }.filter { it != me?.id }.toSet()
            val profiles = c.chat.profiles(ids)
            val orders = ordersD.await()
            convs.map { cv ->
                val otherId = if (cv.customerId == me?.id) cv.staffId else cv.customerId
                ConvItem(cv, otherId?.let { profiles[it] }, cv.orderId?.let { orders[it] })
            }
        }
    }
    LifecycleResumeEffect(Unit) { vm.load(silent = true); onPauseOrDispose { } }
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar(if (com.medk.app.BuildConfig.ADMIN_APP) "المحادثات" else "محادثاتي", onBack = if (showBack) ({ nav.popBackStack() }) else null,
            navigationIcon = onMenu?.let { { IconButton(onClick = it) { Icon(Icons.Filled.Menu, "القائمة") } } },
            actions = { if (!com.medk.app.BuildConfig.ADMIN_APP) IconButton({ openSupport(nav, scope, toast) }) { Icon(Icons.Filled.SupportAgent, "الدعم الفني") } })
        PullToRefreshBox(refreshing, { vm.load(silent = true) }) {
            StateView(state, { vm.load() }) { list ->
                if (list.isEmpty()) EmptyBox(Icons.Filled.Forum, "لا توجد محادثات بعد",
                    "عند إتمام طلب أو التواصل مع بائع ستظهر المحادثة هنا", "تواصل مع الدعم", { openSupport(nav, scope, toast) })
                else LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp + com.medk.app.ui.components.LocalBottomBarInset.current), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.conv.id }) { item -> ConversationRow(item, me) { nav.navigate(Routes.chat(item.conv.id)) } }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(item: ConvItem, me: Profile?, onClick: () -> Unit) {
    val isTeam = me?.roleEnum != Role.CUSTOMER
    val title = item.conv.subject ?: "محادثة"
    val who = when {
        item.other != null -> item.other.displayName + if (!isTeam) " • البائع" else ""
        item.conv.staffId == null -> if (isTeam) "غير مُسندة" else "فريق MED-K"
        else -> "فريق MED-K"
    }
    MedkCard(onClick = onClick, padding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(item.other?.displayName ?: "M", item.other?.avatarUrl, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(formatShort(item.conv.lastMessageAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(who, style = MaterialTheme.typography.labelMedium, color = MedkColors.Violet, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.conv.lastMessage ?: "لا توجد رسائل بعد", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    item.order?.let { StatusChip(it.statusEnum) }
                }
            }
        }
    }
}

// ================================================================ chat VM
class ChatViewModel(private val conversationId: String) : ViewModel() {
    private val c = MedkApp.container
    private val _messages = MutableStateFlow<UiState<List<Message>>>(UiState.Loading)
    val messages = _messages.asStateFlow()
    private val _header = MutableStateFlow<ConvItem?>(null)
    val header = _header.asStateFlow()
    private val _sending = MutableStateFlow(false)
    val sending = _sending.asStateFlow()
    private val _uploading = MutableStateFlow(false)
    val uploading = _uploading.asStateFlow()
    var onError: (String) -> Unit = {}

    init {
        load()
        viewModelScope.launch {
            runCatching { c.chat.newMessages(conversationId).collect { add(it) } }
        }
    }

    fun load() = viewModelScope.launch {
        try {
            val conv = c.chat.conversation(conversationId)
            val me = c.auth.currentUid
            val otherId = if (conv.customerId == me) conv.staffId else conv.customerId
            val other = otherId?.let { c.chat.profiles(listOf(it))[it] }
            val order = conv.orderId?.let { runCatching { c.orders.order(it) }.getOrNull() }
            _header.value = ConvItem(conv, other, order)
            _messages.value = UiState.Success(c.chat.messages(conversationId))
        } catch (e: Exception) { _messages.value = UiState.Error(e.friendly()) }
    }

    private fun add(m: Message) {
        val cur = (_messages.value as? UiState.Success)?.data ?: return
        if (cur.none { it.id == m.id }) _messages.value = UiState.Success(cur + m)
    }

    fun send(text: String, done: () -> Unit) {
        if (text.isBlank() || _sending.value) return
        viewModelScope.launch {
            _sending.value = true
            try { add(c.chat.sendText(conversationId, text)); done() } catch (e: Exception) { onError(e.friendly()) }
            _sending.value = false
        }
    }

    fun sendFile(uri: Uri) = viewModelScope.launch {
        _uploading.value = true
        try { add(c.chat.sendAttachment(conversationId, uri, null)) } catch (e: Exception) { onError(e.friendly()) }
        _uploading.value = false
    }

    fun refreshOrder() = viewModelScope.launch {
        val h = _header.value ?: return@launch
        val id = h.conv.orderId ?: return@launch
        runCatching { c.orders.order(id) }.onSuccess { _header.value = h.copy(order = it) }
    }
}

// =================================================================== chat
@Composable
fun ChatScreen(id: String, nav: NavHostController) {
    val c = MedkApp.container
    val vm: ChatViewModel = viewModel(key = "chat-$id") { ChatViewModel(id) }
    val toast = LocalToast.current
    vm.onError = toast
    val state by vm.messages.collectAsStateWithLifecycle()
    val header by vm.header.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val uploading by vm.uploading.collectAsStateWithLifecycle()
    val me = c.auth.currentUid
    val isTeam = c.auth.profile?.roleEnum != Role.CUSTOMER
    var text by remember { mutableStateOf("") }
    var attachMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    DisposableEffect(id) {
        c.notifications.activeConversationId = id
        onDispose { if (c.notifications.activeConversationId == id) c.notifications.activeConversationId = null }
    }
    LifecycleResumeEffect(Unit) { vm.refreshOrder(); onPauseOrDispose { } }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::sendFile) }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(vm::sendFile) }

    val count = (state as? UiState.Success)?.data?.size ?: 0
    LaunchedEffect(count) { if (count > 0) listState.animateScrollToItem(count - 1) }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        val h = header
        MedkTopBar(h?.conv?.subject ?: "المحادثة", onBack = { nav.popBackStack() },
            subtitle = when {
                h == null -> null
                h.other != null -> h.other.displayName + if (!isTeam) " — البائع" else ""
                h.conv.staffId == null -> "بانتظار إسناد بائع — سيصلك الرد قريباً"
                else -> "فريق MED-K"
            })
        if (h?.order != null) OrderStrip(h.order, isTeam) { nav.navigate(Routes.order(h.order.id)) }
        if (uploading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MedkColors.Violet)
        Box(Modifier.weight(1f)) {
            when (val s = state) {
                is UiState.Loading -> LoadingBox()
                is UiState.Error -> ErrorBox(s.message, { vm.load() })
                is UiState.Success -> if (s.data.isEmpty()) EmptyBox(Icons.Filled.Forum, "ابدأ المحادثة", "اكتب رسالتك وسيرد عليك فريق MED-K")
                else LazyColumn(state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(s.data, key = { it.id }) { m -> Bubble(m, mine = m.senderId == me, nav) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).navigationBarsPadding()
            .padding(8.dp), verticalAlignment = Alignment.Bottom) {
            Box {
                IconButton({ attachMenu = true }, enabled = !uploading) { Icon(Icons.Filled.AttachFile, "إرفاق ملف") }
                DropdownMenu(attachMenu, { attachMenu = false }) {
                    DropdownMenuItem({ Text("صورة") }, leadingIcon = { Icon(Icons.Filled.Image, null) }, onClick = {
                        attachMenu = false; pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                    DropdownMenuItem({ Text("ملف PDF (حتى 650 كيلوبايت)") }, leadingIcon = { Icon(Icons.Filled.PictureAsPdf, null) }, onClick = {
                        attachMenu = false; pickPdf.launch("application/pdf") })
                }
            }
            OutlinedTextField(text, { text = it }, Modifier.weight(1f).heightIn(max = 120.dp), placeholder = { Text("اكتب رسالتك…", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodyMedium, shape = MedkShapes.ChamferSm, colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MedkColors.Violet, unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(44.dp).clip(MedkShapes.Emblem).background(MedkColors.PrimaryGradient)
                .clickable(enabled = text.isNotBlank() && !sending) { vm.send(text) { text = "" } }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.Send, "إرسال", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun OrderStrip(order: Order, isTeam: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(MedkShapes.Card)
        .background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = MedkColors.Violet)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("الطلب ${order.reference}", style = MaterialTheme.typography.titleSmall)
            Text(order.items.joinToString("، ") { "${it.productName} ×${it.quantity}" }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            StatusChip(order.statusEnum)
            if (isTeam) Text("إدارة ›", style = MaterialTheme.typography.labelSmall, color = MedkColors.Violet)
            else PriceBlock(order.totalUsd, order.totalDzd)
        }
    }
}

@Composable
private fun Bubble(m: Message, mine: Boolean, nav: NavHostController) {
    val c = MedkApp.container
    val ctx = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var url by remember(m.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(m.attachmentPath) { m.attachmentPath?.let { url = runCatching { c.chat.attachmentUrl(it) }.getOrNull() } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) {
        Column(Modifier.widthIn(max = 280.dp).clip(androidx.compose.foundation.shape.CutCornerShape(
            topStart = if (mine) 0.dp else 8.dp, topEnd = if (mine) 8.dp else 0.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
            .background(if (mine) MedkColors.PrimaryGradient else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh))
            .padding(horizontal = 10.dp, vertical = 7.dp)) {
            when (m.attachmentType) {
                "image" -> Box(Modifier.width(220.dp).height(180.dp).clip(MedkShapes.Small).background(Color.Black.copy(.2f))
                    .clickable(enabled = url != null) { url?.let { nav.navigate(Routes.media(it, "image")) } }) {
                    if (url != null) AsyncImage(url, m.attachmentName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                "video" -> Row(Modifier.clip(MedkShapes.Small).background(Color.Black.copy(.25f))
                    .clickable(enabled = url != null) { url?.let { nav.navigate(Routes.media(it, "video")) } }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayCircle, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(8.dp)); Text(m.attachmentName ?: "فيديو", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                "pdf" -> Row(Modifier.clip(MedkShapes.Small).background(Color.Black.copy(.25f)).clickable(enabled = url != null) {
                    scope.launch {
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        catch (_: ActivityNotFoundException) { toast("لا يوجد تطبيق لفتح ملفات PDF") }
                    }
                }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PictureAsPdf, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(8.dp)); Text(m.attachmentName ?: "ملف PDF", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!m.body.isNullOrBlank()) Text(m.body, color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = if (m.attachmentType != null) 6.dp else 0.dp))
            Text(formatTime(m.createdAt), style = MaterialTheme.typography.labelSmall,
                color = if (mine) Color.White.copy(.7f) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
        }
    }
}

// ============================================================= media viewer
@Composable
fun MediaScreen(url: String, type: String, nav: NavHostController) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        if (type == "video") {
            val player = remember { ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true } }
            DisposableEffect(Unit) { onDispose { player.release() } }
            AndroidView({ PlayerView(it).apply { this.player = player } }, Modifier.fillMaxSize())
        } else {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            AsyncImage(url, null, Modifier.fillMaxSize()
                .pointerInputTransform { z, pan -> scale = (scale * z).coerceIn(1f, 5f); offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan }
                .graphicsLayerScale(scale, offset), contentScale = ContentScale.Fit)
        }
        MedkTopBarOverlay { nav.popBackStack() }
    }
}

@Composable
private fun MedkTopBarOverlay(onBack: () -> Unit) {
    IconButton(onBack, Modifier.padding(8.dp).clip(CircleShape).background(Color.Black.copy(.5f))) {
        Icon(Icons.Filled.Close, "إغلاق", tint = Color.White)
    }
}

private fun Modifier.pointerInputTransform(onTransform: (Float, androidx.compose.ui.geometry.Offset) -> Unit) =
    this.then(Modifier.pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, _ -> onTransform(zoom, pan) }
    })

private fun Modifier.graphicsLayerScale(scale: Float, offset: androidx.compose.ui.geometry.Offset) =
    this.then(Modifier.graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y })
