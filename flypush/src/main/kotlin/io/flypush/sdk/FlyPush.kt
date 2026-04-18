// Main SDK singleton — call FlyPush.init() once in Application.onCreate()

package io.flypush.sdk

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*

private const val TAG = "FlyPush"
private const val PREFS_DEVICE = "flypush_device"
private const val KEY_DEVICE_ID = "device_id"

data class FlyPushConfig(
    val apiKey: String,
    val baseUrl: String = "https://api.flypush.io",
    val wsUrl: String = "wss://push.flypush.io",
)

object FlyPush {
    internal var config: FlyPushConfig? = null
    internal var deviceId: String? = null
    internal var notificationHandler: NotificationHandler? = null
    internal var localQueue: LocalMessageQueue? = null

    private var api: FlyPushApi? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Initialize the SDK. Call once in Application.onCreate().
     */
    fun init(context: Context, apiKey: String, baseUrl: String = "https://api.flypush.io") {
        val cfg = FlyPushConfig(apiKey = apiKey, baseUrl = baseUrl)
        config = cfg
        localQueue = LocalMessageQueue(context.applicationContext)
        api = FlyPushApi(apiKey, baseUrl)

        // Restore persisted deviceId
        val prefs = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        deviceId = prefs.getString(KEY_DEVICE_ID, null)

        startService(context)
    }

    /**
     * Register this device with FlyPush. Call after init().
     * Stores the deviceId returned by the server for future API calls.
     */
    fun registerDevice(context: Context, userId: String? = null) {
        val token = deviceId ?: generateLocalToken(context)
        scope.launch {
            runCatching {
                val res = api!!.registerDevice(token, userId)
                deviceId = res.id
                context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
                    .edit().putString(KEY_DEVICE_ID, res.id).apply()
                Log.d(TAG, "Device registered: ${res.id}")
            }.onFailure { Log.e(TAG, "registerDevice failed", it) }
        }
    }

    /**
     * Update tags or userId for this device.
     */
    fun setTags(tags: Map<String, String>) {
        val id = deviceId ?: return
        scope.launch {
            runCatching {
                api!!.updateDevice(id, null, tags.values.toList())
            }.onFailure { Log.e(TAG, "setTags failed", it) }
        }
    }

    /**
     * Subscribe this device to a topic.
     */
    fun subscribe(topic: String) {
        val id = deviceId ?: return
        scope.launch {
            runCatching { api!!.subscribe(id, topic) }
                .onFailure { Log.e(TAG, "subscribe failed", it) }
        }
    }

    /**
     * Unsubscribe this device from a topic.
     */
    fun unsubscribe(topic: String) {
        val id = deviceId ?: return
        scope.launch {
            runCatching { api!!.unsubscribe(id, topic) }
                .onFailure { Log.e(TAG, "unsubscribe failed", it) }
        }
    }

    /**
     * Unregister this device entirely.
     */
    fun unregisterDevice(context: Context) {
        val id = deviceId ?: return
        scope.launch {
            runCatching { api!!.unregisterDevice(id) }
                .onFailure { Log.e(TAG, "unregisterDevice failed", it) }
        }
        deviceId = null
        context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
            .edit().remove(KEY_DEVICE_ID).apply()
    }

    /**
     * Provide a custom notification renderer. Called on every incoming message.
     */
    fun setNotificationHandler(handler: NotificationHandler) {
        notificationHandler = handler
    }

    private fun startService(context: Context) {
        val intent = Intent(context.applicationContext, FlyPushService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.applicationContext.startForegroundService(intent)
        } else {
            context.applicationContext.startService(intent)
        }
    }

    private fun generateLocalToken(context: Context): String {
        val token = java.util.UUID.randomUUID().toString()
        context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
            .edit().putString(KEY_DEVICE_ID, token).apply()
        deviceId = token
        return token
    }
}
