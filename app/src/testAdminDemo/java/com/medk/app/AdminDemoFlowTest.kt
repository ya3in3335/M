package com.medk.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasScrollAction
import com.github.takahirom.roborazzi.captureRoboImage
import com.medk.app.data.demo.DemoStore
import com.medk.app.ui.MedkRoot
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** End-to-end walk through the MED-K Admin demo (owner control panel). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AdminDemoFlowTest {
    @get:Rule val rule = createComposeRule()

    private fun waitText(t: String, ms: Long = 8000) = try {
        rule.waitUntil(ms) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200))
            rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    } catch (e: Throwable) { shot("FAIL_admin"); throw AssertionError("waiting for: $t", e) }
    private fun shot(n: String) { rule.waitForIdle(); rule.onRoot().captureRoboImage("screenshots/admin/$n.png") }

    @Test fun ownerControlPanel() {
        rule.setContent { MedkRoot(MutableStateFlow(null)) }
        waitText("دخول كمالك"); shot("01_login")
        rule.onNodeWithText("دخول كمالك").performClick()
        waitText("الإيرادات المؤكدة"); shot("02_dashboard")
        rule.onAllNodesWithText("الطلبات").onFirst().performClick()
        waitText("إدارة الطلبات"); waitText("MK-DEMO0001"); shot("03_orders")
        rule.onAllNodesWithText("المزيد").onFirst().performClick()
        waitText("البائعون والصلاحيات"); shot("04_more")
        rule.onNodeWithText("البائعون والصلاحيات").performClick()
        waitText("أمين"); shot("05_sellers")
        rule.onAllNodesWithText("أمين", substring = true).onFirst().performClick()
        waitText("إدارة أسعار P2P"); shot("06_permissions")
        rule.onNode(androidx.compose.ui.test.hasContentDescription("رجوع")).performClick()
        waitText("سارة")
        rule.onNode(androidx.compose.ui.test.hasContentDescription("رجوع")).performClick()
        waitText("العملاء")
        rule.onNodeWithText("العملاء").performClick()
        waitText("ياسين"); shot("07_customers")
        rule.onNodeWithText("ياسين ب.").performClick()
        waitText("ترقية إلى بائع"); shot("08_customer")
        rule.onNodeWithText("ترقية إلى بائع").performClick()
        rule.onNodeWithText("تأكيد").performClick()
        waitText("صلاحيات البائع"); waitText("إدارة أسعار P2P")
        assertEquals("staff", DemoStore.profiles.getValue("demo_c2").role)
        shot("09_promoted")
        rule.onAllNodesWithText("المنتجات").let { }
    }
}
