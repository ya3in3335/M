package com.medk.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medk.app.core.dzd
import com.medk.app.core.usd
import com.medk.app.ui.theme.MedkColors
import com.medk.app.ui.theme.MedkShapes

// ------------------------------------------------------------------ brand
@Composable
fun MedkWordmark(fontSize: TextUnit = 22.sp, subtitle: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(brush = Brush.horizontalGradient(listOf(MedkColors.Violet, Color(0xFFD7B8FF))))) { append("MED") }
                withStyle(SpanStyle(color = MedkColors.Violet)) { append("-") }
                withStyle(SpanStyle(color = MedkColors.Purple)) { append("K") }
            },
            fontSize = fontSize, fontWeight = FontWeight.ExtraBold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            style = MaterialTheme.typography.displaySmall.copy(lineHeight = fontSize * 1.1f),
        )
        if (subtitle) Text(
            "Digital Services Marketplace",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Classic app logo: "MK" on a rounded purple tile. */
@Composable
fun MedkMark(size: Dp = 64.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(MedkShapes.Logo).background(Brush.linearGradient(listOf(MedkColors.Violet, MedkColors.Purple))),
        contentAlignment = Alignment.Center) {
        Text("MK", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.36f).sp,
            letterSpacing = (-1).sp, style = MaterialTheme.typography.titleLarge.copy(lineHeight = (size.value * 0.42f).sp))
    }
}

/** Circular badge holding an icon. */
@Composable
fun EmblemIcon(icon: ImageVector, brush: Brush, size: Dp = 36.dp, iconTint: Color = Color.White, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(MedkShapes.Emblem).background(brush), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(size * .48f))
    }
}

// ---------------------------------------------------------------- buttons
@Composable
fun PrimaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, loading: Boolean = false, icon: ImageVector? = null, height: Dp = 44.dp,
) {
    val active = enabled && !loading
    Box(
        modifier.fillMaxWidth().height(height.coerceAtMost(46.dp)).clip(MedkShapes.ChamferSm)
            .background(if (active) SolidColor(MedkColors.Purple) else SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest))
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)) }
            Text(text, color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.onSurface, height: Dp = 44.dp,
) {
    Surface(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(height.coerceAtMost(46.dp)),
        shape = MedkShapes.ChamferSm, color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (color == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.outline else color.copy(.5f)),
        // classic outlined button
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)) }
            Text(text, color = color, style = MaterialTheme.typography.labelLarge, modifier = Modifier.alpha(if (enabled) 1f else .5f))
        }
    }
}

// ------------------------------------------------------------- text field
@Composable
fun MedkTextField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    leading: ImageVector? = null, isPassword: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true, minLines: Int = 1, error: String? = null, enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(), enabled = enabled,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) }, singleLine = singleLine, minLines = minLines,
        textStyle = MaterialTheme.typography.bodyMedium,
        leadingIcon = leading?.let { { Icon(it, null, Modifier.size(18.dp)) } },
        trailingIcon = when {
            isPassword -> { { IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, modifier = Modifier.size(18.dp),
                    contentDescription = if (visible) "إخفاء كلمة المرور" else "إظهار كلمة المرور") } } }
            else -> trailing
        },
        visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
        isError = error != null, supportingText = error?.let { { Text(it) } },
        shape = MedkShapes.Card,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MedkColors.Violet, unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedLabelColor = MedkColors.Violet, cursorColor = MedkColors.Violet,
        ),
    )
}

// ---------------------------------------------------------------- top bar
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedkTopBar(
    title: String, onBack: (() -> Unit)? = null, subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        TopAppBar(
            title = {
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = {
                when {
                    navigationIcon != null -> navigationIcon()
                    onBack != null -> IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", Modifier.size(20.dp)) }
                }
            },
            actions = actions,
            expandedHeight = 52.dp,
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Simple divider (kept name for existing call sites). */
@Composable
fun TechRule(modifier: Modifier = Modifier) = HorizontalDivider(modifier, color = MaterialTheme.colorScheme.outlineVariant)

// --------------------------------------------------------- section header
@Suppress("UNUSED_PARAMETER")
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null, index: Int? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) Text(
            action, color = MedkColors.Violet, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clip(MedkShapes.Small).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// ------------------------------------------------------------------ states
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MedkColors.Violet)
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    StateMessage(Icons.Filled.ErrorOutline, "تعذر التحميل", message, if (onRetry != null) "إعادة المحاولة" else null, onRetry, modifier,
        tint = MedkColors.Danger)
}

@Composable
fun EmptyBox(icon: ImageVector, title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null,
             modifier: Modifier = Modifier) = StateMessage(icon, title, subtitle, action, onAction, modifier)

@Composable
private fun StateMessage(icon: ImageVector, title: String, subtitle: String?, action: String?, onAction: (() -> Unit)?,
                         modifier: Modifier, tint: Color = MedkColors.Violet) {
    Column(modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton(action, onAction, Modifier.width(200.dp), height = 40.dp)
        }
    }
}

@Composable
fun Shimmer(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(.35f, .8f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(modifier.clip(MedkShapes.Card).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = a)))
}

// ------------------------------------------------------------------ prices
@Composable
fun PriceBlock(usdValue: Double, dzdValue: Double, modifier: Modifier = Modifier, large: Boolean = false) {
    Column(modifier) {
        Text(usd(usdValue), style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
            color = MedkColors.Violet, fontWeight = FontWeight.ExtraBold)
        Text(dzd(dzdValue), style = if (large) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .15f)).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(11.dp)); Spacer(Modifier.width(3.dp)) }
        Text(text, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun QuantitySelector(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, min: Int = 1, max: Int = 99, compact: Boolean = false) {
    val s = if (compact) 26.dp else 32.dp
    Row(modifier.clip(RoundedCornerShape(50)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
        .background(MaterialTheme.colorScheme.surfaceContainer).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(s).clip(CircleShape).background(MedkColors.PrimaryGradient)
            .clickable(enabled = value < max) { onChange(value + 1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, "زيادة", tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Text("$value", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(if (compact) 26.dp else 34.dp),
            textAlign = TextAlign.Center)
        Box(Modifier.size(s).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = value > min) { onChange(value - 1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Remove, "إنقاص", modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
fun MedkCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: PaddingValues = PaddingValues(12.dp),
             content: @Composable () -> Unit) {
    val shape = MedkShapes.Card
    val m = modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(padding)
    Box(m) { content() }
}

@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(modifier.size(15.dp).clip(CircleShape).background(MedkColors.Danger), contentAlignment = Alignment.Center) {
        Text(if (count > 9) "9+" else "$count", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 10.sp))
    }
}

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f))
        Text(value, color = valueColor, style = MaterialTheme.typography.titleSmall)
    }
}
