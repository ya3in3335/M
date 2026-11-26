package com.medk.app.ui.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.medk.app.MedkApp
import com.medk.app.core.dzd
import com.medk.app.core.formatDateTime
import com.medk.app.core.usd
import com.medk.app.data.CommissionSummary
import com.medk.app.data.PaymentMethod
import com.medk.app.data.Profile
import com.medk.app.data.Settlement
import com.medk.app.ui.components.EmptyBox
import com.medk.app.ui.components.InfoRow
import com.medk.app.ui.components.LocalToast
import com.medk.app.ui.components.MedkCard
import com.medk.app.ui.components.MedkTextField
import com.medk.app.ui.components.MedkTopBar
import com.medk.app.ui.components.PrimaryButton
import com.medk.app.ui.components.SecondaryButton
import com.medk.app.ui.components.StateView
import com.medk.app.ui.components.actionVm
import com.medk.app.ui.components.loadVm
import com.medk.app.ui.theme.MedkColors
import com.medk.app.ui.theme.MedkShapes

private fun f2(x: Double) = "%.2f".format(java.util.Locale.US, x)

// ============================================================================ payment methods editor
@Composable
private fun MethodsEditor(list: SnapshotStateList<PaymentMethod>) {
    var editing by remember { mutableStateOf<Int?>(null) }   // index, -1 = new
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (list.isEmpty()) Text("لا توجد طرق دفع. أضف حساب بريدي موب أو CCP أو غيره ليراه العملاء في طلباتهم.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.forEachIndexed { i, m ->
            Surface(shape = MedkShapes.Small, color = MaterialTheme.colorScheme.surfaceContainerHigh, onClick = { editing = i }) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.title, style = MaterialTheme.typography.titleSmall, color = MedkColors.Violet)
                        Text(m.account, style = MaterialTheme.typography.bodySmall)
                        m.holder?.let { Text("باسم: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    IconButton({ list.removeAt(i) }) { Icon(Icons.Filled.Delete, "حذف", tint = MedkColors.Danger) }
                }
            }
        }
        if (list.size < 8) SecondaryButton("إضافة طريقة دفع", { editing = -1 }, icon = Icons.Filled.Add, height = 40.dp)
    }
    editing?.let { idx ->
        val init = list.getOrNull(idx)
        var type by remember(idx) { mutableStateOf(init?.type ?: "baridimob") }
        var title by remember(idx) { mutableStateOf(init?.title ?: PaymentMethod.typeLabel("baridimob")) }
        var account by remember(idx) { mutableStateOf(init?.account ?: "") }
        var holder by remember(idx) { mutableStateOf(init?.holder ?: "") }
        var note by remember(idx) { mutableStateOf(init?.note ?: "") }
        var menu by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (init == null) "طريقة دفع جديدة" else "تعديل طريقة الدفع") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        SecondaryButton("النوع: ${PaymentMethod.typeLabel(type)}", { menu = true }, height = 40.dp)
                        DropdownMenu(menu, { menu = false }) {
                            PaymentMethod.TYPES.forEach { (k, label) ->
                                DropdownMenuItem({ Text(label) }, onClick = {
                                    if (title.isBlank() || PaymentMethod.TYPES.any { it.second == title }) title = label
                                    type = k; menu = false
                                })
                            }
                        }
                    }
                    MedkTextField(title, { title = it.take(40) }, "الاسم الظاهر للعميل")
                    MedkTextField(account, { account = it.take(120) }, when (type) {
                        "baridimob" -> "رقم RIP (20 رقماً)"; "ccp" -> "رقم الحساب CCP + المفتاح"
                        "usdt" -> "عنوان المحفظة TRC20"; "redotpay", "binance" -> "المعرّف (ID) أو البريد"; else -> "رقم الحساب / المعرّف"
                    })
                    MedkTextField(holder, { holder = it.take(80) }, "اسم صاحب الحساب (اختياري)")
                    MedkTextField(note, { note = it.take(200) }, "ملاحظة للعميل (اختياري)")
                }
            },
            confirmButton = {
                TextButton({
                    val m = PaymentMethod(type, title.trim(), account.trim(), holder.trim().ifBlank { null }, note.trim().ifBlank { null })
                    if (idx == -1) list.add(m) else list[idx] = m
                    editing = null
                }, enabled = title.isNotBlank() && account.isNotBlank()) { Text("تم", color = MedkColors.Violet) }
            },
            dismissButton = { TextButton({ editing = null }) { Text("إلغاء", color = Color.Gray) } },
        )
    }
}

@Composable
private fun SummaryCard(s: CommissionSummary, pct: Double?) {
    MedkCard {
        Column {
            InfoRow("طلبات مؤكدة الدفع", "${s.confirmedOrders}")
            InfoRow("المبيعات", "${dzd(s.salesDzd)} • ${usd(s.salesUsd)}")
            InfoRow("عمولة التطبيق" + (pct?.let { " (الحالية ${f2(it)}%)" } ?: ""), "${dzd(s.commissionDzd)} • ${usd(s.commissionUsd)}")
            InfoRow("المدفوع منها", "${dzd(s.paidDzd)} • ${usd(s.paidUsd)}", valueColor = MedkColors.Success)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            InfoRow("المتبقي للتطبيق", "${dzd(s.dueDzd)} • ${usd(s.dueUsd)}",
                valueColor = if (s.dueDzd > 0.5 || s.dueUsd > 0.01) MedkColors.Warning else MedkColors.Success)
        }
    }
}

@Composable
private fun SettlementRow(st: Settlement) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(listOfNotNull(st.amountDzd.takeIf { it > 0 }?.let { dzd(it) }, st.amountUsd.takeIf { it > 0 }?.let { usd(it) }).joinToString(" + "),
                style = MaterialTheme.typography.titleSmall, color = MedkColors.Success)
            st.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Text(formatDateTime(st.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============================================================================ seller
private data class SellerPayData(val methods: List<PaymentMethod>, val summary: CommissionSummary, val settlements: List<Settlement>, val pct: Double)

@Composable
fun WalletScreen(nav: NavHostController) {
    val c = MedkApp.container
    if (!c.payments.available) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            MedkTopBar("طرق الدفع والعمولة", onBack = { nav.popBackStack() })
            EmptyBox(Icons.Filled.Payments, "غير متاح في النسخة التجريبية", "الدفع اليدوي والعمولات تعمل في النسخة الحقيقية المربوطة بالخادم.")
        }
        return
    }
    val uid = c.auth.currentUid ?: ""
    val vm = loadVm("seller-pay-$uid") {
        SellerPayData(c.payments.methods(uid), c.payments.summary(uid), c.payments.settlements(uid), c.payments.commissionPct())
    }
    LifecycleResumeEffect(Unit) { vm.load(silent = true); onPauseOrDispose { } }
    val state by vm.state.collectAsStateWithLifecycle()
    val action = actionVm("seller-pay-save")
    val busy by action.busy.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("طرق الدفع والعمولة", onBack = { nav.popBackStack() })
        StateView(state, { vm.load() }) { d ->
            val list = remember(d.methods) { mutableStateListOf<PaymentMethod>().apply { addAll(d.methods) } }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("حساباتك لاستلام الدفع", style = MaterialTheme.typography.titleMedium)
                Text("يراها العميل في صفحة طلبه، يحوّل لك مباشرة ويرسل الوصل. بعد التأكد من وصول المبلغ اضغط «تم الدفع» في الطلب.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MedkCard { MethodsEditor(list) }
                PrimaryButton("حفظ طرق الدفع", {
                    action.run(toast, { toast("تم الحفظ"); vm.load(silent = true) }) { c.payments.saveMethods(uid, list.toList()) }
                }, loading = busy)
                Text("عمولة التطبيق", style = MaterialTheme.typography.titleMedium)
                SummaryCard(d.summary, d.pct)
                Text("تُحسب العمولة على كل طلب عند تأكيد الدفع بالنسبة المعمول بها وقتها. ادفع المتبقي لصاحب التطبيق ليسجّله.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (d.settlements.isNotEmpty()) {
                    Text("دفعاتك لصاحب التطبيق", style = MaterialTheme.typography.titleMedium)
                    MedkCard { Column { d.settlements.forEachIndexed { i, s -> if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp)); SettlementRow(s) } } }
                }
            }
        }
    }
}

// ============================================================================ owner
private data class SellerRow(val profile: Profile, val summary: CommissionSummary)
private data class AdminPayData(val pct: Double, val storeMethods: List<PaymentMethod>, val sellers: List<SellerRow>)

@Composable
fun AdminPaymentsScreen(nav: NavHostController) {
    val c = MedkApp.container
    if (!c.payments.available) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            MedkTopBar("الدفع والعمولات", onBack = { nav.popBackStack() })
            EmptyBox(Icons.Filled.Payments, "غير متاح في النسخة التجريبية", "الدفع اليدوي والعمولات تعمل في النسخة الحقيقية المربوطة بالخادم.")
        }
        return
    }
    val vm = loadVm("admin-payments") {
        val sellers = c.admin.staff().filter { it.roleEnum == com.medk.app.data.Role.STAFF }
            .map { SellerRow(it, c.payments.summary(it.id)) }
        AdminPayData(c.payments.commissionPct(), c.payments.methods("store"), sellers)
    }
    LifecycleResumeEffect(Unit) { vm.load(silent = true); onPauseOrDispose { } }
    val state by vm.state.collectAsStateWithLifecycle()
    val action = actionVm("admin-payments-action")
    val busy by action.busy.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    var pct by remember { mutableStateOf("") }
    var settling by remember { mutableStateOf<SellerRow?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("الدفع والعمولات", onBack = { nav.popBackStack() })
        StateView(state, { vm.load() }) { d ->
            LaunchedEffect(d.pct) { pct = f2(d.pct) }
            val store = remember(d.storeMethods) { mutableStateListOf<PaymentMethod>().apply { addAll(d.storeMethods) } }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val totalDue = d.sellers.sumOf { it.summary.dueDzd }
                val totalDueUsd = d.sellers.sumOf { it.summary.dueUsd }
                MedkCard {
                    Column {
                        Text("عمولات مستحقة لك على البائعين", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(dzd(totalDue), style = MaterialTheme.typography.headlineMedium, color = MedkColors.Violet)
                        Text(usd(totalDueUsd), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("نسبة عمولة التطبيق", style = MaterialTheme.typography.titleMedium)
                MedkCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MedkTextField(pct, { pct = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, "النسبة % (0 – 50)",
                            leading = Icons.Filled.Percent, keyboardType = KeyboardType.Decimal)
                        Text("تُطبَّق على الطلبات التي يُؤكَّد دفعها من الآن فصاعداً. الطلبات السابقة تحتفظ بنسبتها.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PrimaryButton("حفظ النسبة", {
                            val p = pct.toDoubleOrNull() ?: run { toast("أدخل نسبة صحيحة"); return@PrimaryButton }
                            action.run(toast, { toast("تم الحفظ"); vm.load(silent = true) }) { c.payments.saveCommissionPct(p) }
                        }, loading = busy)
                    }
                }
                Text("البائعون", style = MaterialTheme.typography.titleMedium)
                if (d.sellers.isEmpty()) EmptyBox(Icons.Filled.Payments, "لا يوجد بائعون بعد")
                d.sellers.forEach { row ->
                    MedkCard {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.profile.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text("${row.summary.confirmedOrders} طلب مؤكد • مبيعات ${dzd(row.summary.salesDzd)}",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(dzd(row.summary.dueDzd), style = MaterialTheme.typography.titleSmall,
                                        color = if (row.summary.dueDzd > 0.5) MedkColors.Warning else MedkColors.Success)
                                    if (row.summary.dueUsd > 0.01) Text(usd(row.summary.dueUsd), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            SecondaryButton("تسجيل دفعة عمولة من البائع", { settling = row }, height = 38.dp, enabled = !busy)
                        }
                    }
                }
                Text("حسابات المتجر", style = MaterialTheme.typography.titleMedium)
                Text("تظهر للعملاء في الطلبات التي ليس لها بائع مسؤول.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MedkCard { MethodsEditor(store) }
                PrimaryButton("حفظ حسابات المتجر", {
                    action.run(toast, { toast("تم الحفظ"); vm.load(silent = true) }) { c.payments.saveMethods("store", store.toList()) }
                }, loading = busy)
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    settling?.let { row ->
        var dzdAmount by remember { mutableStateOf("") }
        var usdAmount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { settling = null },
            title = { Text("دفعة من ${row.profile.displayName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("المتبقي: ${dzd(row.summary.dueDzd)} • ${usd(row.summary.dueUsd)}", style = MaterialTheme.typography.bodySmall)
                    MedkTextField(dzdAmount, { dzdAmount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) }, "المبلغ بالدينار", keyboardType = KeyboardType.Decimal)
                    MedkTextField(usdAmount, { usdAmount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(10) }, "أو بالدولار", keyboardType = KeyboardType.Decimal)
                    MedkTextField(note, { note = it.take(200) }, "ملاحظة (طريقة الدفع، رقم العملية…)")
                }
            },
            confirmButton = {
                TextButton({
                    val dz = dzdAmount.toDoubleOrNull() ?: 0.0; val us = usdAmount.toDoubleOrNull() ?: 0.0
                    if (dz <= 0 && us <= 0) { toast("أدخل مبلغاً"); return@TextButton }
                    val sid = row.profile.id
                    settling = null
                    action.run(toast, { toast("تم تسجيل الدفعة"); vm.load(silent = true) }) { c.payments.addSettlement(sid, dz, us, note) }
                }) { Text("تسجيل", color = MedkColors.Success) }
            },
            dismissButton = { TextButton({ settling = null }) { Text("إلغاء", color = Color.Gray) } },
        )
    }
}
