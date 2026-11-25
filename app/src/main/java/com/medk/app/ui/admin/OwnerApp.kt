package com.medk.app.ui.admin

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.medk.app.MedkApp
import com.medk.app.PendingLink
import com.medk.app.core.dzd
import com.medk.app.core.formatDate
import com.medk.app.core.usd
import com.medk.app.data.Order
import com.medk.app.data.Profile
import com.medk.app.data.Role
import com.medk.app.ui.ClassicNavBar
import com.medk.app.ui.Tab
import com.medk.app.ui.chat.ConversationsScreen
import com.medk.app.ui.components.Avatar
import com.medk.app.ui.components.EmptyBox
import com.medk.app.ui.components.InfoRow
import com.medk.app.ui.components.LocalToast
import com.medk.app.ui.components.MedkCard
import com.medk.app.ui.components.MedkMark
import com.medk.app.ui.components.MedkTextField
import com.medk.app.ui.components.MedkTopBar
import com.medk.app.ui.components.Pill
import com.medk.app.ui.components.PrimaryButton
import com.medk.app.ui.components.SecondaryButton
import com.medk.app.ui.components.StateView
import com.medk.app.ui.components.actionVm
import com.medk.app.ui.components.loadVm
import com.medk.app.ui.detailRoutes
import com.medk.app.ui.navigation.Routes
import com.medk.app.ui.navigation.toTab
import com.medk.app.ui.orders.OrderCard
import com.medk.app.ui.theme.MedkColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// ===================================================================== shell
/** MED-K Admin — the owner's dedicated control-panel app. */
@Composable
fun OwnerAppShell(profile: Profile, pendingLink: MutableStateFlow<PendingLink?>) {
    val c = MedkApp.container
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val unread by c.notifications.unread.collectAsStateWithLifecycle()
    val link by pendingLink.collectAsStateWithLifecycle()
    LaunchedEffect(link) {
        link?.let { l ->
            l.conversationId?.let { nav.navigate(Routes.chat(it)) } ?: l.orderId?.let { nav.navigate(Routes.order(it)) }
            pendingLink.value = null
        }
    }
    val tabs = listOf(
        Tab(Routes.WORKSPACE, "لوحة التحكم", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
        Tab(Routes.ADMIN_ORDERS, "الطلبات", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong),
        Tab(Routes.CONVERSATIONS, "المحادثات", Icons.Outlined.ChatBubbleOutline, Icons.AutoMirrored.Filled.Chat),
        Tab(Routes.ADMIN_PRODUCTS, "المنتجات", Icons.Outlined.Inventory2, Icons.Filled.Inventory2),
        Tab(Routes.ADMIN_MORE, "المزيد", Icons.Filled.MoreHoriz, Icons.Filled.MoreHoriz),
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (tabs.any { it.route == route }) ClassicNavBar(tabs, route, mapOf(Routes.ADMIN_MORE to unread)) { r ->
                nav.toTab(if (r == Routes.ADMIN_ORDERS) Routes.adminOrders() else r)
            }
        },
    ) { pad ->
        NavHost(nav, Routes.WORKSPACE, Modifier.padding(bottom = pad.calculateBottomPadding())) {
            composable(Routes.WORKSPACE) { WorkspaceScreen(nav, null) }
            composable(Routes.ADMIN_ORDERS, listOf(navArgument("status") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                AdminOrdersScreen(nav, it.arguments?.getString("status"), asTab = true)
            }
            composable(Routes.CONVERSATIONS) { ConversationsScreen(nav, null, showBack = false) }
            composable(Routes.ADMIN_PRODUCTS) { AdminProductsScreen(nav, asTab = true) }
            composable(Routes.ADMIN_MORE) { AdminMoreScreen(nav, profile, unread) }
            detailRoutes(nav)
        }
    }
}

/** Shown when a non-owner signs into the owner app. */
@Composable
fun OwnerOnlyScreen(profile: Profile, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyBox(Icons.Filled.AdminPanelSettings, "هذا التطبيق مخصص لمالك المتجر",
            "حسابك (${profile.email ?: profile.displayName}) ليس حساب مالك. استخدم تطبيق MED-K للتسوق أو لمساحة عمل البائع.")
        SecondaryButton("تسجيل الخروج", onSignOut)
    }
}

// ====================================================================== more
@Composable
fun AdminMoreScreen(nav: NavHostController, profile: Profile, unread: Int) {
    val c = MedkApp.container
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("المزيد")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MedkCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MedkMark(44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(profile.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Pill("المالك", MedkColors.Violet)
                }
            }
            Group("الأشخاص") {
                MoreRow(Icons.Filled.ManageAccounts, "البائعون والصلاحيات") { nav.navigate(Routes.ADMIN_STAFF) }
                MoreRow(Icons.Filled.People, "العملاء") { nav.navigate(Routes.ADMIN_CUSTOMERS) }
            }
            Group("المتجر") {
                MoreRow(Icons.Filled.Category, "الأقسام الرئيسية") { nav.navigate(Routes.ADMIN_CATEGORIES) }
                MoreRow(Icons.Filled.ViewList, "الأقسام الفرعية والألعاب") { nav.navigate(Routes.ADMIN_SECTIONS) }
                MoreRow(Icons.Filled.ViewCarousel, "البانرات الإعلانية") { nav.navigate(Routes.ADMIN_BANNERS) }
                MoreRow(Icons.Filled.CurrencyExchange, "أسعار العملات P2P") { nav.navigate(Routes.ADMIN_P2P) }
            }
            Group("الحساب") {
                MoreRow(Icons.Filled.Notifications, "الإشعارات", badge = unread) { nav.navigate(Routes.NOTIFICATIONS) }
                MoreRow(Icons.Filled.Settings, "الإعدادات") { nav.navigate(Routes.SETTINGS) }
                MoreRow(Icons.Filled.Info, "حول MED-K") { nav.navigate(Routes.ABOUT) }
                MoreRow(Icons.AutoMirrored.Filled.Logout, "تسجيل الخروج", tint = MedkColors.Danger) { scope.launch { c.auth.signOut(ctx) } }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    MedkCard(padding = PaddingValues(vertical = 4.dp)) { Column { content() } }
}

@Composable
private fun MoreRow(icon: ImageVector, label: String, tint: Color = MedkColors.Violet, badge: Int = 0, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(tint.copy(.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            color = if (tint == MedkColors.Danger) tint else MaterialTheme.colorScheme.onSurface)
        com.medk.app.ui.components.CountBadge(badge)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ================================================================= customers
data class CustomerRow(val profile: Profile, val orders: List<Order>)

@Composable
fun AdminCustomersScreen(nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("admin-customers") {
        val orders = runCatching { c.orders.visibleOrders() }.getOrDefault(emptyList()).groupBy { it.customerId }
        c.admin.customers().map { CustomerRow(it, orders[it.id].orEmpty()) }
    }
    LifecycleResumeEffect(Unit) { vm.load(silent = true); onPauseOrDispose { } }
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("العملاء", onBack = { nav.popBackStack() })
        StateView(state, { vm.load() }) { list ->
            val q = query.trim()
            val shown = list.filter { q.isEmpty() || it.profile.displayName.contains(q, true) || it.profile.email.orEmpty().contains(q, true) }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { MedkTextField(query, { query = it }, "بحث بالاسم أو البريد", leading = Icons.Filled.Search) }
                item {
                    Text("${list.size} عميل • ${list.count { !it.profile.isActive }} موقوف", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (shown.isEmpty()) item { EmptyBox(Icons.Filled.People, "لا يوجد عملاء") }
                items(shown, key = { it.profile.id }) { r ->
                    MedkCard(onClick = { nav.navigate(Routes.customerDetail(r.profile.id)) }, padding = PaddingValues(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(r.profile.displayName, r.profile.avatarUrl, 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.profile.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(r.profile.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${r.orders.size} طلب", style = MaterialTheme.typography.labelMedium)
                                if (!r.profile.isActive) Pill("موقوف", MedkColors.Danger)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerDetailScreen(id: String, nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("customer-$id") {
        val p = c.admin.customers().firstOrNull { it.id == id } ?: c.chat.profiles(listOf(id))[id] ?: error("العميل غير موجود")
        CustomerRow(p, c.orders.visibleOrders().filter { it.customerId == id })
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val action = actionVm("customer-action")
    val busy by action.busy.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("ملف العميل", onBack = { nav.popBackStack() })
        StateView(state, { vm.load() }) { r ->
            val p = r.profile
            val paid = r.orders.filter { it.status != "open" }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    MedkCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(p.displayName, p.avatarUrl, 52.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(p.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Pill(if (p.isActive) "نشط" else "موقوف", if (p.isActive) MedkColors.Success else MedkColors.Danger,
                                    icon = if (p.isActive) Icons.Filled.CheckCircle else Icons.Filled.Block)
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            InfoRow("تاريخ التسجيل", formatDate(p.createdAt))
                            InfoRow("عدد الطلبات", "${r.orders.size}")
                            InfoRow("إجمالي المدفوع", "${usd(paid.sumOf { it.totalUsd })} • ${dzd(paid.sumOf { it.totalDzd })}", valueColor = MedkColors.Violet)
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(if (p.isActive) "إيقاف الحساب" else "تفعيل الحساب", { confirm = "active" }, Modifier.weight(1f),
                            color = if (p.isActive) MedkColors.Danger else MedkColors.Success, enabled = !busy)
                        PrimaryButton("ترقية إلى بائع", { confirm = "staff" }, Modifier.weight(1f), enabled = !busy && p.isActive)
                    }
                }
                item { Text("طلبات العميل", style = MaterialTheme.typography.titleMedium) }
                if (r.orders.isEmpty()) item { EmptyBox(Icons.AutoMirrored.Filled.ReceiptLong, "لا توجد طلبات") }
                items(r.orders, key = { it.id }) { o -> OrderCard(o, { nav.navigate(Routes.order(o.id)) }) }
            }
            confirm?.let { what ->
                AlertDialog({ confirm = null },
                    title = { Text(if (what == "staff") "ترقية ${p.displayName} إلى بائع؟" else if (p.isActive) "إيقاف الحساب؟" else "تفعيل الحساب؟") },
                    text = { Text(if (what == "staff") "سيظهر في قائمة البائعين، ويمكنك بعدها تحديد صلاحياته والأقسام المسموحة له."
                                  else if (p.isActive) "لن يتمكن العميل من تسجيل الدخول أو إنشاء طلبات." else "سيتمكن العميل من استخدام التطبيق مجدداً.") },
                    confirmButton = { TextButton({
                        confirm = null
                        if (what == "staff") action.run(toast, { toast("تمت الترقية — حدّد صلاحياته الآن"); nav.navigate(Routes.staffDetail(p.id)) { popUpTo(Routes.ADMIN_CUSTOMERS) } }) {
                            c.admin.setRole(p.id, Role.STAFF)
                        } else action.run(toast, { toast("تم التحديث"); vm.load(silent = true) }) { c.admin.setUserActive(p.id, !p.isActive) }
                    }) { Text("تأكيد", color = MedkColors.Violet) } },
                    dismissButton = { TextButton({ confirm = null }) { Text("إلغاء") } })
            }
        }
    }
}

