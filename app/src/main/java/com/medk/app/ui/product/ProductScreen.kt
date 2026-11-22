package com.medk.app.ui.product

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.medk.app.MedkApp
import com.medk.app.core.UiState
import com.medk.app.core.friendly
import com.medk.app.ui.components.CountBadge
import com.medk.app.ui.components.LocalToast
import com.medk.app.ui.components.MedkTopBar
import com.medk.app.ui.components.NetImage
import com.medk.app.ui.components.Pill
import com.medk.app.ui.components.PriceBlock
import com.medk.app.ui.components.PrimaryButton
import com.medk.app.ui.components.QuantitySelector
import com.medk.app.ui.components.SecondaryButton
import com.medk.app.ui.components.StateView
import com.medk.app.ui.components.categoryIcon
import com.medk.app.ui.components.loadVm
import com.medk.app.ui.navigation.Routes
import com.medk.app.ui.navigation.toTab
import com.medk.app.ui.theme.MedkColors
import com.medk.app.ui.theme.categoryPalette
import kotlinx.coroutines.launch
import com.medk.app.ui.theme.MedkShapes

@Composable
fun ProductScreen(id: String, nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("product-$id") {
        val p = c.catalog.product(id)
        val section = p.sectionId?.let { c.catalog.section(it) }
        val cat = c.catalog.categories().firstOrNull { it.id == p.categoryId }
        Triple(p, section, cat)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val favs by c.catalog.favoriteIds.collectAsStateWithLifecycle()
    val cartCount by c.cart.count.collectAsStateWithLifecycle(0)
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    var qty by rememberSaveable { mutableIntStateOf(1) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar((state as? UiState.Success)?.data?.first?.name ?: "", onBack = { nav.popBackStack() }, actions = {
            Box {
                IconButton({ nav.toTab(Routes.CART) }) { Icon(Icons.Filled.ShoppingCart, "السلة") }
                CountBadge(cartCount, Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
        })
        StateView(state, { vm.load() }) { (product, section, cat) ->
            val palette = categoryPalette(cat?.colorKey ?: cat?.slug)
            val images = product.images.ifEmpty { listOf("") }
            val pager = rememberPagerState { images.size }
            val maxQty = product.stock ?: 99
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().aspectRatio(1.55f).clip(MedkShapes.Chamfer)) {
                    HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                        NetImage(images[i].ifBlank { null }, Modifier.fillMaxSize().clickable(enabled = images[i].isNotBlank()) {
                            nav.navigate(Routes.media(images[i], "image"))
                        }, palette = palette, placeholderIcon = categoryIcon(cat?.iconKey))
                    }
                    Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp).clip(com.medk.app.ui.theme.MedkShapes.Hex).background(Color.Black.copy(.55f))
                        .clickable { scope.launch { try { c.catalog.toggleFavorite(product.id) } catch (e: Exception) { toast(e.friendly()) } } },
                        contentAlignment = Alignment.Center) {
                        val fav = product.id in favs
                        Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "المفضلة",
                            tint = if (fav) MedkColors.Violet else Color.White, modifier = Modifier.size(16.dp))
                    }
                    if (images.size > 1) Row(Modifier.align(Alignment.BottomCenter).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(images.size) { i ->
                            Box(Modifier.height(7.dp).width(if (pager.currentPage == i) 20.dp else 7.dp).clip(CircleShape)
                                .background(if (pager.currentPage == i) Color.White else Color.White.copy(.45f)))
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (cat != null) Pill(cat.name, palette.accent)
                        if (section != null) Pill(section.name, MedkColors.Violet)
                    }
                    Text(product.name, style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PriceBlock(product.priceUsd, product.priceDzd, Modifier.weight(1f), large = true)
                        if (product.inStock) Pill(product.stock?.let { "متوفر ($it)" } ?: "متوفر", MedkColors.Success, icon = Icons.Filled.CheckCircle)
                        else Pill("غير متوفر", MedkColors.Danger, icon = Icons.Filled.Cancel)
                    }
                    if (!product.description.isNullOrBlank()) {
                        com.medk.app.ui.components.SectionHeader("الوصف", index = 1)
                        Text(product.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (product.inStock) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الكمية", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        QuantitySelector(qty, { qty = it.coerceIn(1, maxQty) }, max = maxQty)
                    }
                    if (product.inStock) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الإجمالي", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        PriceBlock(product.priceUsd * qty, product.priceDzd * qty)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Inventory2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text("  يتم التسليم عبر المحادثة مع البائع بعد تأكيد الدفع.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            com.medk.app.ui.components.TechRule()
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(if (product.inStock) "أضف إلى السلة" else "غير متوفر حالياً", {
                    scope.launch { c.cart.add(product, qty, section?.name); toast("تمت إضافة ${product.name} إلى السلة") }
                }, enabled = product.inStock, icon = Icons.Filled.AddShoppingCart)
                SecondaryButton("تواصل مع البائع", {
                    scope.launch {
                        try { nav.navigate(Routes.chat(c.chat.start(product.id, null, product.name))) } catch (e: Exception) { toast(e.friendly()) }
                    }
                }, icon = Icons.Filled.SupportAgent, height = 40.dp)
            }
        }
    }
}
