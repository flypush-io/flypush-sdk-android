// Data classes for incoming push messages and SDK configuration

package io.flypush.sdk

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class FlyPushMessage(
    val id: String,
    val title: String,
    val body: String,
    val imageUrl: String? = null,
    val data: JsonObject? = null,
)

@Serializable
internal data class RegisterRequest(
    val platform: String = "ANDROID",
    val token: String,
    val userId: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
internal data class RegisterResponse(
    val id: String,
    val token: String,
)

@Serializable
internal data class UpdateRequest(
    val userId: String? = null,
    val tags: List<String>? = null,
)

@Serializable
internal data class SubscribeRequest(
    val topic: String,
)

@Serializable
internal data class WsMessage(
    val type: String,
    val id: String? = null,
    val payload: FlyPushMessage? = null,
)
