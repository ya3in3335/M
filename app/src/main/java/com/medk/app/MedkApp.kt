package com.medk.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.medk.app.core.AppContainer

class MedkApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    companion object {
        const val CHANNEL_ID = "medk_default"
        lateinit var instance: MedkApp
            private set
        val container get() = instance.container
    }
}
