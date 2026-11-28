package com.medk.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Runtime switches (tests turn the network gate and update checks off). */
object AppFlags {
    @Volatile var strictNetwork = true
    @Volatile var updateChecks = true
    /** Video splash (off under Robolectric tests, which have no video decoder). */
    @Volatile var videoSplash = android.os.Build.FINGERPRINT != "robolectric"
}

/** Live "has working internet" state, used to block the app while offline. */
class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(current())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private fun NetworkCapabilities?.usable() =
        this != null && hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private fun current(): Boolean = runCatching { cm.getNetworkCapabilities(cm.activeNetwork).usable() }.getOrDefault(false)

    init {
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { _online.value = caps.usable() }
                override fun onLost(network: Network) { _online.value = current() }
                override fun onUnavailable() { _online.value = false }
            })
        }
    }

    /** Manual re-check (the "retry" button). */
    fun refresh() { _online.value = current() }
}
