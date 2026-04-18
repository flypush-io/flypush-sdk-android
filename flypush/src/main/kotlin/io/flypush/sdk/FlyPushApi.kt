// REST API client for device registration and topic management

package io.flypush.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

internal class FlyPushApi(
    private val apiKey: String,
    private val baseUrl: String,
) {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun registerDevice(token: String, userId: String?): RegisterResponse =
        post("/v1/devices/register", RegisterRequest(token = token, userId = userId))

    suspend fun updateDevice(deviceId: String, userId: String?, tags: List<String>?): Unit =
        put("/v1/devices/$deviceId", UpdateRequest(userId = userId, tags = tags))

    suspend fun unregisterDevice(deviceId: String): Unit =
        delete("/v1/devices/$deviceId")

    suspend fun subscribe(deviceId: String, topic: String): Unit =
        post("/v1/devices/$deviceId/subscribe", SubscribeRequest(topic))

    suspend fun unsubscribe(deviceId: String, topic: String): Unit =
        post("/v1/devices/$deviceId/unsubscribe", SubscribeRequest(topic))

    private suspend inline fun <reified T> post(path: String, body: T): T =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}$path")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(json.encodeToString(body).toRequestBody(JSON_MEDIA))
                .build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("FlyPush API error ${res.code}")
                json.decodeFromString(res.body!!.string())
            }
        }

    private suspend inline fun <reified T> put(path: String, body: T) =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}$path")
                .addHeader("Authorization", "Bearer $apiKey")
                .put(json.encodeToString(body).toRequestBody(JSON_MEDIA))
                .build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("FlyPush API error ${res.code}")
            }
        }

    private suspend fun delete(path: String) =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}$path")
                .addHeader("Authorization", "Bearer $apiKey")
                .delete()
                .build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("FlyPush API error ${res.code}")
            }
        }
}
