package com.medk.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.MutableStateFlow
import com.medk.app.ui.MedkRoot

/** Deep link target extracted from a tapped system notification. */
data class PendingLink(val conversationId: String?, val orderId: String?)

class MainActivity : ComponentActivity() {
    val pendingLink = MutableStateFlow<PendingLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent { MedkRoot(pendingLink) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val conv = intent?.getStringExtra(EXTRA_CONVERSATION)
        val order = intent?.getStringExtra(EXTRA_ORDER)
        if (conv != null || order != null) pendingLink.value = PendingLink(conv, order)
    }

    companion object {
        const val EXTRA_CONVERSATION = "conversation_id"
        const val EXTRA_ORDER = "order_id"
    }
}
