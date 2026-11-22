package com.medk.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.medk.app.data.Category
import com.medk.app.data.OrderStatus
import com.medk.app.data.Product
import com.medk.app.ui.theme.CategoryPalette
import com.medk.app.ui.theme.MedkColors
import com.medk.app.ui.theme.MedkShapes
import com.medk.app.ui.theme.blueprintGrid
import com.medk.app.ui.theme.categoryPalette

fun categoryIcon(key: String?): ImageVector = when (key) {
    "gaming" -> Icons.Filled.SportsEsports
    "subscriptions" -> Icons.Filled.PlayCircle
    "accounts" -> Icons.Filled.AccountCircle
    "p2p" -> Icons.Filled.CurrencyBitcoin
    "other" -> Icons.Filled.Apps
    else -> Icons.Filled.CardGiftcard
}

/** Remote image with a branded placeholder. */
@Composable
fun NetImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop,
             placeholderIcon: ImageVector = Icons.Filled.Image, palette: CategoryPalette? = null) {
    val fallback: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .background(palette?.let { Brush.linearGradient(listOf(it.start.copy(.55f), it.end.copy(.25f))) }
                ?: Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHighest)))
            .blueprintGrid(Color.White, 14f, .07f),
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(42.dp).clip(MedkShapes.Hex).background(Color.White.copy(.18f)))
            Box(Modifier.size(38.dp).clip(MedkShapes.Hex).background(Color.Black.copy(.35f)), contentAlignment = Alignment.Center) {
                Icon(placeholderIcon, null, tint = Color.White.copy(alpha = .85f), modifier = Modifier.size(18.dp))
            }
        }
    }
    if (url.isNullOrBlank()) Box(modifier) { fallback() }
    else SubcomposeAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = contentScale,
        loading = { Shimmer(Modifier.fillMaxSize()) }, error = { fallback() })
}

/** Hexagon emblem for a category: outer ring + gradient core + icon. */
@Composable
fun CategoryArt(key: String?, palette: CategoryPalette, size: Dp = 46.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(MedkShapes.Hex).background(palette.accent.copy(alpha = .45f)))
        Box(Modifier.fillMaxSize(.9f).clip(MedkShapes.Hex).background(MaterialTheme.colorScheme.background))
        Box(Modifier.fillMaxSize(.74f).clip(MedkShapes.Hex).background(palette.brush), contentAlignment = Alignment.Center) {
            Icon(categoryIcon(key), null, tint = Color.White, modifier = Modifier.size(size * .32f))
        }
    }
}

/** Service card — dark technical panel tinted with the category colour. */
@Composable
fun CategoryCard(category: Category, onClick: () -> Unit, modifier: Modifier = Modifier, index: Int? = null) {
    val p = categoryPalette(category.colorKey ?: category.slug)
    val shape = MedkShapes.Chamfer
    Row(
        modifier.fillMaxWidth().height(72.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .background(Brush.horizontalGradient(listOf(p.start.copy(alpha = .22f), Color.Transparent, Color.Transparent)))
            .border(1.dp, Brush.horizontalGradient(listOf(p.accent.copy(alpha = .65f), MaterialTheme.colorScheme.outlineVariant)), shape)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(p.brush))
        Spacer(Modifier.width(11.dp))
        CategoryArt(category.iconKey ?: category.slug, p)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(category.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!category.description.isNullOrBlank()) Text(category.description, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (index != null) Text(index.toString().padStart(2, '0'), style = MaterialTheme.typography.labelMedium,
            color = p.accent.copy(alpha = .8f), modifier = Modifier.padding(horizontal = 8.dp))
        Box(Modifier.padding(end = 12.dp).size(26.dp).clip(MedkShapes.ChamferXs).background(p.accent.copy(alpha = .16f)),
            contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = p.accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ProductCard(
    product: Product, onClick: () -> Unit, onAdd: (() -> Unit)?, modifier: Modifier = Modifier,
    isFavorite: Boolean = false, onFavorite: (() -> Unit)? = null, palette: CategoryPalette? = null,
) {
    val shape = MedkShapes.Card
    Column(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.35f)) {
            NetImage(product.cover, Modifier.fillMaxSize(), palette = palette)
            if (!product.inStock) Pill("غير متوفر", MedkColors.Danger,
                Modifier.align(Alignment.TopStart).padding(6.dp).background(Color.Black.copy(.6f), MedkShapes.ChamferXs))
            else if (product.stock != null && product.stock <= 5) Pill("آخر ${product.stock}", MedkColors.Warning,
                Modifier.align(Alignment.TopStart).padding(6.dp).background(Color.Black.copy(.6f), MedkShapes.ChamferXs))
            if (onFavorite != null) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(MedkShapes.Hex)
                .background(Color.Black.copy(alpha = .55f)).clickable(onClick = onFavorite), contentAlignment = Alignment.Center) {
                Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "المفضلة",
                    tint = if (isFavorite) MedkColors.Violet else Color.White, modifier = Modifier.size(13.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Text(product.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(36.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PriceBlock(product.priceUsd, product.priceDzd, Modifier.weight(1f))
                if (onAdd != null && product.inStock) Box(Modifier.size(30.dp).clip(MedkShapes.ChamferXs)
                    .background(MedkColors.PrimaryGradient).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AddShoppingCart, "أضف إلى السلة", tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

fun statusColor(s: OrderStatus): Color = when (s) {
    OrderStatus.OPEN -> MedkColors.Warning
    OrderStatus.PAID -> MedkColors.Info
    OrderStatus.DELIVERED -> MedkColors.Success
    OrderStatus.CLOSED -> Color(0xFF8E8A99)
}

@Composable
fun StatusChip(status: OrderStatus, modifier: Modifier = Modifier) = Pill(status.label, statusColor(status), modifier)

/** Horizontal progress stepper with hexagon nodes: open → paid → delivered → closed. */
@Composable
fun OrderProgress(status: OrderStatus, modifier: Modifier = Modifier) {
    val steps = OrderStatus.entries
    val current = steps.indexOf(status)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        steps.forEachIndexed { i, s ->
            val done = i <= current
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(2.dp).background(
                        if (i == 0) Color.Transparent else if (done) MedkColors.Violet else MaterialTheme.colorScheme.outline))
                    Box(Modifier.size(24.dp).clip(MedkShapes.Hex).background(
                        if (done) MedkColors.PrimaryGradient else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest)),
                        contentAlignment = Alignment.Center) {
                        if (done) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        else Text("${i + 1}", style = MaterialTheme.typography.labelSmall)
                    }
                    Box(Modifier.weight(1f).height(2.dp).background(
                        if (i == steps.lastIndex) Color.Transparent else if (i < current) MedkColors.Violet else MaterialTheme.colorScheme.outline))
                }
                Spacer(Modifier.height(4.dp))
                Text(s.label, style = MaterialTheme.typography.labelSmall,
                    color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun Avatar(name: String?, url: String?, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(MedkShapes.Hex).background(MedkColors.PrimaryGradient), contentAlignment = Alignment.Center) {
        Text((name?.trim()?.firstOrNull() ?: 'M').uppercase(), color = Color.White, style = MaterialTheme.typography.titleSmall)
        if (!url.isNullOrBlank()) SubcomposeAsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun VerifiedSeller(name: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Filled.Verified, null, tint = MedkColors.Info, modifier = Modifier.size(16.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
