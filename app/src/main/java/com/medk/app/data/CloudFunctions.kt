package com.medk.app.data

import com.medk.app.core.AppContainer
import kotlinx.coroutines.tasks.await

/**
 * Calls MED-K Cloud Functions (callable). Firebase attaches the user's ID token and
 * the App Check token automatically; all authorisation happens on the server.
 */
class CloudFunctions(private val c: AppContainer) {
    @Suppress("UNCHECKED_CAST")
    suspend fun call(name: String, data: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val result = c.functions.getHttpsCallable(name).call(data).await()
        return (result.getData() as? Map<String, Any?>) ?: emptyMap()
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun callList(name: String, data: Map<String, Any?> = emptyMap()): List<Map<String, Any?>> {
        val result = c.functions.getHttpsCallable(name).call(data).await()
        return (result.getData() as? List<Map<String, Any?>>) ?: emptyList()
    }
}
