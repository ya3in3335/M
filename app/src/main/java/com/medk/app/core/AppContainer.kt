package com.medk.app.core

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.medk.app.BuildConfig
import com.medk.app.data.AdminRepository
import com.medk.app.data.AuthRepository
import com.medk.app.data.CartRepository
import com.medk.app.data.CatalogRepository
import com.medk.app.data.ChatRepository
import com.medk.app.data.EdgeFunctions
import com.medk.app.data.NotificationRepository
import com.medk.app.data.OrderRepository
import com.medk.app.data.SettingsRepository
import com.medk.app.data.demo.DemoAdminRepository
import com.medk.app.data.demo.DemoAuthRepository
import com.medk.app.data.demo.DemoCatalogRepository
import com.medk.app.data.demo.DemoChatRepository
import com.medk.app.data.demo.DemoNotificationRepository
import com.medk.app.data.demo.DemoOrderRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/** Manual dependency container (single instance, created by [com.medk.app.MedkApp]). */
class AppContainer(val context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; coerceInputValues = true }

    val firebaseConfigured: Boolean = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    val backendConfigured: Boolean = BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
    val isDemo: Boolean = BuildConfig.DEMO
    val isConfigured get() = isDemo || (firebaseConfigured && backendConfigured)

    val firebaseAuth: FirebaseAuth? by lazy { if (firebaseConfigured) FirebaseAuth.getInstance() else null }

    /** Supabase client authenticated with the Firebase ID token (Supabase third-party auth). */
    val supabase: SupabaseClient by lazy {
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            defaultSerializer = KotlinXSerializer(json)
            accessToken = { firebaseAuth?.currentUser?.getIdToken(false)?.await()?.token }
            install(Postgrest)
            install(Storage)
            install(Realtime)
        }
    }

    val settings by lazy { SettingsRepository(context) }
    val edge by lazy { EdgeFunctions(this) }
    val auth: AuthRepository by lazy { if (isDemo) DemoAuthRepository(this) else AuthRepository(this) }
    val catalog: CatalogRepository by lazy { if (isDemo) DemoCatalogRepository(this) else CatalogRepository(this) }
    val cart by lazy { CartRepository(context, json) }
    val orders: OrderRepository by lazy { if (isDemo) DemoOrderRepository(this) else OrderRepository(this) }
    val chat: ChatRepository by lazy { if (isDemo) DemoChatRepository(this) else ChatRepository(this) }
    val admin: AdminRepository by lazy { if (isDemo) DemoAdminRepository(this) else AdminRepository(this) }
    val notifications: NotificationRepository by lazy { if (isDemo) DemoNotificationRepository(this) else NotificationRepository(this) }
}
