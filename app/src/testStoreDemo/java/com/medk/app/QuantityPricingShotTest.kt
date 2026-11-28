package com.medk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.compose.rememberNavController
import com.github.takahirom.roborazzi.captureRoboImage
import com.medk.app.ui.product.ProductScreen
import com.medk.app.ui.theme.MedkTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Visual check of the packages / per-unit product pages (demo data). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class QuantityPricingShotTest {
    @get:Rule val rule = createComposeRule()
    @Before fun flags() { com.medk.app.core.AppFlags.strictNetwork = false; com.medk.app.core.AppFlags.updateChecks = false }

    private fun shot(id: String, name: String) {
        rule.setContent { MedkTheme(true) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { ProductScreen(id, rememberNavController()) } } }
        repeat(30) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100)); rule.waitForIdle() }
        rule.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun adminProductsFiltered() {
        (MedkApp.container.auth as com.medk.app.data.demo.DemoAuthRepository).loginAs(com.medk.app.data.Role.OWNER)
        rule.setContent { MedkTheme(true) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            com.medk.app.ui.admin.AdminProductsScreen(rememberNavController()) } } }
        repeat(30) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100)); rule.waitForIdle() }
        rule.onRoot().captureRoboImage("screenshots/22_admin_products_filter.png")
    }

    @Test fun packages() = shot("p1", "20_product_packages")
    @Test fun perUnit() = shot("p4", "21_product_per_unit")
}
