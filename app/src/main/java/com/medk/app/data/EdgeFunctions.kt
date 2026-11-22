package com.medk.app.data

import com.medk.app.BuildConfig
import com.medk.app.core.AppContainer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Calls MED-K Supabase Edge Functions. The Firebase ID token is sent and verified
 * on the server (see supabase/functions). No privileged key exists in the app.
 */
class EdgeFunctions(private val c: AppContainer) {
    private val http by lazy { HttpClient(OkHttp) }

    suspend fun call(name: String, body: JsonObject, forceRefresh: Boolean = false): JsonObject {
        val user = c.firebaseAuth?.currentUser ?: error("not signed in")
        val token = user.getIdToken(forceRefresh).await().token ?: error("missing token")
        val res = http.post("${BuildConfig.SUPABASE_URL}/functions/v1/$name") {
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(TextContent(body.toString(), ContentType.Application.Json))
        }
        val text = res.bodyAsText()
        val obj = runCatching { c.json.parseToJsonElement(text).jsonObject }.getOrNull()
        if (!res.status.isSuccess()) {
            val err = obj?.get("error")?.jsonPrimitive?.contentOrNull ?: "HTTP ${res.status.value}"
            throw IllegalStateException(err)
        }
        return obj ?: JsonObject(emptyMap())
    }
}
