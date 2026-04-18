// Interface for custom notification rendering

package io.flypush.sdk

import android.app.Notification
import android.content.Context

interface NotificationHandler {
    /**
     * Called when a push message arrives. Return a [Notification] to display it,
     * or null to suppress display (handle it yourself).
     */
    fun onMessage(context: Context, message: FlyPushMessage): Notification?
}
