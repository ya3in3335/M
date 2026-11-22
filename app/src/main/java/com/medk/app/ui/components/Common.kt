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

// ------------------------------------------------------------------ brand
@Composable
fun MedkWordmark(fontSize: TextUnit = 30.sp, subtitle: Boolean = false, modifier: Modifier = Modifier) {
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

/** "M" mark inside a rounded gradient tile. */
@Composable
fun MedkMark(size: Dp = 72.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(MedkColors.PrimaryGradient),
        contentAlignment = Alignment.Center,
    ) {
        Text("M", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.5f).sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    }
}

// ---------------------------------------------------------------- buttons
@Composable
fun PrimaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, loading: Boolean = false, icon: ImageVector? = null, height: Dp = 52.dp,
) {
    val active = enabled && !loading
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp))
            .background(if (active) MedkColors.PrimaryGradient else SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest))
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.onSurface, height: Dp = 52.dp,
) {
    Surface(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, color = color, style = MaterialTheme.typography.titleMedium, modifier = Modifier.alpha(if (enabled) 1f else .5f))
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
        label = { Text(label) }, singleLine = singleLine, minLines = minLines,
        leadingIcon = leading?.let { { Icon(it, null) } },
        trailingIcon = when {
            isPassword -> { { IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "إخفاء كلمة المرور" else "إظهار كلمة المرور") } } }
            else -> trailing
        },
        visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
        isError = error != null, supportingText = error?.let { { Text(it) } },
        shape = RoundedCornerShape(16.dp),
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
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = {
            when {
                navigationIcon != null -> navigationIcon()
                onBack != null -> IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

// --------------------------------------------------------- section header
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(MedkColors.PrimaryGradient))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) Text(
            action, color = MedkColors.Violet, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp),
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
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(action, onAction, Modifier.width(220.dp), height = 46.dp)
        }
    }
}

@Composable
fun Shimmer(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(.35f, .8f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = a)))
}

// ------------------------------------------------------------------ prices
@Composable
fun PriceBlock(usdValue: Double, dzdValue: Double, modifier: Modifier = Modifier, large: Boolean = false) {
    Column(modifier) {
        Text(usd(usdValue), style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = MedkColors.Violet, fontWeight = FontWeight.ExtraBold)
        Text(dzd(dzdValue), style = if (large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .16f)).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun QuantitySelector(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, min: Int = 1, max: Int = 99, compact: Boolean = false) {
    val s = if (compact) 30.dp else 40.dp
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(s).clip(RoundedCornerShape(10.dp)).background(MedkColors.PrimaryGradient)
            .clickable(enabled = value < max) { onChange(value + 1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, "زيادة", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(if (compact) 32.dp else 44.dp),
            textAlign = TextAlign.Center)
        Box(Modifier.size(s).clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(enabled = value > min) { onChange(value - 1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Remove, "إنقاص", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun MedkCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: PaddingValues = PaddingValues(16.dp),
             content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val m = modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(padding)
    Box(m) { content() }
}

@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(modifier.size(18.dp).clip(CircleShape).background(MedkColors.Danger), contentAlignment = Alignment.Center) {
        Text(if (count > 9) "9+" else "$count", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 10.sp))
    }
}

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        Text(value, color = valueColor, style = MaterialTheme.typography.titleSmall)
    }
}
