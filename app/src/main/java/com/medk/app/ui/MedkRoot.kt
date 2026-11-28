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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.foundation.layout.WindowInsets
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.haze
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
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
import com.medk.app.BuildConfig
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
                        k == "splash" -> SplashScreen { minSplash = false }
                        a is AuthState.NotConfigured -> NotConfiguredScreen()
                        a is AuthState.VerifyEmail -> com.medk.app.ui.auth.VerifyEmailScreen(a.email)
                        a is AuthState.Failed -> AuthFailedScreen(a.message, { scope.launch { c.auth.restore() } },
                            { scope.launch { c.auth.signOut(c.context) } })
                        a is AuthState.SignedIn && BuildConfig.ADMIN_APP ->
                            if (a.profile.roleEnum == Role.OWNER) com.medk.app.ui.admin.OwnerAppShell(a.profile, pendingLink)
                            else com.medk.app.ui.admin.OwnerOnlyScreen(a.profile) { scope.launch { c.auth.signOut(c.context) } }
                        a is AuthState.SignedIn -> AppShell(a.profile, pendingLink)
                        else -> AuthNav()
                    }
                }
                // Strict online mode + daily in-app updates.
                val online by c.network.online.collectAsStateWithLifecycle()
                com.medk.app.ui.system.UpdatePrompt()
                if (com.medk.app.core.AppFlags.strictNetwork && !online) com.medk.app.ui.system.OfflineScreen { c.network.refresh() }
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
    NavHost(nav, if (BuildConfig.ADMIN_APP) "login" else "welcome", enterTransition = { fadeIn(tween(250)) }, exitTransition = { fadeOut(tween(200)) }) {
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
    // In the store app only sellers get the workspace; the owner uses the separate MED-K Admin app.
    val isTeam = profile.roleEnum == Role.STAFF
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
                Column(Modifier.fillMaxWidth().clip(MedkShapes.Chamfer).background(MedkColors.PrimaryGradient).padding(14.dp)) {
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
        val hazeState = remember { dev.chrisbanes.haze.HazeState() }
        val showBar = route in Routes.tabs
        val inset = glassBarInset()
        CompositionLocalProvider(
            com.medk.app.ui.components.LocalHazeState provides hazeState,
            com.medk.app.ui.components.LocalBottomBarInset provides if (showBar) inset else 0.dp,
        ) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            NavHost(
                nav, if (isTeam) Routes.WORKSPACE else Routes.HOME,
                Modifier.fillMaxSize().haze(hazeState).let { if (showBar) it else it.navigationBarsPadding() },
                enterTransition = { slideIn() }, exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(250)) }, popExitTransition = { slideOut() },
            ) {
                composable(Routes.HOME) { HomeScreen(nav, openDrawer, unread) }
                composable(Routes.CATEGORIES) { CategoriesScreen(nav, openDrawer) }
                composable(Routes.CART) { CartScreen(nav, openDrawer) }
                composable(Routes.CONVERSATIONS) { ConversationsScreen(nav, openDrawer) }
                composable(Routes.PROFILE) { ProfileScreen(nav, openDrawer) }
                composable(Routes.WORKSPACE) { WorkspaceScreen(nav, openDrawer) }
                composable(Routes.ADMIN_ORDERS, listOf(navArgument("status") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                    AdminOrdersScreen(nav, it.arguments?.getString("status"))
                }
                composable(Routes.ADMIN_PRODUCTS) { AdminProductsScreen(nav) }
                detailRoutes(nav)
            }
            if (showBar) Box(Modifier.align(Alignment.BottomCenter)) { BottomBar(route, cartCount) { nav.toTab(it) } }
        } }
    }
}

/** Detail screens shared by the store app and the owner app. */
fun androidx.navigation.NavGraphBuilder.detailRoutes(nav: NavHostController) {
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
    composable(Routes.PRODUCT_EDIT, listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })) {
        ProductEditScreen(it.arguments?.getString("id"), nav)
    }
    composable(Routes.ADMIN_CATEGORIES) { AdminCategoriesScreen(nav) }
    composable(Routes.ADMIN_SECTIONS) { AdminSectionsScreen(nav) }
    composable(Routes.ADMIN_STAFF) { AdminStaffScreen(nav) }
    composable(Routes.STAFF_DETAIL) { StaffDetailScreen(it.arguments?.getString("id").orEmpty(), nav) }
    composable(Routes.ADMIN_BANNERS) { AdminBannersScreen(nav) }
    composable(Routes.ADMIN_P2P) { AdminP2pScreen(nav) }
    composable(com.medk.app.ui.admin.Db.ROOT) { com.medk.app.ui.admin.DatabaseScreen(nav) }
    composable(com.medk.app.ui.admin.Db.COLLECTION, arguments = listOf(androidx.navigation.navArgument("path") { defaultValue = "" })) {
        com.medk.app.ui.admin.DatabaseCollectionScreen(it.arguments?.getString("path") ?: "", nav)
    }
    composable(com.medk.app.ui.admin.Db.DOC, arguments = listOf(androidx.navigation.navArgument("path") { defaultValue = "" })) {
        com.medk.app.ui.admin.DatabaseDocScreen(it.arguments?.getString("path") ?: "", nav)
    }
    composable(Routes.WALLET) { com.medk.app.ui.payments.WalletScreen(nav) }
    composable(Routes.ADMIN_PAYMENTS) { com.medk.app.ui.payments.AdminPaymentsScreen(nav) }
    composable(Routes.ADMIN_CUSTOMERS) { com.medk.app.ui.admin.AdminCustomersScreen(nav) }
    composable(Routes.CUSTOMER_DETAIL) { com.medk.app.ui.admin.CustomerDetailScreen(it.arguments?.getString("id").orEmpty(), nav) }
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

data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
private fun BottomBar(current: String?, cartCount: Int, onSelect: (String) -> Unit) {
    val tabs = listOf(
        Tab(Routes.HOME, "الرئيسية", Icons.Outlined.Home, Icons.Filled.Home),
        Tab(Routes.CATEGORIES, "الأقسام", Icons.Outlined.GridView, Icons.Filled.GridView),
        Tab(Routes.CART, "السلة", Icons.Outlined.ShoppingCart, Icons.Filled.ShoppingCart),
        Tab(Routes.CONVERSATIONS, "محادثاتي", Icons.Outlined.ChatBubbleOutline, Icons.AutoMirrored.Filled.Chat),
        Tab(Routes.PROFILE, "حسابي", Icons.Outlined.Person, Icons.Filled.Person),
    )
    ClassicNavBar(tabs, current, mapOf(Routes.CART to cartCount), onSelect)
}

/** Floating, pill-shaped frosted-glass bottom bar (shared by the store and owner apps). */
@Composable
fun ClassicNavBar(tabs: List<Tab>, current: String?, badges: Map<String, Int>, onSelect: (String) -> Unit) {
    val dark = com.medk.app.ui.theme.LocalIsDark.current
    val haze = com.medk.app.ui.components.LocalHazeState.current
    val pill = RoundedCornerShape(50)
    val glass = dev.chrisbanes.haze.HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.background,
        tint = dev.chrisbanes.haze.HazeTint(if (dark) Color.White.copy(alpha = .07f) else Color.White.copy(alpha = .40f)),
        blurRadius = 24.dp, noiseFactor = .04f,
        fallbackTint = dev.chrisbanes.haze.HazeTint(if (dark) Color(0xFF221D30).copy(alpha = .80f) else Color.White.copy(alpha = .80f)),
    )
    val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .12f else .50f), Color.White.copy(alpha = .02f)))
    val rim = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .35f else .95f), Color.White.copy(alpha = if (dark) .06f else .35f)))
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).clip(pill)
                .let { m -> if (haze != null) m.then(Modifier.hazeChild(haze, glass)) else m.background(glass.fallbackTint!!.color) }
                .background(sheen).border(1.dp, rim, pill)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { t ->
                val sel = current == t.route
                val bg by androidx.compose.animation.animateColorAsState(
                    if (sel) MedkColors.Purple.copy(alpha = if (dark) .45f else .18f) else Color.Transparent, label = "tabBg")
                val tint by androidx.compose.animation.animateColorAsState(
                    if (sel) (if (dark) Color.White else MedkColors.Purple) else MaterialTheme.colorScheme.onSurface.copy(alpha = .72f), label = "tabTint")
                Column(
                    Modifier.weight(1f).height(52.dp).clip(pill).background(bg)
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null) { onSelect(t.route) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Box {
                        Icon(if (sel) t.selectedIcon else t.icon, t.label, tint = tint, modifier = Modifier.size(22.dp))
                        CountBadge(badges[t.route] ?: 0, Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-5).dp))
                    }
                    Text(t.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                }
            }
        }
    }
}

/** Total vertical space the floating bar covers (bar + margins + system navigation bar). */
@Composable
fun glassBarInset(): androidx.compose.ui.unit.Dp =
    84.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
