# FlyPush Android SDK

FlyPush push notifications for Android — persistent WebSocket transport, no FCM dependency.

## Installation

Add to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.flypush:sdk-android:0.1.0")
}
```

## Quick start

### 1. Initialize in `Application.onCreate()`

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FlyPush.init(this, apiKey = "fp_your_api_key")
    }
}
```

### 2. Register the device (after requesting notification permission)

```kotlin
// In your Activity/Fragment, after user grants POST_NOTIFICATIONS permission:
FlyPush.registerDevice(context, userId = "user_123")
```

### 3. Request battery optimization exemption (recommended)

```kotlin
// In your main Activity:
BatteryOptimization.requestExemption(this)
```

## Topics

```kotlin
FlyPush.subscribe("breaking-news")
FlyPush.unsubscribe("breaking-news")
```

## Tags

```kotlin
FlyPush.setTags(mapOf("plan" to "premium", "region" to "us"))
```

## Custom notification handling

```kotlin
FlyPush.setNotificationHandler(object : NotificationHandler {
    override fun onMessage(context: Context, message: FlyPushMessage): Notification? {
        // Build and return your custom Notification, or return null to suppress
        return Notification.Builder(context, "your_channel")
            .setContentTitle(message.title)
            .setContentText(message.body)
            .build()
    }
})
```

## Unregister

```kotlin
FlyPush.unregisterDevice(context)
```

## How it works

- `FlyPush.init()` starts a **foreground service** that maintains a persistent WebSocket to `wss://push.flypush.io`
- On reconnect, messages queued while offline (up to 100) are drained from local storage
- Exponential backoff reconnect: 1s → 2s → 4s → … → 60s max
- Heartbeat: ping every 30s, close connection if pong not received within 10s
- The service restarts automatically after device reboot via `BootReceiver`

## Permissions

The SDK declares these permissions in its manifest (merged automatically):

| Permission | Why |
|---|---|
| `INTERNET` | WebSocket connection |
| `FOREGROUND_SERVICE` | Persistent service |
| `POST_NOTIFICATIONS` | Show notifications (Android 13+) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Keep connection alive |
| `RECEIVE_BOOT_COMPLETED` | Restart service after reboot |

## Requirements

- Android API 21+ (Android 5.0 Lollipop)
- Kotlin 1.9+
