package com.medk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.medk.app.data.Category
import com.medk.app.data.OrderStatus
import com.medk.app.data.Product
import com.medk.app.ui.auth.LoginScreen
import com.medk.app.ui.auth.SplashScreen
import com.medk.app.ui.auth.WelcomeScreen
import com.medk.app.ui.components.CategoryCard
import com.medk.app.ui.components.MedkWordmark
import com.medk.app.ui.components.OrderProgress
import com.medk.app.ui.components.ProductCard
import com.medk.app.ui.components.SectionHeader
import com.medk.app.ui.home.BannerPager
import com.medk.app.ui.theme.MedkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Visual regression renders of key screens (run: ./gradlew recordRoborazziDebug). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val categories = listOf(
        Category("1", "gaming", "شحن الألعاب", "جواهر - شدات - عملات داخل الألعاب", "orange", "gaming"),
        Category("2", "subscriptions", "اشتراكات التطبيقات", "Netflix - YouTube - Spotify وأكثر", "blue", "subscriptions"),
        Category("3", "accounts", "شراء الحسابات", "حسابات - ألعاب وتطبيقات مضمونة", "purple", "accounts"),
        Category("4", "p2p", "شراء وبيع العملات الرقمية P2P", "بيتكوين - USDT وغيرها", "green", "p2p"),
        Category("5", "other", "خدمات أخرى", "برامج - اشتراكات مميزة - خدمات رقمية متنوعة", "cyan", "other"),
    )

    private fun shot(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        rule.setContent { MedkTheme(dark) { Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() } } }
        rule.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun splash() = shot("01_splash") { SplashScreen() }
    @Test fun welcome() = shot("02_welcome") { WelcomeScreen({}, {}) }
    @Test fun login() = shot("03_login") { LoginScreen({}, {}, {}) }

    @Test fun home() = shot("04_home") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MedkWordmark(subtitle = true, modifier = Modifier.padding(bottom = 4.dp).fillMaxWidthCompat())
            BannerPager(emptyList()) {}
            SectionHeader("خدماتنا الرئيسية", action = "عرض الكل", onAction = {})
            categories.forEach { CategoryCard(it, {}) }
        }
    }

    @Test fun homeLight() = shot("05_home_light", dark = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("خدماتنا الرئيسية", action = "عرض الكل", onAction = {})
            categories.take(3).forEach { CategoryCard(it, {}) }
        }
    }

    @Test fun productsAndProgress() = shot("06_products") {
        val p = Product("p1", "1", null, "1080 جوهرة Free Fire", "شحن مباشر عبر ID", 9.99, 2700.0, 4, true)
        val q = Product("p2", "1", null, "660 UC PUBG Mobile", null, 9.5, 2500.0, null, true)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProductCard(p, {}, {}, Modifier.width(180.dp), isFavorite = true, onFavorite = {})
                ProductCard(q, {}, {}, Modifier.width(180.dp), onFavorite = {})
            }
            OrderProgress(OrderStatus.PAID)
        }
    }
}

private fun Modifier.fillMaxWidthCompat() = this.then(Modifier.padding(0.dp))
