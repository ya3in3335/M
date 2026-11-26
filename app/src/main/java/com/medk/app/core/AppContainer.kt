package com.medk.app.core

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.medk.app.BuildConfig
import com.medk.app.data.AdminRepository
import com.medk.app.data.AuthRepository
import com.medk.app.data.CartRepository
import com.medk.app.data.CatalogRepository
import com.medk.app.data.ChatRepository
import com.medk.app.data.NotificationRepository
import com.medk.app.data.OrderRepository
import com.medk.app.data.SettingsRepository
import com.medk.app.data.demo.DemoAdminRepository
import com.medk.app.data.demo.DemoAuthRepository
import com.medk.app.data.demo.DemoCatalogRepository
import com.medk.app.data.demo.DemoChatRepository
import com.medk.app.data.demo.DemoNotificationRepository
import com.medk.app.data.demo.DemoOrderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

/** Manual dependency container (single instance, created by [com.medk.app.MedkApp]). */
class AppContainer(val context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; coerceInputValues = true }

    val isDemo: Boolean = BuildConfig.DEMO
    val firebaseConfigured: Boolean = !isDemo && runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    val isConfigured get() = isDemo || firebaseConfigured

    val firebaseAuth: FirebaseAuth? by lazy { if (firebaseConfigured) FirebaseAuth.getInstance() else null }
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    val settings by lazy { SettingsRepository(context) }
    val network by lazy { NetworkMonitor(context) }
    val updates by lazy { UpdateManager(context, json, appScope) }
    val media by lazy { com.medk.app.data.MediaRepository(this) }
    val auth: AuthRepository by lazy { if (isDemo) DemoAuthRepository(this) else AuthRepository(this) }
    val catalog: CatalogRepository by lazy { if (isDemo) DemoCatalogRepository(this) else CatalogRepository(this) }
    val cart by lazy { CartRepository(context, json) }
    val orders: OrderRepository by lazy { if (isDemo) DemoOrderRepository(this) else OrderRepository(this) }
    val chat: ChatRepository by lazy { if (isDemo) DemoChatRepository(this) else ChatRepository(this) }
    val admin: AdminRepository by lazy { if (isDemo) DemoAdminRepository(this) else AdminRepository(this) }
    val notifications: NotificationRepository by lazy { if (isDemo) DemoNotificationRepository(this) else NotificationRepository(this) }
}
