package com.medk.app.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.medk.app.MedkApp
import com.medk.app.core.UiState
import com.medk.app.core.friendly
import com.medk.app.data.Category
import com.medk.app.data.Product
import com.medk.app.data.Section
import com.medk.app.ui.components.CategoryArt
import com.medk.app.ui.components.EmptyBox
import com.medk.app.ui.components.LocalToast
import com.medk.app.ui.components.MedkTextField
import com.medk.app.ui.components.MedkTopBar
import com.medk.app.ui.components.NetImage
import com.medk.app.ui.components.ProductCard
import com.medk.app.ui.components.SecondaryButton
import com.medk.app.ui.components.StateView
import com.medk.app.ui.components.categoryIcon
import com.medk.app.ui.components.loadVm
import com.medk.app.ui.navigation.Routes
import com.medk.app.ui.theme.CategoryPalette
import com.medk.app.ui.theme.categoryPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================================ all categories
@Composable
fun CategoriesScreen(nav: NavHostController, onMenu: () -> Unit) {
    val c = MedkApp.container
    val vm = loadVm("categories") { c.catalog.categories(force = true).filter { it.isActive } to c.catalog.allSections(force = true) }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("الأقسام", subtitle = "جميع الأقسام المتاحة",
            navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "القائمة") } },
            actions = { IconButton(onClick = { nav.navigate(Routes.SEARCH) }) { Icon(Icons.Filled.Search, "بحث") } })
        StateView(state, { vm.load() }) { (cats, sections) ->
            LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(cats, key = { it.id }) { cat ->
                    CategoryTile(cat, sections.count { it.categoryId == cat.id && it.isActive }) { nav.navigate(Routes.category(cat.slug)) }
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(cat: Category, sectionCount: Int, onClick: () -> Unit) {
    val p = categoryPalette(cat.colorKey ?: cat.slug)
    Column(Modifier.fillMaxWidth().aspectRatio(.82f).clip(RoundedCornerShape(22.dp)).background(p.brush)
        .border(1.dp, Color.White.copy(.12f), RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(14.dp)) {
        CategoryArt(cat.iconKey ?: cat.slug, p, 74.dp)
        Spacer(Modifier.weight(1f))
        Text(cat.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(if (sectionCount > 0) "$sectionCount أقسام فرعية" else (cat.description ?: ""), color = Color.White.copy(.8f),
            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ============================================================ category router
data class CategoryData(val category: Category, val sections: List<Section>, val products: List<Product>)

@Composable
fun CategoryScreen(slug: String, nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("category-$slug") {
        val cat = c.catalog.category(slug) ?: error("القسم غير موجود")
        val sections = c.catalog.sections(cat.id, force = true).filter { it.isActive }
        val products = if (slug == "p2p") emptyList() else c.catalog.productsByCategory(cat.id)
        CategoryData(cat, sections, products)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val title = (state as? UiState.Success)?.data?.category?.name ?: ""
    if (slug == "p2p") { P2pScreen(nav, state, { vm.load() }); return }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar(title, onBack = { nav.popBackStack() },
            actions = { IconButton(onClick = { nav.navigate(Routes.SEARCH) }) { Icon(Icons.Filled.Search, "بحث") } })
        StateView(state, { vm.load() }) { data ->
            when (slug) {
                "gaming", "subscriptions" -> SectionGridCategory(data, nav, tall = slug == "gaming")
                else -> ProductsCategory(data, nav)
            }
        }
    }
}

@Composable
private fun CategoryHeader(cat: Category, p: CategoryPalette, extra: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.horizontal).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CategoryArt(cat.iconKey ?: cat.slug, p, 76.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(cat.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
            if (!cat.description.isNullOrBlank()) Text(cat.description, color = Color.White.copy(.85f), style = MaterialTheme.typography.bodySmall)
            Text(extra, color = Color.White.copy(.7f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Gaming & subscriptions: pick a game / app first. */
@Composable
private fun SectionGridCategory(data: CategoryData, nav: NavHostController, tall: Boolean) {
    val p = categoryPalette(data.category.colorKey ?: data.category.slug)
    LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(2) }) {
            CategoryHeader(data.category, p, "${data.products.count { it.inStock }} منتج متوفر")
        }
        item(span = { GridItemSpan(2) }) {
            Text(if (tall) "اختر اللعبة" else "اختر التطبيق", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
        }
        if (data.sections.isEmpty()) item(span = { GridItemSpan(2) }) { EmptyBox(Icons.Filled.Inventory2, "لا توجد أقسام بعد") }
        items(data.sections, key = { it.id }) { s ->
            SectionTile(s, data.products.count { it.sectionId == s.id && it.inStock }, p, data.category.iconKey, tall) {
                nav.navigate(Routes.section(s.id))
            }
        }
    }
}

@Composable
fun SectionTile(s: Section, count: Int, p: CategoryPalette, iconKey: String?, tall: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (tall) 1f else 1.4f)) {
            NetImage(s.imageUrl, Modifier.fillMaxSize(), palette = p, placeholderIcon = categoryIcon(iconKey))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.55f)))))
            Text(s.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (count > 0) "$count منتج" else "قريباً", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = p.accent)
        }
    }
}

/** Accounts & other services: products with section filter chips. */
@Composable
private fun ProductsCategory(data: CategoryData, nav: NavHostController) {
    val p = categoryPalette(data.category.colorKey ?: data.category.slug)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val list = if (selected == null) data.products else data.products.filter { it.sectionId == selected }
    ProductGrid(list, nav, p, header = {
        item(span = { GridItemSpan(2) }) { CategoryHeader(data.category, p, "${data.products.count { it.inStock }} منتج متوفر") }
        if (data.sections.isNotEmpty()) item(span = { GridItemSpan(2) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("الكل", selected == null) { selected = null } }
                items(data.sections, key = { it.id }) { s -> Chip(s.name, selected == s.id) { selected = s.id } }
            }
        }
    }, emptyText = "لا توجد منتجات متاحة في هذا القسم حالياً", sectionNames = data.sections.associate { it.id to it.name })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected, onClick, label = { Text(label) }, shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = com.medk.app.ui.theme.MedkColors.Purple,
            selectedLabelColor = Color.White, containerColor = MaterialTheme.colorScheme.surfaceContainer))
}

/** Reusable product grid with add-to-cart & favorites. */
@Composable
fun ProductGrid(
    products: List<Product>, nav: NavHostController, palette: CategoryPalette? = null,
    header: LazyGridScope.() -> Unit = {}, emptyText: String = "لا توجد منتجات", sectionNames: Map<String, String> = emptyMap(),
) {
    val c = MedkApp.container
    val favs by c.catalog.favoriteIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    LazyVerticalGrid(GridCells.Adaptive(160.dp), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        header()
        if (products.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyBox(Icons.Filled.Inventory2, emptyText) }
        items(products, key = { it.id }) { p ->
            ProductCard(p, { nav.navigate(Routes.product(p.id)) }, onAdd = {
                scope.launch { c.cart.add(p, 1, p.sectionId?.let { sectionNames[it] }); toast("تمت الإضافة إلى السلة") }
            }, isFavorite = p.id in favs, palette = palette, onFavorite = {
                scope.launch { try { c.catalog.toggleFavorite(p.id) } catch (e: Exception) { toast(e.friendly()) } }
            })
        }
    }
}

// ============================================================ section (game / app)
@Composable
fun SectionScreen(id: String, nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("section-$id") {
        val s = c.catalog.section(id) ?: error("القسم غير موجود")
        val cat = c.catalog.categories().firstOrNull { it.id == s.categoryId }
        Triple(s, cat, c.catalog.productsBySection(id))
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar((state as? UiState.Success)?.data?.first?.name ?: "", onBack = { nav.popBackStack() })
        StateView(state, { vm.load() }) { (section, cat, products) ->
            val p = categoryPalette(cat?.colorKey ?: cat?.slug)
            @OptIn(ExperimentalMaterial3Api::class)
            PullToRefreshBox(refreshing, { vm.load(silent = true) }) {
                ProductGrid(products, nav, p, header = {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.horizontal).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))) {
                                NetImage(section.imageUrl, Modifier.fillMaxSize(), palette = p, placeholderIcon = categoryIcon(cat?.iconKey))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(section.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
                                Text(section.description ?: cat?.name.orEmpty(), color = Color.White.copy(.8f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SecondaryButton("تواصل مع البائع", {
                            scope.launch {
                                try { nav.navigate(Routes.chat(c.chat.start(null, section.id, "استفسار — ${section.name}"))) }
                                catch (e: Exception) { toast(e.friendly()) }
                            }
                        }, icon = Icons.Filled.SupportAgent, height = 46.dp)
                    }
                }, emptyText = "لا توجد منتجات متاحة لـ ${section.name} حالياً", sectionNames = mapOf(section.id to section.name))
            }
        }
    }
}

// ============================================================ search
@Composable
fun SearchScreen(nav: NavHostController) {
    val c = MedkApp.container
    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<UiState<List<Product>>?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { state = null; return@LaunchedEffect }
        delay(350)
        state = UiState.Loading
        state = try { UiState.Success(c.catalog.search(query)) } catch (e: Exception) { UiState.Error(e.friendly()) }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("البحث", onBack = { nav.popBackStack() })
        MedkTextField(query, { query = it }, "ابحث داخل المتجر", Modifier.padding(horizontal = 16.dp).focusRequester(focus),
            leading = Icons.Filled.Search, trailing = if (query.isNotEmpty()) { { IconButton({ query = "" }) { Icon(Icons.Filled.Close, "مسح") } } } else null)
        when (val s = state) {
            null -> EmptyBox(Icons.Filled.Search, "ابحث عن منتج", "اكتب اسم اللعبة أو التطبيق أو الخدمة (حرفان على الأقل)")
            is UiState.Loading -> com.medk.app.ui.components.LoadingBox()
            is UiState.Error -> com.medk.app.ui.components.ErrorBox(s.message, null)
            is UiState.Success -> if (s.data.isEmpty()) EmptyBox(Icons.Filled.SearchOff, "لا توجد نتائج", "جرّب كلمات بحث أخرى")
                else ProductGrid(s.data, nav)
        }
    }
}

@Suppress("unused") private val keep = CircleShape
