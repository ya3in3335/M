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

/** End-to-end walk through the demo flavor (no backend). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DemoFlowTest {
    @get:Rule val rule = createComposeRule()

    private fun waitText(t: String, ms: Long = 8000) = try {
        rule.waitUntil(ms) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200))
            rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    } catch (e: Throwable) { shot("FAIL_" + t.take(12).replace(" ", "_")); throw AssertionError("waiting for: $t", e) }
    private fun shot(n: String) { rule.waitForIdle(); rule.onRoot().captureRoboImage("screenshots/demo/$n.png") }

    @Test fun customerAndOwnerFlow() {
        rule.setContent { MedkRoot(MutableStateFlow(null)) }
        waitText("نسخة تجريبية"); shot("01_welcome")
        rule.onNodeWithText("دخول كعميل").performClick()
        waitText("خدماتنا الرئيسية"); shot("02_home")

        rule.onNodeWithText("شحن الألعاب").performClick()
        waitText("اختر اللعبة"); waitText("Free Fire"); shot("03_gaming")
        rule.onAllNodesWithText("Free Fire").onFirst().performClick()
        waitText("1080 جوهرة"); shot("04_section")
        rule.onNodeWithText("520 جوهرة Free Fire").performClick()
        waitText("أضف إلى السلة"); shot("05_product")
        rule.onNodeWithText("أضف إلى السلة").performClick()
        rule.waitForIdle()

        // also add a Netflix product → cart must split into 2 orders (different sellers)
        DemoStore.products.first { it.id == "p9" }.let { p ->
            kotlinx.coroutines.runBlocking { MedkApp.container.cart.add(p, 1, "Netflix") }
        }
        rule.onNode(androidx.compose.ui.test.hasContentDescription("السلة")).performClick()
        waitText("الإجمالي"); waitText("بائعين"); shot("06_cart")
        val before = DemoStore.orders.size
        rule.onNodeWithText("تواصل مع البائع لإتمام الطلب").performClick()
        waitText("طلب MK-", 10000); shot("07_conversations")
        assertEquals(before + 2, DemoStore.orders.size)

        rule.onAllNodesWithText("Free Fire", substring = true).onFirst().performClick()
        waitText("أرغب في إتمام الطلب")
        rule.onNodeWithText("اكتب رسالتك…").performTextInput("ID: 123456789")
        rule.onNodeWithText("اكتب رسالتك…", useUnmergedTree = true).let { }
        rule.onAllNodesWithText("ID: 123456789").onFirst()
        rule.onNode(androidx.compose.ui.test.hasContentDescription("إرسال")).performClick()
        waitText("استلمت طلبك", 10000); shot("08_chat")
        assertTrue(DemoStore.messages.any { it.body?.contains("استلمت طلبك") == true })
    }

    @Test fun moreCustomerScreens() {
        rule.setContent { MedkRoot(MutableStateFlow(null)) }
        waitText("نسخة تجريبية")
        rule.onNodeWithText("دخول كعميل").performClick()
        waitText("خدماتنا الرئيسية")
        rule.onAllNodesWithText("الأقسام").onFirst().performClick()
        waitText("أقسام فرعية"); shot("11_categories")
        rule.onNodeWithText("شراء وبيع العملات الرقمية P2P").performClick()
        waitText("Tether"); shot("12_p2p")
        rule.onNode(androidx.compose.ui.test.hasContentDescription("رجوع")).performClick()
        waitText("أقسام فرعية")
        rule.onAllNodesWithText("حسابي").onFirst().performClick()
        waitText("طلباتي"); shot("13_profile")
        rule.onAllNodesWithText("طلباتي").onFirst().performClick()
        waitText("MK-DEMO0001")
        rule.onNodeWithText("MK-DEMO0001").performClick()
        waitText("حالة التسليم"); shot("14_order")
    }

    @Test fun staffWorkspace() {
        rule.setContent { MedkRoot(MutableStateFlow(null)) }
        waitText("نسخة تجريبية")
        rule.onNodeWithText("دخول كبائع").performClick()
        waitText("مساحة عمل البائع"); waitText("الطلبات الواردة"); shot("09_staff_workspace")
    }
}
