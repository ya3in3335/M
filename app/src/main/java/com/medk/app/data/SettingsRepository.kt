package com.medk.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("medk_settings")

class SettingsRepository(private val context: Context) {
    private val darkKey = booleanPreferencesKey("dark_theme")
    private val notifKey = booleanPreferencesKey("notifications")

    val darkTheme: Flow<Boolean> = context.settingsStore.data.map { it[darkKey] ?: true }
    val notificationsEnabled: Flow<Boolean> = context.settingsStore.data.map { it[notifKey] ?: true }

    suspend fun setDarkTheme(v: Boolean) { context.settingsStore.edit { it[darkKey] = v } }
    suspend fun setNotifications(v: Boolean) { context.settingsStore.edit { it[notifKey] = v } }
}
