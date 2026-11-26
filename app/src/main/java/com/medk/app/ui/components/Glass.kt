package com.medk.app.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/** Height covered by the floating glass bottom bar; tab screens pad their scroll content by it. */
val LocalBottomBarInset = compositionLocalOf<Dp> { 0.dp }

/** Source of the backdrop blur for the glass bottom bar (null = no blur available). */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }
