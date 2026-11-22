package com.medk.app.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.medk.app.MedkApp
import com.medk.app.core.UiState
import com.medk.app.core.dzd
import com.medk.app.core.formatDateTime
import com.medk.app.core.friendly
import com.medk.app.data.P2pAsset
import com.medk.app.ui.components.EmptyBox
import com.medk.app.ui.components.InfoRow
import com.medk.app.ui.components.LocalToast
import com.medk.app.ui.components.MedkCard
import com.medk.app.ui.components.MedkTextField
import com.medk.app.ui.components.MedkTopBar
import com.medk.app.ui.components.PrimaryButton
import com.medk.app.ui.components.CategoryArt
import com.medk.app.ui.components.StateView
import com.medk.app.ui.components.loadVm
import com.medk.app.ui.navigation.Routes
import com.medk.app.ui.theme.MedkColors
import com.medk.app.ui.theme.categoryPalette
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun P2pScreen(nav: NavHostController, categoryState: UiState<CategoryData>, retry: () -> Unit) {
    val c = MedkApp.container
    val assetsVm = loadVm("p2p-assets") { c.catalog.p2pAssets().filter { it.isActive } }
    val assets by assetsVm.state.collectAsStateWithLifecycle()
    val p = categoryPalette("p2p")
    var buy by rememberSaveable { mutableStateOf(true) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var amount by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("العملات الرقمية P2P", onBack = { nav.popBackStack() })
        StateView(categoryState, retry) { data ->
            StateView(assets, { assetsVm.load() }) { list ->
                val selected = list.firstOrNull { it.id == selectedId } ?: list.firstOrNull()
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.horizontal).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        CategoryArt("p2p", p, 70.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("شراء وبيع العملات الرقمية", color = Color.White, style = MaterialTheme.typography.titleLarge)
                            Text("تعامل مباشر وآمن مع فريق MED-K بالدينار الجزائري", color = Color.White.copy(.85f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    // buy / sell toggle
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(4.dp)) {
                        listOf(true to "شراء", false to "بيع").forEach { (b, label) ->
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(if (buy == b) (if (b) MedkColors.Success else MedkColors.Danger) else Color.Transparent)
                                .clickable { buy = b }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(label, color = if (buy == b) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                    if (list.isEmpty()) {
                        EmptyBox(Icons.Filled.CurrencyExchange, "الأسعار غير منشورة حالياً",
                            "تواصل مع البائع للحصول على السعر الحالي")
                    } else {
                        Text("العملات المدعومة وأسعار الصرف", style = MaterialTheme.typography.titleMedium)
                        list.forEach { a -> AssetRow(a, buy, a.id == selected?.id) { selectedId = a.id } }
                        if (selected != null) {
                            MedkCard {
                                Column {
                                    Text("حاسبة ${if (buy) "الشراء" else "البيع"} — ${selected.code}", style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(10.dp))
                                    MedkTextField(amount, { v -> amount = v.filter { it.isDigit() || it == '.' } }, "الكمية (${selected.code})",
                                        keyboardType = KeyboardType.Decimal)
                                    val qty = amount.toDoubleOrNull() ?: 0.0
                                    val rate = if (buy) selected.buyPriceDzd else selected.sellPriceDzd
                                    InfoRow("السعر", dzd(rate) + " / " + selected.code)
                                    InfoRow(if (buy) "المبلغ الذي ستدفعه" else "المبلغ الذي ستستلمه", dzd(qty * rate), valueColor = MedkColors.Violet)
                                    val outOfRange = qty > 0 && ((selected.minAmount != null && qty < selected.minAmount) ||
                                        (selected.maxAmount != null && qty > selected.maxAmount))
                                    if (outOfRange) Text("الكمية خارج الحدود المسموحة", color = MedkColors.Danger, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    MedkCard {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Shield, null, tint = MedkColors.Success); Spacer(Modifier.width(8.dp))
                                Text("معلومات الخدمة", style = MaterialTheme.typography.titleMedium)
                            }
                            Text("• يتم تنفيذ كل عملية عبر محادثة خاصة مع بائع معتمد.\n• لا تحوّل أي مبلغ قبل تأكيد البائع للتفاصيل داخل المحادثة.\n• تأكد من الشبكة (Network) وعنوان المحفظة قبل الإرسال.\n• الأسعار قابلة للتغير حسب السوق.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    PrimaryButton("تواصل مع البائع", {
                        val section = data.sections.firstOrNull()
                        val sel = selected
                        val subject = buildString {
                            append(if (buy) "شراء " else "بيع "); append(sel?.code ?: "عملات رقمية")
                            amount.toDoubleOrNull()?.let { append(" — الكمية: ").append(String.format(Locale.US, "%.2f", it)) }
                        }
                        scope.launch {
                            try { nav.navigate(Routes.chat(c.chat.start(null, section?.id, subject))) } catch (e: Exception) { toast(e.friendly()) }
                        }
                    }, icon = Icons.Filled.SupportAgent)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun AssetRow(a: P2pAsset, buy: Boolean, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
        .border(if (selected) 2.dp else 1.dp, if (selected) MedkColors.Success else MaterialTheme.colorScheme.outlineVariant, shape)
        .clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(categoryPalette("p2p").brush), contentAlignment = Alignment.Center) {
            Text(a.code.take(4), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, style = MaterialTheme.typography.titleSmall)
            Text(listOfNotNull(a.network, a.minAmount?.let { "حد أدنى ${it.toInt()}" }, a.maxAmount?.let { "حد أقصى ${it.toInt()}" })
                .joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (a.updatedAt != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(" آخر تحديث ${formatDateTime(a.updatedAt)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(dzd(if (buy) a.buyPriceDzd else a.sellPriceDzd), style = MaterialTheme.typography.titleMedium,
                color = if (buy) MedkColors.Success else MedkColors.Danger)
            Text(if (buy) "سعر الشراء" else "سعر البيع", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
