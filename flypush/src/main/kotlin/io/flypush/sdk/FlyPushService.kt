// Foreground service — maintains persistent WebSocket, reconnects with exponential backoff

package io.flypush.sdk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.*
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

private const val TAG = "FlyPushService"
private const val CHANNEL_ID = "flypush_service"
private const val NOTIF_ID = 1
private const val PING_INTERVAL_MS = 30_000L
private const val PONG_TIMEOUT_MS = 10_000L
private const val MAX_BACKOFF_MS = 60_000L
private const val BASE_BACKOFF_MS = 1_000L

internal class FlyPushService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private var socket: WebSocket? = null
    private var reconnectAttempt = 0
    private var pingJob: Job? = null
    private var pongTimeoutJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildForegroundNotification())
        scope.launch { connect() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        socket?.close(1000, "Service destroyed")
        super.onDestroy()
    }

    private suspend fun connect() {
        val config = FlyPush.config ?: return
        val deviceId = FlyPush.deviceId ?: return

        if (reconnectAttempt > 0) {
            val delayMs = min(
                BASE_BACKOFF_MS * 2.0.pow(reconnectAttempt - 1).toLong(),
                MAX_BACKOFF_MS
            )
            Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempt)")
            delay(delayMs)
        }

        val wsUrl = config.wsUrl + "?token=$deviceId"
        val client = OkHttpClient.Builder()
            .pingInterval(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder().url(wsUrl).build()

        withContext(Dispatchers.Main) {
            socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connected")
                    reconnectAttempt = 0
                    startHeartbeat(ws)
                    drainOfflineQueue(ws)
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleMessage(ws, text)
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "WebSocket failure: ${t.message}")
                    stopHeartbeat()
                    reconnectAttempt++
                    scope.launch { connect() }
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $reason")
                    stopHeartbeat()
                    if (code != 1000) {
                        reconnectAttempt++
                        scope.launch { connect() }
                    }
                }
            })
        }
    }

    private fun handleMessage(ws: WebSocket, text: String) {
        val msg = try {
            json.decodeFromString<WsMessage>(text)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse WS message: $text")
            return
        }

        when (msg.type) {
            "pong" -> {
                pongTimeoutJob?.cancel()
            }
            "notification" -> {
                val payload = msg.payload ?: return
                // ACK back to server
                ws.send(json.encodeToString(WsMessage.serializer(), WsMessage(type = "ack", id = msg.id)))
                dispatchMessage(payload)
            }
        }
    }

    private fun dispatchMessage(message: FlyPushMessage) {
        val handler = FlyPush.notificationHandler
        val notif: Notification? = if (handler != null) {
            handler.onMessage(this, message)
        } else {
            buildDefaultNotification(message)
        }

        if (notif != null) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(message.id.hashCode(), notif)
        }
    }

    private fun buildDefaultNotification(message: FlyPushMessage): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
    }

    private fun drainOfflineQueue(ws: WebSocket) {
        val queue = FlyPush.localQueue ?: return
        scope.launch(Dispatchers.IO) {
            queue.drainAll().forEach { msg -> dispatchMessage(msg) }
        }
    }

    private fun startHeartbeat(ws: WebSocket) {
        pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                ws.send(json.encodeToString(WsMessage.serializer(), WsMessage(type = "ping")))
                pongTimeoutJob = scope.launch {
                    delay(PONG_TIMEOUT_MS)
                    Log.w(TAG, "Pong timeout — closing WebSocket")
                    ws.close(1001, "Pong timeout")
                }
            }
        }
    }

    private fun stopHeartbeat() {
        pingJob?.cancel()
        pongTimeoutJob?.cancel()
    }

    private fun buildForegroundNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Push notifications active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FlyPush Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains push notification connection"
                setShowBadge(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
