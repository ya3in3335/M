package com.medk.app.ui.profile

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.medk.app.BuildConfig
import com.medk.app.MedkApp
import com.medk.app.core.formatShort
import com.medk.app.data.AuthState
import com.medk.app.data.Role
import com.medk.app.ui.catalog.ProductGrid
import com.medk.app.ui.components.Avatar
import com.medk.app.ui.components.CountBadge
import com.medk.app.ui.components.EmptyBox
import com.medk.app.ui.components.LocalToast
import com.medk.app.ui.components.MedkCard
import com.medk.app.ui.components.MedkMark
import com.medk.app.ui.components.MedkTextField
import com.medk.app.ui.components.MedkTopBar
import com.medk.app.ui.components.MedkWordmark
import com.medk.app.ui.components.PrimaryButton
import com.medk.app.ui.components.StateView
import com.medk.app.ui.components.actionVm
import com.medk.app.ui.components.loadVm
import com.medk.app.ui.navigation.Routes
import com.medk.app.ui.navigation.toTab
import com.medk.app.ui.openSupport
import com.medk.app.ui.theme.MedkColors
import com.medk.app.ui.theme.blueprintGrid
import kotlinx.coroutines.launch
import com.medk.app.ui.theme.MedkShapes

@Composable
private fun MenuRow(icon: ImageVector, label: String, tint: Color = MedkColors.Violet, badge: Int = 0,
                    trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().clip(MedkShapes.Small).let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(MedkShapes.Hex).background(tint.copy(.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
            color = if (tint == MedkColors.Danger) tint else MaterialTheme.colorScheme.onSurface)
        CountBadge(badge)
        if (trailing != null) trailing() else if (onClick != null)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ================================================================= profile
@Composable
fun ProfileScreen(nav: NavHostController, onMenu: () -> Unit) {
    val c = MedkApp.container
    val auth by c.auth.state.collectAsStateWithLifecycle()
    val profile = (auth as? AuthState.SignedIn)?.profile ?: return
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val ctx = LocalContext.current
    val unread by c.notifications.unread.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("حسابي", navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "القائمة") } })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clip(MedkShapes.Chamfer).background(MedkColors.PrimaryGradient)
                .blueprintGrid(Color.White, 16f, .08f).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(MedkShapes.Hex).background(Color.White.copy(.25f)), contentAlignment = Alignment.Center) {
                    Avatar(profile.displayName, profile.avatarUrl, 50.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.displayName, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(profile.email ?: "", color = Color.White.copy(.85f), style = MaterialTheme.typography.bodySmall)
                    if (profile.roleEnum != Role.CUSTOMER) Text(if (profile.roleEnum == Role.OWNER) "المالك" else "فريق المبيعات",
                        color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(.25f))
                            .padding(horizontal = 10.dp, vertical = 2.dp))
                }
            }
            MedkCard(padding = PaddingValues(8.dp)) {
                Column {
                    if (profile.roleEnum != Role.CUSTOMER) MenuRow(Icons.Filled.Dashboard, "لوحة التحكم") { nav.toTab(Routes.WORKSPACE) }
                    MenuRow(Icons.AutoMirrored.Filled.ReceiptLong, "طلباتي") { nav.navigate(Routes.ORDERS) }
                    MenuRow(Icons.AutoMirrored.Filled.Chat, "محادثاتي") { nav.toTab(Routes.CONVERSATIONS) }
                    MenuRow(Icons.Filled.Favorite, "المفضلة") { nav.navigate(Routes.FAVORITES) }
                    MenuRow(Icons.Filled.Notifications, "الإشعارات", badge = unread) { nav.navigate(Routes.NOTIFICATIONS) }
                }
            }
            MedkCard(padding = PaddingValues(8.dp)) {
                Column {
                    MenuRow(Icons.Filled.Settings, "الإعدادات") { nav.navigate(Routes.SETTINGS) }
                    MenuRow(Icons.Filled.SupportAgent, "خدمة العملاء") { openSupport(nav, scope, toast) }
                    MenuRow(Icons.Filled.Info, "حول MED-K") { nav.navigate(Routes.ABOUT) }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    MenuRow(Icons.AutoMirrored.Filled.Logout, "تسجيل الخروج", tint = MedkColors.Danger) { scope.launch { c.auth.signOut(ctx) } }
                }
            }
        }
    }
}

// ================================================================ settings
@Composable
fun SettingsScreen(nav: NavHostController) {
    val c = MedkApp.container
    val dark by c.settings.darkTheme.collectAsStateWithLifecycle(true)
    val notif by c.settings.notificationsEnabled.collectAsStateWithLifecycle(true)
    val scope = rememberCoroutineScope()
    val switchColors = SwitchDefaults.colors(checkedTrackColor = MedkColors.Purple)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("الإعدادات", onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("المظهر واللغة", style = MaterialTheme.typography.titleMedium)
            MedkCard(padding = PaddingValues(8.dp)) {
                Column {
                    MenuRow(Icons.Filled.DarkMode, "الوضع الداكن", trailing = {
                        Switch(dark, { v -> scope.launch { c.settings.setDarkTheme(v) } }, colors = switchColors)
                    }, onClick = { scope.launch { c.settings.setDarkTheme(!dark) } })
                    MenuRow(Icons.Filled.Language, "اللغة", trailing = {
                        Text("العربية ✓", color = MedkColors.Violet, style = MaterialTheme.typography.labelLarge)
                    }, onClick = null)
                }
            }
            Text("الإشعارات", style = MaterialTheme.typography.titleMedium)
            MedkCard(padding = PaddingValues(8.dp)) {
                MenuRow(Icons.Filled.Notifications, "إشعارات الرسائل والطلبات", trailing = {
                    Switch(notif, { v -> scope.launch { c.settings.setNotifications(v) } }, colors = switchColors)
                }, onClick = { scope.launch { c.settings.setNotifications(!notif) } })
            }
            Text("الحساب", style = MaterialTheme.typography.titleMedium)
            MedkCard(padding = PaddingValues(8.dp)) {
                Column {
                    MenuRow(Icons.Filled.ManageAccounts, "إعدادات الحساب") { nav.navigate(Routes.ACCOUNT) }
                    MenuRow(Icons.Filled.PrivacyTip, "سياسة الخصوصية") { nav.navigate(Routes.PRIVACY) }
                    MenuRow(Icons.Filled.Info, "حول MED-K") { nav.navigate(Routes.ABOUT) }
                }
            }
            Text("الإصدار ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

// ================================================================= account
@Composable
fun AccountScreen(nav: NavHostController) {
    val c = MedkApp.container
    val profile = c.auth.profile
    val toast = LocalToast.current
    var name by rememberSaveable { mutableStateOf(profile?.fullName ?: "") }
    var current by rememberSaveable { mutableStateOf("") }
    var newPass by rememberSaveable { mutableStateOf("") }
    val nameAction = actionVm("acc-name")
    val passAction = actionVm("acc-pass")
    val nameBusy by nameAction.busy.collectAsStateWithLifecycle()
    val passBusy by passAction.busy.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("إعدادات الحساب", onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("المعلومات الشخصية", style = MaterialTheme.typography.titleMedium)
            MedkTextField(profile?.email ?: "", {}, "البريد الإلكتروني", leading = Icons.Filled.Email, enabled = false)
            MedkTextField(name, { name = it }, "الاسم الكامل", leading = Icons.Filled.Person)
            PrimaryButton("حفظ الاسم", {
                nameAction.run(toast, { toast("تم حفظ الاسم") }) { c.auth.updateName(name.trim()) }
            }, enabled = name.trim().length >= 2, loading = nameBusy)
            if (c.auth.isPasswordAccount) {
                Spacer(Modifier.height(10.dp))
                Text("تغيير كلمة المرور", style = MaterialTheme.typography.titleMedium)
                MedkTextField(current, { current = it }, "كلمة المرور الحالية", leading = Icons.Filled.Lock, isPassword = true)
                MedkTextField(newPass, { newPass = it }, "كلمة المرور الجديدة (8 أحرف على الأقل)", leading = Icons.Filled.Lock, isPassword = true)
                PrimaryButton("تغيير كلمة المرور", {
                    passAction.run(toast, { toast("تم تغيير كلمة المرور"); current = ""; newPass = "" }) { c.auth.changePassword(current, newPass) }
                }, enabled = current.isNotEmpty() && newPass.length >= 8, loading = passBusy)
            } else {
                Text("تم تسجيل الدخول عبر Google — تتم إدارة كلمة المرور من حساب Google الخاص بك.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// =============================================================== favorites
@Composable
fun FavoritesScreen(nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("favorites") { c.catalog.loadFavorites() }
    val favs by c.catalog.favoriteIds.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("المفضلة", onBack = { nav.popBackStack() })
        StateView(state, { vm.load() }) { list ->
            val visible = list.filter { it.id in favs }
            if (visible.isEmpty()) EmptyBox(Icons.Filled.Favorite, "لا توجد منتجات مفضلة", "اضغط على ♥ في أي منتج لإضافته هنا")
            else ProductGrid(visible, nav)
        }
    }
}

// =========================================================== notifications
@Composable
fun NotificationsScreen(nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("notifications") { c.notifications.list() }
    LifecycleResumeEffect(Unit) { vm.load(silent = true); onPauseOrDispose { } }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("الإشعارات", onBack = { nav.popBackStack() }, actions = {
            IconButton({ scope.launch { runCatching { c.notifications.markAllRead() }; vm.update { l -> l.map { it.copy(isRead = true) } } } }) {
                Icon(Icons.Filled.DoneAll, "تعليم الكل كمقروء")
            }
        })
        StateView(state, { vm.load() }) { list ->
            if (list.isEmpty()) EmptyBox(Icons.Filled.NotificationsNone, "لا توجد إشعارات", "ستظهر هنا تحديثات طلباتك ورسائلك")
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { n ->
                    MedkCard(onClick = {
                        if (!n.isRead) scope.launch { runCatching { c.notifications.markRead(n.id) } }
                        vm.update { l -> l.map { if (it.id == n.id) it.copy(isRead = true) else it } }
                        when {
                            n.conversationId != null -> nav.navigate(Routes.chat(n.conversationId))
                            n.orderId != null -> nav.navigate(Routes.order(n.orderId))
                        }
                    }, padding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(if (n.isRead) Color.Transparent else MedkColors.Violet))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(n.title, style = MaterialTheme.typography.titleSmall)
                                if (!n.body.isNullOrBlank()) Text(n.body, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatShort(n.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// =================================================================== about
@Composable
fun AboutScreen(nav: NavHostController) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("حول MED-K", onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MedkMark(96.dp)
            MedkWordmark(subtitle = true)
            Text("MED-K سوق للخدمات الرقمية يجمع شحن الألعاب، اشتراكات التطبيقات، بيع الحسابات، تبادل العملات الرقمية P2P، بطاقات الهدايا وتراخيص البرامج في تطبيق واحد.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MedkCard {
                Column {
                    Text("كيف نعمل؟", style = MaterialTheme.typography.titleMedium)
                    Text("1. اختر المنتج وأضفه إلى السلة.\n2. يتم إنشاء طلب ومحادثة خاصة مع البائع المسؤول.\n3. ادفع حسب التعليمات داخل المحادثة.\n4. يستلم البائع الدفع ويُسلِّم طلبك مع متابعة الحالة لحظياً.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("الإصدار ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PrivacyScreen(nav: NavHostController) {
    val sections = listOf(
        "البيانات التي نجمعها" to "الاسم، البريد الإلكتروني، وصورة الحساب (عند تسجيل الدخول عبر Google)، الطلبات، والرسائل والملفات التي ترسلها داخل المحادثات.",
        "كيف نستخدم البيانات" to "لتنفيذ طلباتك، التواصل معك عبر المحادثات، إرسال إشعارات حالة الطلب، وتحسين الخدمة. لا نبيع بياناتك لأي طرف ثالث.",
        "الأمان" to "تتم المصادقة عبر Firebase Authentication، وتُحمى البيانات بسياسات وصول على مستوى الصفوف (RLS): لا يرى العميل إلا طلباته ومحادثاته، ولا يرى الموظف إلا المحادثات المسندة إليه. ملفات المحادثات مخزنة في مساحة خاصة وتُعرض عبر روابط مؤقتة.",
        "المدفوعات" to "لا يقوم التطبيق بمعالجة بيانات البطاقات البنكية. تتم تعليمات الدفع داخل المحادثة مع البائع المعتمد فقط.",
        "حقوقك" to "يمكنك تعديل اسمك من إعدادات الحساب، وطلب حذف حسابك وبياناتك عبر خدمة العملاء.",
    )
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("سياسة الخصوصية", onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            sections.forEach { (t, b) ->
                MedkCard {
                    Column {
                        Text(t, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(b, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
