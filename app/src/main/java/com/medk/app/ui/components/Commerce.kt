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
        Box(Modifier.fillMaxSize().background(palette?.brush ?: Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHighest))),
            contentAlignment = Alignment.Center) {
            Icon(placeholderIcon, null, tint = Color.White.copy(alpha = .55f), modifier = Modifier.size(36.dp))
        }
    }
    if (url.isNullOrBlank()) Box(modifier) { fallback() }
    else SubcomposeAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = contentScale,
        loading = { Shimmer(Modifier.fillMaxSize()) }, error = { fallback() })
}

/** Layered illustration used on category cards (soft rings + icon). */
@Composable
fun CategoryArt(key: String?, palette: CategoryPalette, size: Dp = 84.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = .08f)))
        Box(Modifier.size(size * .74f).clip(CircleShape).background(
            Brush.linearGradient(listOf(Color.White.copy(alpha = .32f), Color.White.copy(alpha = .06f)))))
        Icon(categoryIcon(key), null, tint = Color.White, modifier = Modifier.size(size * .46f).rotate(if (key == "gaming") -12f else 0f))
        if (key == "accounts") Box(
            Modifier.align(Alignment.BottomStart).offset(x = 6.dp, y = (-6).dp).size(size * .3f).clip(CircleShape)
                .background(MedkColors.Info), contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(size * .2f)) }
    }
}

/** Large service card from the home reference design. */
@Composable
fun CategoryCard(category: Category, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = categoryPalette(category.colorKey ?: category.slug)
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier.fillMaxWidth().height(104.dp).clip(shape).background(p.horizontal)
            .border(1.dp, Brush.horizontalGradient(listOf(p.accent.copy(alpha = .7f), Color.White.copy(alpha = .08f))), shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryArt(category.iconKey ?: category.slug, p)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(category.name, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!category.description.isNullOrBlank()) Text(category.description, color = Color.White.copy(alpha = .82f),
                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = .28f))
            .border(1.dp, Color.White.copy(alpha = .25f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color.White)
        }
    }
}

@Composable
fun ProductCard(
    product: Product, onClick: () -> Unit, onAdd: (() -> Unit)?, modifier: Modifier = Modifier,
    isFavorite: Boolean = false, onFavorite: (() -> Unit)? = null, palette: CategoryPalette? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.15f)) {
            NetImage(product.cover, Modifier.fillMaxSize(), palette = palette)
            if (!product.inStock) Pill("غير متوفر", MedkColors.Danger,
                Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.Black.copy(.5f), RoundedCornerShape(50)))
            else if (product.stock != null && product.stock <= 5) Pill("آخر ${product.stock}", MedkColors.Warning,
                Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.Black.copy(.5f), RoundedCornerShape(50)))
            if (onFavorite != null) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = .45f)).clickable(onClick = onFavorite), contentAlignment = Alignment.Center) {
                Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "المفضلة",
                    tint = if (isFavorite) MedkColors.Violet else Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(product.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(42.dp))
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PriceBlock(product.priceUsd, product.priceDzd, Modifier.weight(1f))
                if (onAdd != null && product.inStock) Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                    .background(MedkColors.PrimaryGradient).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AddShoppingCart, "أضف إلى السلة", tint = Color.White, modifier = Modifier.size(18.dp))
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

/** Horizontal progress stepper: open → paid → delivered → closed. */
@Composable
fun OrderProgress(status: OrderStatus, modifier: Modifier = Modifier) {
    val steps = OrderStatus.entries
    val current = steps.indexOf(status)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        steps.forEachIndexed { i, s ->
            val done = i <= current
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(3.dp).background(
                        if (i == 0) Color.Transparent else if (done) MedkColors.Violet else MaterialTheme.colorScheme.outline))
                    Box(Modifier.size(30.dp).clip(CircleShape).background(
                        if (done) MedkColors.PrimaryGradient else Brush.linearGradient(listOf(
                            MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainerHighest))),
                        contentAlignment = Alignment.Center) {
                        if (done) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        else Text("${i + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                    Box(Modifier.weight(1f).height(3.dp).background(
                        if (i == steps.lastIndex) Color.Transparent else if (i < current) MedkColors.Violet else MaterialTheme.colorScheme.outline))
                }
                Spacer(Modifier.height(6.dp))
                Text(s.label, style = MaterialTheme.typography.labelSmall,
                    color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun Avatar(name: String?, url: String?, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(MedkColors.PrimaryGradient), contentAlignment = Alignment.Center) {
        Text((name?.trim()?.firstOrNull() ?: 'M').uppercase(), color = Color.White, style = MaterialTheme.typography.titleMedium)
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
