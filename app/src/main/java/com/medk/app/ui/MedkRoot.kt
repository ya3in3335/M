package com.medk.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import com.medk.app.core.friendly
import com.medk.app.data.AuthState
import com.medk.app.data.Profile
import com.medk.app.data.Role
import com.medk.app.ui.admin.AdminBannersScreen
import com.medk.app.ui.admin.AdminCategoriesScreen
import com.medk.app.ui.admin.AdminOrdersScreen
import com.medk.app.ui.admin.AdminP2pScreen
import com.medk.app.ui.admin.AdminProductsScreen
import com.medk.app.ui.admin.AdminSectionsScreen
import com.medk.app.ui.admin.AdminStaffScreen
import com.medk.app.ui.admin.ProductEditScreen
import com.medk.app.ui.admin.StaffDetailScreen
import com.medk.app.ui.admin.WorkspaceScreen
import com.medk.app.ui.auth.AuthFailedScreen
import com.medk.app.ui.auth.ForgotPasswordScreen
import com.medk.app.ui.auth.LoginScreen
import com.medk.app.ui.auth.NotConfiguredScreen
import com.medk.app.ui.auth.RegisterScreen
import com.medk.app.ui.auth.SplashScreen
import com.medk.app.ui.auth.WelcomeScreen
import com.medk.app.ui.cart.CartScreen
import com.medk.app.ui.catalog.CategoriesScreen
import com.medk.app.ui.catalog.CategoryScreen
import com.medk.app.ui.catalog.SearchScreen
import com.medk.app.ui.catalog.SectionScreen
import com.medk.app.ui.chat.ChatScreen
import com.medk.app.ui.chat.ConversationsScreen
import com.medk.app.ui.chat.MediaScreen
import com.medk.app.ui.components.CountBadge
import com.medk.app.ui.components.LocalToast
import com.medk.app.ui.components.MedkWordmark
import com.medk.app.ui.components.Avatar
import com.medk.app.ui.home.HomeScreen
import com.medk.app.ui.orders.OrderDetailScreen
import com.medk.app.ui.orders.OrdersScreen
import com.medk.app.ui.product.ProductScreen
import com.medk.app.ui.profile.AboutScreen
import com.medk.app.ui.profile.AccountScreen
import com.medk.app.ui.profile.FavoritesScreen
import com.medk.app.ui.profile.NotificationsScreen
import com.medk.app.ui.profile.PrivacyScreen
import com.medk.app.ui.profile.ProfileScreen
import com.medk.app.ui.profile.SettingsScreen
import com.medk.app.ui.navigation.Routes
import com.medk.app.ui.navigation.toTab
import com.medk.app.ui.theme.MedkColors
import com.medk.app.ui.theme.blueprintGrid
import com.medk.app.ui.theme.MedkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.medk.app.ui.theme.MedkShapes

@Composable
fun MedkRoot(pendingLink: MutableStateFlow<PendingLink?>) {
    val c = MedkApp.container
    val dark by c.settings.darkTheme.collectAsStateWithLifecycle(initialValue = true)
    MedkTheme(dark) {
        val auth by c.auth.state.collectAsStateWithLifecycle()
        var minSplash by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) { delay(1300); minSplash = false }
        val snack = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val toast: (String) -> Unit = { m -> scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(m) } }

        CompositionLocalProvider(LocalToast provides toast) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                val key = when {
                    minSplash || auth is AuthState.Loading -> "splash"
                    auth is AuthState.SignedIn -> "app:" + (auth as AuthState.SignedIn).profile.let { it.id + it.role }
                    else -> auth::class.simpleName ?: "x"
                }
                Crossfade(key, animationSpec = tween(450), label = "root") { k ->
                    val a = auth
                    when {
                        k == "splash" -> SplashScreen()
                        a is AuthState.NotConfigured -> NotConfiguredScreen()
                        a is AuthState.Failed -> AuthFailedScreen(a.message, { scope.launch { c.auth.restore() } },
                            { scope.launch { c.auth.signOut(c.context) } })
                        a is AuthState.SignedIn -> AppShell(a.profile, pendingLink)
                        else -> AuthNav()
                    }
                }
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 64.dp)) { d ->
                    androidx.compose.material3.Snackbar(d, shape = MedkShapes.ChamferSm, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 12.dp)
                            .border(1.dp, MedkColors.Violet.copy(.5f), MedkShapes.ChamferSm))
                }
            }
        }
    }
}

private fun AnimatedContentTransitionScope<*>.slideIn() =
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(280)) + fadeIn(tween(280))
private fun AnimatedContentTransitionScope<*>.slideOut() =
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(280)) + fadeOut(tween(200))

@Composable
private fun AuthNav() {
    val nav = rememberNavController()
    NavHost(nav, "welcome", enterTransition = { fadeIn(tween(250)) }, exitTransition = { fadeOut(tween(200)) }) {
        composable("welcome") { WelcomeScreen({ nav.navigate("login") }, { nav.navigate("register") }) }
        composable("login") {
            LoginScreen(onBack = { nav.popBackStack() }, onRegister = { nav.navigate("register") { popUpTo("welcome") } },
                onForgot = { nav.navigate("forgot") })
        }
        composable("register") { RegisterScreen(onBack = { nav.popBackStack() }, onLogin = { nav.navigate("login") { popUpTo("welcome") } }) }
        composable("forgot") { ForgotPasswordScreen(onBack = { nav.popBackStack() }) }
    }
}

/** Opens the customer-support conversation. */
fun openSupport(nav: NavHostController, scope: kotlinx.coroutines.CoroutineScope, toast: (String) -> Unit) {
    scope.launch {
        try { nav.navigate(Routes.chat(MedkApp.container.chat.start(null, null, "الدعم الفني"))) }
        catch (e: Exception) { toast(e.friendly()) }
    }
}

@Composable
private fun AppShell(profile: Profile, pendingLink: MutableStateFlow<PendingLink?>) {
    val c = MedkApp.container
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val ctx = LocalContext.current
    val isTeam = profile.roleEnum != Role.CUSTOMER
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val cartCount by c.cart.count.collectAsStateWithLifecycle(0)
    val unread by c.notifications.unread.collectAsStateWithLifecycle()

    // Android 13+: ask once for notification permission
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        runCatching { c.catalog.loadFavorites() }
    }
    val link by pendingLink.collectAsStateWithLifecycle()
    LaunchedEffect(link) {
        link?.let { l ->
            l.conversationId?.let { nav.navigate(Routes.chat(it)) } ?: l.orderId?.let { nav.navigate(Routes.order(it)) }
            pendingLink.value = null
        }
    }
    val openDrawer: () -> Unit = { scope.launch { drawer.open() } }
    fun go(r: String) { scope.launch { drawer.close() }; if (r in Routes.tabs || r == Routes.WORKSPACE) nav.toTab(r) else nav.navigate(r) }

    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow, drawerShape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp), modifier = Modifier.width(290.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                Column(Modifier.fillMaxWidth().clip(MedkShapes.Chamfer).background(MedkColors.PrimaryGradient)
                    .blueprintGrid(Color.White, 16f, .08f).padding(14.dp)) {
                    Avatar(profile.displayName, profile.avatarUrl, 42.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(profile.displayName, color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text(profile.email ?: "", color = Color.White.copy(.8f), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                if (isTeam) DrawerItem("لوحة التحكم", Icons.Filled.Dashboard, route == Routes.WORKSPACE) { go(Routes.WORKSPACE) }
                DrawerItem("الرئيسية", Icons.Filled.Home, route == Routes.HOME) { go(Routes.HOME) }
                DrawerItem("جميع الأقسام", Icons.Filled.GridView, route == Routes.CATEGORIES) { go(Routes.CATEGORIES) }
                DrawerItem("سلة التسوق", Icons.Filled.ShoppingCart, route == Routes.CART, cartCount) { go(Routes.CART) }
                DrawerItem("محادثاتي", Icons.AutoMirrored.Filled.Chat, route == Routes.CONVERSATIONS) { go(Routes.CONVERSATIONS) }
                DrawerItem("طلباتي", Icons.AutoMirrored.Filled.ReceiptLong, route == Routes.ORDERS) { go(Routes.ORDERS) }
                DrawerItem("الإشعارات", Icons.Filled.Notifications, route == Routes.NOTIFICATIONS, unread) { go(Routes.NOTIFICATIONS) }
                DrawerItem("خدمة العملاء", Icons.Filled.SupportAgent, false) {
                    scope.launch { drawer.close() }; openSupport(nav, scope, toast)
                }
                DrawerItem("الإعدادات", Icons.Filled.Settings, route == Routes.SETTINGS) { go(Routes.SETTINGS) }
                DrawerItem("حول MED-K", Icons.Filled.Info, route == Routes.ABOUT) { go(Routes.ABOUT) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                DrawerItem("تسجيل الخروج", Icons.AutoMirrored.Filled.Logout, false, tint = MedkColors.Danger) {
                    scope.launch { drawer.close(); c.auth.signOut(ctx) }
                }
                Spacer(Modifier.height(16.dp))
                MedkWordmark(subtitle = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { if (route in Routes.tabs) BottomBar(route, cartCount) { nav.toTab(it) } },
        ) { pad ->
            NavHost(
                nav, if (isTeam) Routes.WORKSPACE else Routes.HOME, Modifier.padding(bottom = pad.calculateBottomPadding()),
                enterTransition = { slideIn() }, exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(250)) }, popExitTransition = { slideOut() },
            ) {
                composable(Routes.HOME) { HomeScreen(nav, openDrawer, unread) }
                composable(Routes.CATEGORIES) { CategoriesScreen(nav, openDrawer) }
                composable(Routes.CART) { CartScreen(nav, openDrawer) }
                composable(Routes.CONVERSATIONS) { ConversationsScreen(nav, openDrawer) }
                composable(Routes.PROFILE) { ProfileScreen(nav, openDrawer) }
                composable(Routes.SEARCH) { SearchScreen(nav) }
                composable(Routes.ORDERS) { OrdersScreen(nav) }
                composable(Routes.FAVORITES) { FavoritesScreen(nav) }
                composable(Routes.NOTIFICATIONS) { NotificationsScreen(nav) }
                composable(Routes.SETTINGS) { SettingsScreen(nav) }
                composable(Routes.ACCOUNT) { AccountScreen(nav) }
                composable(Routes.ABOUT) { AboutScreen(nav) }
                composable(Routes.PRIVACY) { PrivacyScreen(nav) }
                composable(Routes.CATEGORY) { CategoryScreen(it.arguments?.getString("slug").orEmpty(), nav) }
                composable(Routes.SECTION) { SectionScreen(it.arguments?.getString("id").orEmpty(), nav) }
                composable(Routes.PRODUCT) { ProductScreen(it.arguments?.getString("id").orEmpty(), nav) }
                composable(Routes.CHAT) { ChatScreen(it.arguments?.getString("id").orEmpty(), nav) }
                composable(Routes.ORDER) { OrderDetailScreen(it.arguments?.getString("id").orEmpty(), nav) }
                composable(Routes.MEDIA, listOf(navArgument("url") { type = NavType.StringType; defaultValue = "" },
                    navArgument("type") { type = NavType.StringType; defaultValue = "image" })) {
                    MediaScreen(it.arguments?.getString("url").orEmpty(), it.arguments?.getString("type").orEmpty(), nav)
                }
                composable(Routes.WORKSPACE) { WorkspaceScreen(nav, openDrawer) }
                composable(Routes.ADMIN_ORDERS, listOf(navArgument("status") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                    AdminOrdersScreen(nav, it.arguments?.getString("status"))
                }
                composable(Routes.ADMIN_PRODUCTS) { AdminProductsScreen(nav) }
                composable(Routes.PRODUCT_EDIT, listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                    ProductEditScreen(it.arguments?.getString("id"), nav)
                }
                composable(Routes.ADMIN_CATEGORIES) { AdminCategoriesScreen(nav) }
                composable(Routes.ADMIN_SECTIONS) { AdminSectionsScreen(nav) }
                composable(Routes.ADMIN_STAFF) { AdminStaffScreen(nav) }
                composable(Routes.STAFF_DETAIL) { StaffDetailScreen(it.arguments?.getString("id").orEmpty(), nav) }
                composable(Routes.ADMIN_BANNERS) { AdminBannersScreen(nav) }
                composable(Routes.ADMIN_P2P) { AdminP2pScreen(nav) }
            }
        }
    }
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, selected: Boolean, badge: Int = 0, tint: Color? = null, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, style = MaterialTheme.typography.labelLarge, color = tint ?: Color.Unspecified) },
        icon = { Icon(icon, null, Modifier.size(19.dp), tint = tint ?: if (selected) MedkColors.Violet else MaterialTheme.colorScheme.onSurfaceVariant) },
        badge = { CountBadge(badge) }, selected = selected, onClick = onClick,
        shape = MedkShapes.ChamferSm,
        colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = MedkColors.Purple.copy(alpha = .18f)),
        modifier = Modifier.padding(vertical = 1.dp).height(44.dp),
    )
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
private fun BottomBar(current: String?, cartCount: Int, onSelect: (String) -> Unit) {
    val tabs = listOf(
        Tab(Routes.HOME, "الرئيسية", Icons.Outlined.Home, Icons.Filled.Home),
        Tab(Routes.CATEGORIES, "الأقسام", Icons.Outlined.GridView, Icons.Filled.GridView),
        Tab(Routes.CART, "السلة", Icons.Outlined.ShoppingCart, Icons.Filled.ShoppingCart),
        Tab(Routes.CONVERSATIONS, "محادثاتي", Icons.Outlined.ChatBubbleOutline, Icons.AutoMirrored.Filled.Chat),
        Tab(Routes.PROFILE, "حسابي", Icons.Outlined.Person, Icons.Filled.Person),
    )
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Row(Modifier.fillMaxWidth().height(56.dp)) {
            tabs.forEach { t ->
                val sel = current == t.route
                Column(Modifier.weight(1f).fillMaxSize().clickable { onSelect(t.route) },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(if (sel) 26.dp else 0.dp).height(2.dp).background(MedkColors.PrimaryGradient))
                    Spacer(Modifier.height(7.dp))
                    Box {
                        Icon(if (sel) t.selectedIcon else t.icon, t.label,
                            tint = if (sel) MedkColors.Violet else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
                        if (t.route == Routes.CART) CountBadge(cartCount, Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-5).dp))
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(t.label, style = MaterialTheme.typography.labelSmall,
                        color = if (sel) MedkColors.Violet else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
