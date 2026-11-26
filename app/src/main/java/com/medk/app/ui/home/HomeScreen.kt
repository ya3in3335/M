package com.medk.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.medk.app.MedkApp
import com.medk.app.core.UiState
import com.medk.app.data.Banner
import com.medk.app.data.Category
import com.medk.app.data.Product
import com.medk.app.ui.components.CategoryCard
import com.medk.app.ui.components.CountBadge
import com.medk.app.ui.components.ErrorBox
import com.medk.app.ui.components.LocalToast
import com.medk.app.ui.components.MedkMark
import com.medk.app.ui.components.MedkWordmark
import com.medk.app.ui.components.NetImage
import com.medk.app.ui.components.ProductCard
import com.medk.app.ui.components.SectionHeader
import com.medk.app.ui.components.Shimmer
import com.medk.app.ui.components.categoryIcon
import com.medk.app.ui.components.loadVm
import com.medk.app.ui.navigation.Routes
import com.medk.app.ui.navigation.toTab
import com.medk.app.ui.theme.MedkColors
import com.medk.app.ui.components.EmblemIcon
import androidx.compose.ui.unit.sp
import com.medk.app.ui.theme.categoryPalette
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.medk.app.ui.theme.MedkShapes

data class HomeData(val categories: List<Category>, val banners: List<Banner>, val latest: List<Product>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController, onMenu: () -> Unit, unread: Int) {
    val c = MedkApp.container
    val vm = loadVm("home") {
        coroutineScope {
            val cats = async { c.catalog.categories(force = true) }
            val banners = async { runCatching { c.catalog.banners() }.getOrDefault(emptyList()) }
            val latest = async { runCatching { c.catalog.latestProducts() }.getOrDefault(emptyList()) }
            HomeData(cats.await().filter { it.isActive }, banners.await(), latest.await())
        }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val favs by c.catalog.favoriteIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // header
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "القائمة", modifier = Modifier.size(22.dp)) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MedkWordmark(20.sp)
                    if (com.medk.app.BuildConfig.DEMO) com.medk.app.ui.components.Pill("DEMO", MedkColors.Warning, Modifier.padding(start = 6.dp))
                }
            }
            Box {
                IconButton(onClick = { nav.navigate(Routes.NOTIFICATIONS) }) {
                    Icon(Icons.Outlined.Notifications, "الإشعارات", modifier = Modifier.size(22.dp))
                }
                CountBadge(unread, Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 8.dp))
            }
        }
        // search bar
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().height(42.dp).clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .clickable { nav.navigate(Routes.SEARCH) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("ابحث عن لعبة، اشتراك أو خدمة…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f))
        }

        PullToRefreshBox(refreshing, onRefresh = { vm.load(silent = true) }, modifier = Modifier.fillMaxSize()) {
            when (val s = state) {
                is UiState.Loading -> Column(Modifier.padding(16.dp)) {
                    Shimmer(Modifier.fillMaxWidth().height(132.dp)); Spacer(Modifier.height(16.dp))
                    repeat(5) { Shimmer(Modifier.fillMaxWidth().height(72.dp)); Spacer(Modifier.height(10.dp)) }
                }
                is UiState.Error -> ErrorBox(s.message, { vm.load() })
                is UiState.Success -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp + com.medk.app.ui.components.LocalBottomBarInset.current), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { BannerPager(s.data.banners) { slug -> if (slug != null) nav.navigate(Routes.category(slug)) else nav.toTab(Routes.CATEGORIES) } }
                    item { SectionHeader("خدماتنا الرئيسية", action = "عرض الكل", onAction = { nav.toTab(Routes.CATEGORIES) }, index = 1) }
                    items(s.data.categories.size, key = { s.data.categories[it].id }) { i ->
                        val cat = s.data.categories[i]
                        CategoryCard(cat, { nav.navigate(Routes.category(cat.slug)) }, index = i + 1)
                    }
                    if (s.data.latest.isNotEmpty()) {
                        item { SectionHeader("أحدث المنتجات", index = 2) }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(s.data.latest, key = { it.id }) { p ->
                                    ProductCard(p, { nav.navigate(Routes.product(p.id)) }, onAdd = {
                                        scope.launch { c.cart.add(p, 1, null); toast("تمت الإضافة إلى السلة") }
                                    }, modifier = Modifier.width(140.dp), isFavorite = p.id in favs,
                                        onFavorite = { scope.launch { runCatching { c.catalog.toggleFavorite(p.id) } } })
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BannerPager(banners: List<Banner>, onOpen: (String?) -> Unit) {
    val count = banners.size + 1 // brand welcome banner first
    val pager = rememberPagerState { count }
    LaunchedEffect(count) {
        if (count > 1) while (true) { delay(5000); pager.animateScrollToPage((pager.currentPage + 1) % count) }
    }
    Column {
        HorizontalPager(pager, pageSpacing = 10.dp) { page ->
            if (page == 0) WelcomeBanner { onOpen(null) } else RemoteBanner(banners[page - 1]) { onOpen(banners[page - 1].categorySlug) }
        }
        if (count > 1) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(count) { i ->
                val sel = pager.currentPage == i
                Box(Modifier.padding(horizontal = 3.dp).size(if (sel) 8.dp else 6.dp).clip(CircleShape)
                    .background(if (sel) MedkColors.Violet else MaterialTheme.colorScheme.surfaceContainerHighest))
            }
        }
    }
}

@Composable
private fun WelcomeBanner(onBrowse: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(132.dp).clip(MedkShapes.Card)
        .background(Brush.linearGradient(listOf(MedkColors.Purple, Color(0xFF4A1A99))))
        .padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("مرحباً بك في MED-K", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text("كل خدماتك الرقمية في مكان واحد", color = Color.White.copy(.85f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onBrowse)
                .padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("تصفح الخدمات", color = MedkColors.Purple, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MedkColors.Purple, modifier = Modifier.size(14.dp))
            }
        }
        Box(Modifier.size(92.dp).clip(CircleShape).background(Color.White.copy(.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ShoppingBag, null, tint = Color.White, modifier = Modifier.size(46.dp))
        }
    }
}

@Composable
private fun RemoteBanner(b: Banner, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(132.dp).clip(MedkShapes.Card).clickable(onClick = onClick)) {
        NetImage(b.imageUrl, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(.8f)))))
        Column(Modifier.align(Alignment.CenterEnd).padding(16.dp).fillMaxWidth(.6f)) {
            Text(b.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!b.subtitle.isNullOrBlank()) Text(b.subtitle, color = Color.White.copy(.85f), style = MaterialTheme.typography.labelSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
