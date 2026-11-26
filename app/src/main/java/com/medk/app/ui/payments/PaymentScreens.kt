package com.medk.app.ui.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.medk.app.MedkApp
import com.medk.app.core.formatDateTime
import com.medk.app.core.usd
import com.medk.app.data.PaymentsConfig
import com.medk.app.data.Wallet
import com.medk.app.data.WalletEntry
import com.medk.app.data.Withdrawal
import com.medk.app.ui.components.EmptyBox
import com.medk.app.ui.components.InfoRow
import com.medk.app.ui.components.LocalToast
import com.medk.app.ui.components.MedkCard
import com.medk.app.ui.components.MedkTextField
import com.medk.app.ui.components.MedkTopBar
import com.medk.app.ui.components.Pill
import com.medk.app.ui.components.PrimaryButton
import com.medk.app.ui.components.SecondaryButton
import com.medk.app.ui.components.StateView
import com.medk.app.ui.components.actionVm
import com.medk.app.ui.components.loadVm
import com.medk.app.ui.theme.MedkColors

private data class WalletData(val wallet: Wallet, val entries: List<WalletEntry>, val withdrawals: List<Withdrawal>)

private fun statusColor(s: String) = when (s) { "paid" -> MedkColors.Success; "rejected" -> MedkColors.Danger; else -> MedkColors.Warning }

// ============================================================================ seller wallet
@Composable
fun WalletScreen(nav: NavHostController) {
    val c = MedkApp.container
    val uid = c.auth.currentUid ?: ""
    val vm = loadVm("wallet-$uid") {
        WalletData(c.payments.wallet(uid), c.payments.entries(uid), c.payments.withdrawals(uid))
    }
    LifecycleResumeEffect(Unit) { vm.load(silent = true); onPauseOrDispose { } }
    val state by vm.state.collectAsStateWithLifecycle()
    val action = actionVm("withdraw")
    val busy by action.busy.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    var amount by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("محفظتي", onBack = { nav.popBackStack() })
        StateView(state, { vm.load() }) { d ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MedkCard {
                    Column {
                        Text("الرصيد المتاح", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%.2f".format(java.util.Locale.US, d.wallet.balanceUsd)} USDT", style = MaterialTheme.typography.headlineMedium, color = MedkColors.Violet)
                        Spacer(Modifier.height(4.dp))
                        InfoRow("إجمالي الأرباح", usd(d.wallet.earnedUsd))
                    }
                }
                Text("طلب سحب", style = MaterialTheme.typography.titleMedium)
                MedkCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MedkTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(10) }, "المبلغ (USDT)", keyboardType = KeyboardType.Decimal)
                        MedkTextField(address, { address = it.trim().take(40) }, "عنوان محفظتك USDT (شبكة TRC20)")
                        Text("أقل مبلغ للسحب 5 USDT. يُحجز المبلغ من رصيدك حتى يحوّله لك المالك.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PrimaryButton("إرسال طلب السحب", {
                            val a = amount.toDoubleOrNull()
                            if (a == null || a <= 0) { toast("أدخل مبلغاً صحيحاً"); return@PrimaryButton }
                            action.run(toast, { toast("تم إرسال طلب السحب"); amount = ""; vm.load(silent = true) }) {
                                c.payments.requestWithdrawal(a, address)
                            }
                        }, loading = busy, enabled = amount.isNotBlank() && address.isNotBlank())
                    }
                }
                if (d.withdrawals.isNotEmpty()) {
                    Text("طلبات السحب", style = MaterialTheme.typography.titleMedium)
                    MedkCard { Column { d.withdrawals.forEachIndexed { i, w -> if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp)); WithdrawalRow(w) } } }
                }
                Text("سجل الحركات", style = MaterialTheme.typography.titleMedium)
                if (d.entries.isEmpty()) EmptyBox(Icons.Filled.AccountBalanceWallet, "لا توجد حركات بعد", "تظهر هنا أرباحك عند دفع العملاء داخل التطبيق.")
                else MedkCard { Column { d.entries.forEachIndexed { i, e -> if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp)); EntryRow(e) } } }
            }
        }
    }
}

@Composable
private fun EntryRow(e: WalletEntry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(e.label + (e.reference?.let { " • $it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
            Text(formatDateTime(e.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text((if (e.amountUsd >= 0) "+" else "") + "%.2f".format(java.util.Locale.US, e.amountUsd),
            style = MaterialTheme.typography.titleSmall, color = if (e.amountUsd >= 0) MedkColors.Success else MedkColors.Danger)
    }
}

@Composable
private fun WithdrawalRow(w: Withdrawal, showName: Boolean = false) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text((if (showName) w.name.ifBlank { "بائع" } + " • " else "") + "${"%.2f".format(java.util.Locale.US, w.amountUsd)} USDT",
                    style = MaterialTheme.typography.titleSmall)
                Text(formatDateTime(w.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Pill(w.label, statusColor(w.status))
        }
        SelectionContainer { Text(w.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (!w.txid.isNullOrBlank()) SelectionContainer { Text("رقم العملية: ${w.txid}", style = MaterialTheme.typography.labelSmall) }
    }
}

// ============================================================================ owner: payments
private data class AdminPayData(val config: PaymentsConfig, val platform: Wallet, val withdrawals: List<Withdrawal>)

@Composable
fun AdminPaymentsScreen(nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("admin-payments") {
        AdminPayData(c.payments.config(), c.payments.wallet("platform"), c.payments.withdrawals(null))
    }
    LifecycleResumeEffect(Unit) { vm.load(silent = true); onPauseOrDispose { } }
    val state by vm.state.collectAsStateWithLifecycle()
    val action = actionVm("admin-payments-action")
    val busy by action.busy.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    var pct by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var paying by remember { mutableStateOf<Withdrawal?>(null) }
    var txid by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("المدفوعات", onBack = { nav.popBackStack() })
        StateView(state, { vm.load() }) { d ->
            LaunchedEffect(d.config) { pct = "%.1f".format(java.util.Locale.US, d.config.commissionPct); enabled = d.config.enabled }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MedkCard {
                    Column {
                        Text("أرباح التطبيق (عمولاتك)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%.2f".format(java.util.Locale.US, d.platform.earnedUsd)} USDT", style = MaterialTheme.typography.headlineMedium, color = MedkColors.Violet)
                        Text("كل المدفوعات تصل إلى محفظتك في بوابة الدفع. أرصدة البائعين تُحوَّل لهم عند الموافقة على طلبات السحب.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("الإعدادات", style = MaterialTheme.typography.titleMedium)
                MedkCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("تفعيل الدفع داخل التطبيق", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(enabled, { enabled = it }, colors = SwitchDefaults.colors(checkedTrackColor = MedkColors.Purple))
                        }
                        MedkTextField(pct, { pct = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, "نسبة عمولة التطبيق %",
                            leading = Icons.Filled.Percent, keyboardType = KeyboardType.Decimal)
                        Text("تُحسب العمولة من المبلغ بعد خصم رسوم البوابة، والباقي للبائع. الطلبات بدون بائع: المبلغ كله لك.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PrimaryButton("حفظ", {
                            val p = pct.toDoubleOrNull()
                            if (p == null) { toast("أدخل نسبة صحيحة"); return@PrimaryButton }
                            action.run(toast, { toast("تم الحفظ"); vm.load(silent = true) }) { c.payments.saveConfig(PaymentsConfig(enabled, p)) }
                        }, loading = busy)
                    }
                }
                val pending = d.withdrawals.filter { it.status == "pending" }
                Text("طلبات السحب المعلّقة (${pending.size})", style = MaterialTheme.typography.titleMedium)
                if (pending.isEmpty()) EmptyBox(Icons.Filled.AccountBalanceWallet, "لا توجد طلبات سحب معلّقة")
                pending.forEach { w ->
                    MedkCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            WithdrawalRow(w, showName = true)
                            Row {
                                PrimaryButton("تم التحويل", { paying = w; txid = "" }, Modifier.weight(1f), enabled = !busy)
                                Spacer(Modifier.width(8.dp))
                                SecondaryButton("رفض", {
                                    action.run(toast, { toast("تم الرفض وإرجاع المبلغ للبائع"); vm.load(silent = true) }) { c.payments.decide(w.id, false, null) }
                                }, Modifier.weight(1f), enabled = !busy, color = MedkColors.Danger)
                            }
                        }
                    }
                }
                val done = d.withdrawals.filter { it.status != "pending" }
                if (done.isNotEmpty()) {
                    Text("السجل", style = MaterialTheme.typography.titleMedium)
                    MedkCard { Column { done.forEachIndexed { i, w -> if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp)); WithdrawalRow(w, showName = true) } } }
                }
            }
        }
    }

    paying?.let { w ->
        AlertDialog(
            onDismissRequest = { paying = null },
            title = { Text("تأكيد التحويل") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("حوّل ${"%.2f".format(java.util.Locale.US, w.amountUsd)} USDT (TRC20) إلى:", style = MaterialTheme.typography.bodyMedium)
                    SelectionContainer { Text(w.address, style = MaterialTheme.typography.bodySmall, color = MedkColors.Violet) }
                    MedkTextField(txid, { txid = it.trim().take(120) }, "رقم العملية (اختياري)")
                }
            },
            confirmButton = {
                TextButton({
                    val id = w.id; val t = txid
                    paying = null
                    action.run(toast, { toast("تم تسجيل التحويل"); vm.load(silent = true) }) { c.payments.decide(id, true, t) }
                }) { Text("تم التحويل", color = MedkColors.Success) }
            },
            dismissButton = { TextButton({ paying = null }) { Text("إلغاء", color = Color.Gray) } },
        )
    }
}
