package com.medk.app.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Geometric shape language of MED-K: chamfered corners (from the MK logo). */
object MedkShapes {
    val Chamfer = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
    val ChamferSm = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp)
    val ChamferXs = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp)
    val Card = RoundedCornerShape(10.dp)
    val Small = RoundedCornerShape(6.dp)
    /** Chamfered square — the MK logo frame, used for emblems/avatars/badges. */
    val Emblem = CutCornerShape(topStartPercent = 28, topEndPercent = 0, bottomEndPercent = 28, bottomStartPercent = 0)
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

