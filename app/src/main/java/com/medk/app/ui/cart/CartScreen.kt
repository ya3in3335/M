package com.medk.app.ui.cart

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.medk.app.MedkApp
import com.medk.app.core.dzd
import com.medk.app.core.usd
import com.medk.app.data.CartLine
import com.medk.app.ui.components.EmptyBox
import com.medk.app.ui.components.LocalToast
import com.medk.app.ui.components.MedkCard
import com.medk.app.ui.components.MedkTextField
import com.medk.app.ui.components.MedkTopBar
import com.medk.app.ui.components.NetImage
import com.medk.app.ui.components.PriceBlock
import com.medk.app.ui.components.PrimaryButton
import com.medk.app.ui.components.QuantitySelector
import com.medk.app.ui.components.SecondaryButton
import com.medk.app.ui.components.actionVm
import com.medk.app.ui.navigation.Routes
import com.medk.app.ui.navigation.toTab
import com.medk.app.ui.theme.MedkColors
import kotlinx.coroutines.launch
import com.medk.app.ui.theme.MedkShapes

@Composable
fun CartScreen(nav: NavHostController, onMenu: () -> Unit) {
    val c = MedkApp.container
    val lines by c.cart.lines.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val action = actionVm("checkout")
    val busy by action.busy.collectAsStateWithLifecycle()
    var note by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var sellerGroups by remember { mutableStateOf(1) }
    LaunchedEffect(lines.map { it.sectionId }) {
        sellerGroups = runCatching {
            val sections = c.catalog.allSections(force = true).associateBy { it.id }
            lines.map { l -> l.sectionId?.let { sections[it]?.assignedStaffId } }.distinct().size
        }.getOrDefault(1)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("السلة", subtitle = if (lines.isNotEmpty()) "${lines.sumOf { it.quantity }} عناصر" else null,
            navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "القائمة") } },
            actions = { if (lines.isNotEmpty()) IconButton({ confirmClear = true }) { Icon(Icons.Filled.DeleteOutline, "إفراغ السلة") } })
        if (lines.isEmpty()) {
            EmptyBox(Icons.Filled.RemoveShoppingCart, "سلتك فارغة", "أضف منتجات من الأقسام لتظهر هنا",
                "متابعة التسوق", { nav.toTab(Routes.HOME) })
            return@Column
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lines, key = { it.productId }) { line ->
                CartRow(line, onOpen = { nav.navigate(Routes.product(line.productId)) },
                    onQty = { q -> scope.launch { c.cart.setQuantity(line.productId, q) } },
                    onRemove = { scope.launch { c.cart.remove(line.productId) } })
            }
            item {
                MedkTextField(note, { note = it.take(500) }, "ملاحظة للبائع (اختياري) — مثل معرّف اللاعب ID", singleLine = false, minLines = 2)
            }
            if (sellerGroups > 1) item {
                Row(Modifier.fillMaxWidth().clip(MedkShapes.Card).background(MedkColors.Info.copy(.12f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = MedkColors.Info)
                    Spacer(Modifier.width(8.dp))
                    Text("سلتك تحتوي على منتجات من $sellerGroups بائعين مختلفين، سيتم إنشاء $sellerGroups طلبات منفصلة مع محادثة لكل بائع.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        // summary
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp + com.medk.app.ui.components.LocalBottomBarInset.current), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الإجمالي", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(usd(lines.sumOf { it.priceUsd * it.quantity }), style = MaterialTheme.typography.titleLarge, color = MedkColors.Violet)
                    Text(dzd(lines.sumOf { it.priceDzd * it.quantity }), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PrimaryButton("تواصل مع البائع لإتمام الطلب", {
                action.run(toast) {
                    val created = c.orders.checkout(lines, note)
                    c.cart.removeAll(lines.map { it.productId })
                    note = ""
                    if (created.size == 1) {
                        toast("تم إنشاء الطلب ${created[0].reference}")
                        nav.navigate(Routes.chat(created[0].conversationId))
                    } else {
                        toast("تم إنشاء ${created.size} طلبات: ${created.joinToString("، ") { it.reference }}")
                        nav.toTab(Routes.CONVERSATIONS)
                    }
                }
            }, loading = busy, icon = Icons.Filled.SupportAgent)
            SecondaryButton("متابعة التسوق", { nav.toTab(Routes.HOME) }, height = 40.dp)
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("إفراغ السلة؟") }, text = { Text("سيتم حذف جميع المنتجات من السلة.") },
        confirmButton = { TextButton({ confirmClear = false; scope.launch { c.cart.clear() } }) { Text("إفراغ", color = MedkColors.Danger) } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("إلغاء") } })
}

@Composable
private fun CartRow(line: CartLine, onOpen: () -> Unit, onQty: (Int) -> Unit, onRemove: () -> Unit) {
    MedkCard(padding = PaddingValues(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(MedkShapes.ChamferSm).clickable(onClick = onOpen)) { NetImage(line.image, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(line.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (line.sectionName != null) Text(line.sectionName, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                PriceBlock(line.priceUsd * line.quantity, line.priceDzd * line.quantity)
            }
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onRemove, Modifier.size(28.dp)) { Icon(Icons.Filled.DeleteOutline, "حذف", tint = MedkColors.Danger, modifier = Modifier.size(17.dp)) }
                QuantitySelector(line.quantity, onQty, compact = true)
            }
        }
    }
}
