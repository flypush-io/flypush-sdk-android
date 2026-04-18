// Offline message queue — persists up to 100 messages in SharedPreferences

package io.flypush.sdk

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PREFS_NAME = "flypush_queue"
private const val KEY_QUEUE = "queue"
private const val MAX_SIZE = 100

internal class LocalMessageQueue(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun push(message: FlyPushMessage) {
        val current = loadAll().toMutableList()
        current.add(message)
        if (current.size > MAX_SIZE) current.removeAt(0) // drop oldest
        save(current)
    }

    @Synchronized
    fun drainAll(): List<FlyPushMessage> {
        val messages = loadAll()
        prefs.edit().remove(KEY_QUEUE).apply()
        return messages
    }

    private fun loadAll(): List<FlyPushMessage> {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<FlyPushMessage>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(messages: List<FlyPushMessage>) {
        prefs.edit().putString(KEY_QUEUE, json.encodeToString(messages)).apply()
    }
}
