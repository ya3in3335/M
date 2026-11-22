package com.medk.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.medk.app.MedkApp
import com.medk.app.core.dzd
import com.medk.app.core.usd
import com.medk.app.data.DashboardStats
import com.medk.app.data.Order
import com.medk.app.data.OrderStatus
import com.medk.app.data.Profile
import com.medk.app.data.Role
import com.medk.app.ui.catalog.Chip
import com.medk.app.ui.components.EmptyBox
import com.medk.app.ui.components.MedkTopBar
import com.medk.app.ui.components.SectionHeader
import com.medk.app.ui.components.StateView
import com.medk.app.ui.components.loadVm
import com.medk.app.ui.navigation.Routes
import com.medk.app.ui.navigation.toTab
import com.medk.app.ui.orders.OrderCard
import com.medk.app.ui.theme.MedkColors
import com.medk.app.ui.theme.blueprintGrid
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.medk.app.ui.theme.MedkShapes

data class WorkspaceData(val stats: DashboardStats, val incoming: List<Order>, val customers: Map<String, Profile>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(nav: NavHostController, onMenu: () -> Unit) {
    val c = MedkApp.container
    val me = c.auth.profile ?: return
    val isOwner = me.roleEnum == Role.OWNER
    val vm = loadVm("workspace") {
        coroutineScope {
            val stats = async { c.admin.stats() }
            val incoming = async {
                c.orders.visibleOrders().filter { it.status == "open" || it.status == "paid" }
                    .filter { isOwner || it.staffId == me.id }.take(8)
            }
            val list = incoming.await()
            WorkspaceData(stats.await(), list, c.chat.profiles(list.map { it.customerId }.toSet()))
        }
    }
    LifecycleResumeEffect(Unit) { vm.load(silent = true); onPauseOrDispose { } }
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar(if (isOwner) "لوحة تحكم المالك" else "مساحة عمل الموظف", subtitle = me.displayName,
            navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "القائمة") } },
            actions = { IconButton({ nav.toTab(Routes.HOME) }) { Icon(Icons.Filled.Storefront, "عرض المتجر") } })
        PullToRefreshBox(refreshing, { vm.load(silent = true) }) {
            StateView(state, { vm.load() }) { d ->
                val s = d.stats
                LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(16.dp, 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(span = { GridItemSpan(2) }) {
                        Row(Modifier.fillMaxWidth().clip(MedkShapes.Chamfer).background(MedkColors.PrimaryGradient)
                            .blueprintGrid(Color.White, 16f, .08f).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (isOwner) "الإيرادات المؤكدة" else "إيرادات طلباتك المؤكدة", color = Color.White.copy(.8f),
                                    style = MaterialTheme.typography.labelMedium)
                                Text(usd(s.revenueUsd), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                                Text(dzd(s.revenueDzd), color = Color.White.copy(.8f), style = MaterialTheme.typography.labelMedium)
                            }
                            Box(Modifier.size(46.dp).clip(MedkShapes.Emblem).background(Color.White.copy(.18f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Paid, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    item { StatCard("إجمالي الطلبات", "${s.totalOrders}", Icons.AutoMirrored.Filled.ReceiptLong, MedkColors.Violet) { nav.navigate(Routes.adminOrders()) } }
                    item { StatCard("محادثات مفتوحة", "${s.openConversations}", Icons.AutoMirrored.Filled.Chat, MedkColors.Info) { nav.toTab(Routes.CONVERSATIONS) } }
                    item { StatCard("طلبات مفتوحة", "${s.openOrders}", Icons.Filled.PendingActions, MedkColors.Warning) { nav.navigate(Routes.adminOrders("open")) } }
                    item { StatCard("طلبات مدفوعة", "${s.paidOrders}", Icons.Filled.Paid, MedkColors.Info) { nav.navigate(Routes.adminOrders("paid")) } }
                    item { StatCard("طلبات مُسلَّمة", "${s.deliveredOrders}", Icons.Filled.LocalShipping, MedkColors.Success) { nav.navigate(Routes.adminOrders("delivered")) } }
                    if (isOwner) item { StatCard("غير مُسندة", "${s.unassignedOrders}", Icons.Filled.Groups, MedkColors.Danger) { nav.navigate(Routes.adminOrders()) } }
                    else item { StatCard("طلبات مغلقة", "${s.closedOrders}", Icons.AutoMirrored.Filled.ReceiptLong, Color(0xFF8E8A99)) { nav.navigate(Routes.adminOrders("closed")) } }

                    item(span = { GridItemSpan(2) }) { SectionHeader("الإدارة", index = 1) }
                    item { Tile("الطلبات", Icons.AutoMirrored.Filled.ReceiptLong) { nav.navigate(Routes.adminOrders()) } }
                    item { Tile("المحادثات", Icons.AutoMirrored.Filled.Chat) { nav.toTab(Routes.CONVERSATIONS) } }
                    if (isOwner || me.canManageProducts) item { Tile("المنتجات", Icons.Filled.Inventory2, "${s.products}") { nav.navigate(Routes.ADMIN_PRODUCTS) } }
                    if (isOwner) {
                        item { Tile("الأقسام الرئيسية", Icons.Filled.Category) { nav.navigate(Routes.ADMIN_CATEGORIES) } }
                        item { Tile("الأقسام الفرعية", Icons.Filled.ViewList) { nav.navigate(Routes.ADMIN_SECTIONS) } }
                        item { Tile("الموظفون", Icons.Filled.Groups, "${s.staff}") { nav.navigate(Routes.ADMIN_STAFF) } }
                        item { Tile("البانرات", Icons.Filled.ViewCarousel) { nav.navigate(Routes.ADMIN_BANNERS) } }
                        item { Tile("أسعار P2P", Icons.Filled.CurrencyExchange) { nav.navigate(Routes.ADMIN_P2P) } }
                    }
                    item(span = { GridItemSpan(2) }) { SectionHeader("الطلبات الواردة", action = "عرض الكل", onAction = { nav.navigate(Routes.adminOrders()) }, index = 2) }
                    if (d.incoming.isEmpty()) item(span = { GridItemSpan(2) }) { EmptyBox(Icons.Filled.SpaceDashboard, "لا توجد طلبات بانتظار المعالجة") }
                    items(d.incoming, key = { it.id }, span = { GridItemSpan(2) }) { o ->
                        OrderCard(o, { nav.navigate(Routes.order(o.id)) }, customerName = d.customers[o.customerId]?.displayName)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MedkShapes.ChamferSm).background(MaterialTheme.colorScheme.surfaceContainer)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MedkShapes.ChamferSm).clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(MedkShapes.Emblem).background(color.copy(.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Tile(label: String, icon: ImageVector, badge: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MedkShapes.ChamferSm).background(MaterialTheme.colorScheme.surfaceContainer)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MedkShapes.ChamferSm).clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MedkColors.Violet, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (badge != null) Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============================================================ orders admin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminOrdersScreen(nav: NavHostController, initialStatus: String?) {
    val c = MedkApp.container
    val me = c.auth.profile
    var status by rememberSaveable { mutableStateOf(initialStatus) }
    var onlyUnassigned by rememberSaveable { mutableStateOf(false) }
    val vm = loadVm("admin-orders") {
        val list = c.orders.visibleOrders().filter { me?.roleEnum == Role.OWNER || it.staffId == me?.id }
        list to c.chat.profiles(list.flatMap { listOfNotNull(it.customerId, it.staffId) }.toSet())
    }
    LifecycleResumeEffect(Unit) { vm.load(silent = true); onPauseOrDispose { } }
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("إدارة الطلبات", onBack = { nav.popBackStack() })
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Chip("الكل", status == null && !onlyUnassigned) { status = null; onlyUnassigned = false } }
            items(OrderStatus.entries) { s -> Chip(s.label, status == s.key) { status = s.key; onlyUnassigned = false } }
            if (me?.roleEnum == Role.OWNER) item { Chip("غير مُسندة", onlyUnassigned) { onlyUnassigned = true; status = null } }
        }
        PullToRefreshBox(refreshing, { vm.load(silent = true) }) {
            StateView(state, { vm.load() }) { (orders, people) ->
                val list = orders.filter { (status == null || it.status == status) && (!onlyUnassigned || it.staffId == null) }
                if (list.isEmpty()) EmptyBox(Icons.AutoMirrored.Filled.ReceiptLong, "لا توجد طلبات")
                else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { o ->
                        Column {
                            OrderCard(o, { nav.navigate(Routes.order(o.id)) }, customerName = people[o.customerId]?.displayName)
                            if (me?.roleEnum == Role.OWNER) Text("البائع: " + (o.staffId?.let { people[it]?.displayName } ?: "غير مُسند"),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp, top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
