package com.medk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.medk.app.ui.system.LogoMotionFrame
import com.medk.app.ui.theme.MedkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the splash logo animation frame by frame (turned into a preview video by the build tooling). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class LogoMotionFramesTest {
    @get:Rule val rule = createComposeRule()

    @Test fun frames() {
        val p = mutableFloatStateOf(0f)
        rule.setContent {
            MedkTheme(true) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF160A2B), Color(0xFF07070A), Color(0xFF07070A)))),
                    contentAlignment = Alignment.Center) { LogoMotionFrame(p.floatValue, 230.dp) }
            }
        }
        val n = (System.getProperty("medk.frames") ?: "60").toInt()
        for (i in 0..n) {
            p.floatValue = i / n.toFloat()
            rule.waitForIdle()
            rule.onRoot().captureRoboImage("build/logo-frames/f%03d.png".format(i))
        }
    }
}
