package com.medk.app.ui.system

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.medk.app.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Total length of the logo animation. */
const val LOGO_MOTION_MS = 2800

private const val COLS = 8
private const val ROWS = 7

private class Shard(val col: Int, val row: Int, val angle: Float, val dist: Float, val spin: Float, val delay: Float)

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun easeOutCubic(t: Float) = 1f - (1f - t).pow(3)
private fun easeInOutCubic(t: Float) = if (t < .5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
private fun easeOutBack(t: Float): Float { val c1 = 1.55f; val c3 = c1 + 1f; return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2) }

/**
 * MED-K logo in motion: the tile appears, the logo shatters into shards that fly apart and spin,
 * then each shard flies back (staggered, slight overshoot) and the logo re-forms, glows and gets a light sweep.
 * [progress] runs 0 → 1 (see [LogoMotion] for the self-driving version).
 */
@Composable
fun LogoMotionFrame(progress: Float, size: Dp = 220.dp, modifier: Modifier = Modifier) {
    val art = ImageBitmap.imageResource(R.drawable.medk_logo_art)
    val shards = remember {
        val rnd = Random(7)
        List(COLS * ROWS) { i ->
            val c = i % COLS; val r = i / COLS
            val cx = (c + .5f) / COLS - .5f; val cy = (r + .5f) / ROWS - .5f
            val base = kotlin.math.atan2(cy, cx)
            Shard(c, r, base + (rnd.nextFloat() - .5f) * 1.1f, .55f + rnd.nextFloat() * .75f, (rnd.nextFloat() - .5f) * 540f,
                // shards near the centre come back first
                delay = clamp01(hypot(cx, cy) / .72f) * .55f + rnd.nextFloat() * .12f)
        }
    }
    Canvas(modifier.size(size)) { drawLogo(art, shards, progress) }
}

/** Plays the whole animation once (used by the splash screen). */
@Composable
fun LogoMotion(size: Dp = 220.dp, modifier: Modifier = Modifier) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(LOGO_MOTION_MS, easing = LinearEasing)) }
    Box(modifier) { LogoMotionFrame(p.value, size) }
}

private fun DrawScope.drawLogo(art: ImageBitmap, shards: List<Shard>, p: Float) {
    val w = size.width
    val tile = Size(w * .9f, w * .9f)
    val tileTopLeft = Offset((w - tile.width) / 2f, (size.height - tile.height) / 2f)
    val corner = CornerRadius(tile.width * .2f)
    val violet = Color(0xFFA855F7); val purple = Color(0xFF7C3AED)

    // ---- phases
    val appear = easeOutCubic(clamp01(p / .16f))                       // 0.00 – 0.16 logo appears
    val burst = easeOutCubic(clamp01((p - .20f) / .22f))               // 0.20 – 0.42 shatter
    val gather = clamp01((p - .42f) / .40f)                            // 0.42 – 0.82 re-assemble
    val finish = easeInOutCubic(clamp01((p - .80f) / .20f))            // 0.80 – 1.00 glow + sweep
    val frameAlpha = when { p < .20f -> appear; p < .80f -> (1f - burst).coerceAtLeast(0f) * (1f - clamp01((p - .42f) / .1f)); else -> finish }

    // ---- tile (glass) + glowing frame
    val pulse = if (p > .8f) 1f + .035f * sin(finish * PI.toFloat()) else 1f
    scale(pulse) {
        if (frameAlpha > 0f) {
            drawRoundRect(Brush.radialGradient(listOf(Color(0xFF1C0B33), Color(0xFF07040E)), center = center, radius = w * .7f),
                tileTopLeft, tile, corner, alpha = frameAlpha)
            drawRoundRect(Brush.linearGradient(listOf(violet, purple.copy(.25f), violet)), tileTopLeft, tile, corner,
                style = Stroke(w * .012f), alpha = frameAlpha)
            // outer glow
            for (k in 1..4) drawRoundRect(violet, tileTopLeft - Offset(k * w * .012f, k * w * .012f),
                Size(tile.width + k * w * .024f, tile.height + k * w * .024f), CornerRadius(corner.x + k * w * .012f),
                style = Stroke(w * .01f), alpha = frameAlpha * (.16f - k * .035f).coerceAtLeast(0f))
        }

        // ---- logo shards
        val artW = tile.width * .82f
        val artH = artW * art.height / art.width
        val artTopLeft = Offset((w - artW) / 2f, (size.height - artH) / 2f)
        val sw = art.width / COLS; val sh = art.height / ROWS
        val dw = artW / COLS; val dh = artH / ROWS
        for (s in shards) {
            val srcX = s.col * sw; val srcY = s.row * sh
            val srcW = if (s.col == COLS - 1) art.width - srcX else sw
            val srcH = if (s.row == ROWS - 1) art.height - srcY else sh
            // how far this shard is from home: 0 = in place, 1 = fully scattered
            val away = when {
                p < .20f -> 0f
                p < .42f -> burst
                else -> 1f - easeOutBack(clamp01((gather - s.delay * .5f) / .5f))
            }
            val alpha = when {
                p < .20f -> appear
                p < .42f -> 1f - burst * .55f
                else -> (.45f + .55f * (1f - away.coerceAtLeast(0f))).coerceIn(0f, 1f)
            }
            val home = Offset(artTopLeft.x + s.col * dw, artTopLeft.y + s.row * dh)
            val fly = Offset(cos(s.angle), sin(s.angle)) * (s.dist * w * away)
            val pos = home + fly
            val dstW = artW * srcW / art.width; val dstH = artH * srcH / art.height
            val grow = if (p < .20f) .88f + .12f * appear else 1f
            translate(pos.x, pos.y) {
                rotate(s.spin * away, Offset(dstW / 2f, dstH / 2f)) {
                    scale(grow, Offset(dstW / 2f, dstH / 2f)) {
                        drawImage(art, IntOffset(srcX, srcY), IntSize(srcW, srcH), dstSize = IntSize(dstW.toInt() + 1, dstH.toInt() + 1),
                            alpha = alpha)
                    }
                }
            }
        }

        // ---- light sweep across the finished tile
        if (p > .82f) {
            val t = clamp01((p - .82f) / .16f)
            val x = tileTopLeft.x - tile.width * .5f + t * tile.width * 2f
            clipPath(androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(
                tileTopLeft.x, tileTopLeft.y, tileTopLeft.x + tile.width, tileTopLeft.y + tile.height, corner)) }) {
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(.16f), Color.Transparent),
                    start = Offset(x - w * .07f, tileTopLeft.y), end = Offset(x + w * .07f, tileTopLeft.y + w * .14f)))
            }
        }
    }
}
