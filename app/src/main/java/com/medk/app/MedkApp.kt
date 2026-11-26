package com.medk.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.medk.app.core.AppContainer

class MedkApp : Application(), coil3.SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        // App Check: Play Integrity in release, debug provider in debug builds.
        if (container.firebaseConfigured) runCatching { com.medk.app.core.AppCheckSetup.install() }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /** Image loader that also understands "media:" / "chatfile:" references stored in Firestore. */
    override fun newImageLoader(context: coil3.PlatformContext): coil3.ImageLoader =
        coil3.ImageLoader.Builder(context).components { add(com.medk.app.data.MedkMediaFetcher.Factory()) }.build()

    companion object {
        const val CHANNEL_ID = "medk_default"
        lateinit var instance: MedkApp
            private set
        val container get() = instance.container
    }
}
