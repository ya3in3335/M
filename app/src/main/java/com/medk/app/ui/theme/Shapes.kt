package com.medk.app.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Geometric shape language of MED-K: hexagons + chamfered corners. */
object MedkShapes {
    val Chamfer = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
    val ChamferSm = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp)
    val ChamferXs = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp)
    val Card = RoundedCornerShape(10.dp)
    val Small = RoundedCornerShape(6.dp)
    val Hex = HexagonShape()
}

/** Pointy-top regular hexagon fitted to the bounds. */
class HexagonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(size.width / 2f / cos(Math.toRadians(30.0)).toFloat(), size.height / 2f)
        val cx = size.width / 2f; val cy = size.height / 2f
        val p = Path()
        for (i in 0 until 6) {
            val a = Math.toRadians((-90 + 60 * i).toDouble())
            val x = cx + r * cos(a).toFloat(); val y = cy + r * sin(a).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return Outline.Generic(p)
    }
}

/** Faint engineering grid drawn behind content. */
fun Modifier.blueprintGrid(color: Color, step: Float = 22f, alpha: Float = .06f): Modifier = drawBehind {
    val s = step * density
    val c = color.copy(alpha = alpha)
    var x = 0f
    while (x < size.width) { drawLine(c, Offset(x, 0f), Offset(x, size.height), 1f); x += s }
    var y = 0f
    while (y < size.height) { drawLine(c, Offset(0f, y), Offset(size.width, y), 1f); y += s }
}

