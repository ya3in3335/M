package com.medk.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Classic, familiar shape language: rounded rectangles and circles. */
object MedkShapes {
    val Chamfer = RoundedCornerShape(16.dp)
    val ChamferSm = RoundedCornerShape(12.dp)
    val ChamferXs = RoundedCornerShape(8.dp)
    val Card = RoundedCornerShape(14.dp)
    val Small = RoundedCornerShape(8.dp)
    /** Icon badges, avatars, step markers. */
    val Emblem = CircleShape
    /** App logo tile. */
    val Logo = RoundedCornerShape(26)
}

