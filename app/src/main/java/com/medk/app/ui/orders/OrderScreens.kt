package com.medk.app.ui.orders

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.medk.app.MedkApp
import com.medk.app.core.dzd
import com.medk.app.core.formatDateTime
import com.medk.app.core.friendly
import com.medk.app.core.usd
import com.medk.app.data.Order
import com.medk.app.data.OrderStatus
import com.medk.app.data.Profile
import com.medk.app.data.Role
import com.medk.app.ui.components.EmptyBox
import com.medk.app.ui.components.InfoRow
import com.medk.app.ui.components.LocalToast
import com.medk.app.ui.components.MedkCard
import com.medk.app.ui.components.MedkTopBar
import com.medk.app.ui.components.NetImage
import com.medk.app.ui.components.OrderProgress
import com.medk.app.ui.components.PrimaryButton
import com.medk.app.ui.components.SecondaryButton
import com.medk.app.ui.components.StateView
import com.medk.app.ui.components.StatusChip
import com.medk.app.ui.components.actionVm
import com.medk.app.ui.components.loadVm
import com.medk.app.ui.navigation.Routes
import com.medk.app.ui.navigation.toTab
import com.medk.app.ui.theme.MedkColors
import kotlinx.coroutines.launch
import com.medk.app.ui.theme.MedkShapes

@Composable
fun OrderCard(o: Order, onClick: () -> Unit, customerName: String? = null) {
    MedkCard(onClick = onClick, padding = PaddingValues(14.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.reference, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(o.statusEnum)
            }
            if (customerName != null) Text("العميل: $customerName", style = MaterialTheme.typography.labelMedium, color = MedkColors.Violet)
            Text(o.items.joinToString("، ") { "${it.productName} ×${it.quantity}" }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDateTime(o.createdAt), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("${usd(o.totalUsd)} • ${dzd(o.totalDzd)}", style = MaterialTheme.typography.titleSmall, color = MedkColors.Violet)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("my-orders") { c.orders.visibleOrders(onlyMine = true) }
    LifecycleResumeEffect(Unit) { vm.load(silent = true); onPauseOrDispose { } }
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("طلباتي", onBack = { nav.popBackStack() })
        PullToRefreshBox(refreshing, { vm.load(silent = true) }) {
            StateView(state, { vm.load() }) { list ->
                if (list.isEmpty()) EmptyBox(Icons.AutoMirrored.Filled.ReceiptLong, "لا توجد طلبات بعد", "ابدأ التسوق وأتمم طلبك الأول",
                    "تصفح الخدمات", { nav.toTab(Routes.HOME) })
                else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { o -> OrderCard(o, { nav.navigate(Routes.order(o.id)) }) }
                }
            }
        }
    }
}

data class OrderDetail(val order: Order, val seller: Profile?, val customer: Profile?, val conversationId: String?, val staff: List<Profile>)

@Composable
fun OrderDetailScreen(id: String, nav: NavHostController) {
    val c = MedkApp.container
    val me = c.auth.profile
    val isOwner = me?.roleEnum == Role.OWNER
    val isTeam = me?.roleEnum != Role.CUSTOMER
    val vm = loadVm("order-$id") {
        val o = c.orders.order(id)
        val people = c.chat.profiles(listOfNotNull(o.staffId, o.customerId))
        val conv = runCatching { c.chat.conversationForOrder(id)?.id }.getOrNull()
        val staff = if (isOwner) runCatching { c.admin.staff().filter { it.isActive } }.getOrDefault(emptyList()) else emptyList()
        OrderDetail(o, o.staffId?.let { people[it] }, people[o.customerId], conv, staff)
    }
    LifecycleResumeEffect(Unit) { vm.load(silent = true); onPauseOrDispose { } }
    val state by vm.state.collectAsStateWithLifecycle()
    val action = actionVm("order-action-$id")
    val busy by action.busy.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var assignMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("تفاصيل الطلب", onBack = { nav.popBackStack() })
        StateView(state, { vm.load() }) { d ->
            val o = d.order
            val st = o.statusEnum
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MedkCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("رقم الطلب", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(o.reference, style = MaterialTheme.typography.headlineSmall)
                            }
                            StatusChip(st)
                        }
                        Spacer(Modifier.height(16.dp))
                        OrderProgress(st)
                    }
                }
                MedkCard {
                    Column {
                        InfoRow("تاريخ الإنشاء", formatDateTime(o.createdAt))
                        InfoRow("حالة الدفع", if (st == OrderStatus.OPEN) "بانتظار الدفع" else "مدفوع${o.paidAt?.let { " • " + formatDateTime(it) } ?: ""}",
                            valueColor = if (st == OrderStatus.OPEN) MedkColors.Warning else MedkColors.Success)
                        InfoRow("حالة التسليم", if (st == OrderStatus.DELIVERED || st == OrderStatus.CLOSED) "تم التسليم${o.deliveredAt?.let { " • " + formatDateTime(it) } ?: ""}" else "قيد المعالجة",
                            valueColor = if (st == OrderStatus.DELIVERED || st == OrderStatus.CLOSED) MedkColors.Success else MaterialTheme.colorScheme.onSurfaceVariant)
                        InfoRow("البائع المسؤول", d.seller?.displayName ?: "لم يُسند بعد")
                        if (isTeam) InfoRow("العميل", d.customer?.displayName ?: "—")
                        if (!o.note.isNullOrBlank()) InfoRow("ملاحظة", o.note)
                    }
                }
                Text("المنتجات", style = MaterialTheme.typography.titleMedium)
                MedkCard(padding = PaddingValues(10.dp)) {
                    Column {
                        o.items.forEachIndexed { i, it ->
                            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(54.dp).clip(MedkShapes.Small)) { NetImage(it.imageUrl, Modifier.fillMaxSize()) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(it.productName, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                    Text("الكمية: ${it.quantity} × ${usd(it.unitPriceUsd)}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(usd(it.unitPriceUsd * it.quantity), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        Row {
                            Text("الإجمالي", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(usd(o.totalUsd), style = MaterialTheme.typography.titleLarge, color = MedkColors.Violet)
                                Text(dzd(o.totalDzd), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (d.conversationId != null) PrimaryButton("فتح المحادثة", { nav.navigate(Routes.chat(d.conversationId)) },
                    icon = Icons.AutoMirrored.Filled.Chat)

                // ---- team controls
                val canUpdate = isOwner || (isTeam && me?.canUpdateOrders == true && o.staffId == me.id)
                if (canUpdate) {
                    Text("تحديث الحالة", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrderStatus.entries.filter { it != st }.forEach { s ->
                            SecondaryButton(s.label, {
                                action.run(toast, { toast("تم تحديث الحالة إلى: ${s.label}"); vm.load(silent = true) }) { c.orders.updateStatus(o.id, s) }
                            }, Modifier.weight(1f), enabled = !busy, height = 40.dp, color = com.medk.app.ui.components.statusColor(s))
                        }
                    }
                }
                if (isOwner) Box {
                    SecondaryButton("إسناد إلى موظف", { assignMenu = true }, icon = Icons.Filled.PersonAdd, height = 40.dp)
                    DropdownMenu(assignMenu, { assignMenu = false }) {
                        DropdownMenuItem({ Text("بدون إسناد") }, onClick = {
                            assignMenu = false
                            scope.launch { try { c.orders.assignStaff(o.id, null); vm.load(silent = true) } catch (e: Exception) { toast(e.friendly()) } }
                        })
                        d.staff.forEach { s ->
                            DropdownMenuItem({ Text(s.displayName + if (s.id == o.staffId) " ✓" else "") }, onClick = {
                                assignMenu = false
                                scope.launch {
                                    try { c.orders.assignStaff(o.id, s.id); toast("تم الإسناد إلى ${s.displayName}"); vm.load(silent = true) }
                                    catch (e: Exception) { toast(e.friendly()) }
                                }
                            })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
