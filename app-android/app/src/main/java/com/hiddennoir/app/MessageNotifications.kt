package com.hiddennoir.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationCompat

internal data class MessagePreview(val label: String, val conversation: String)

class MessageNotifications : NotificationListenerService() {
    override fun onListenerConnected() {
        connected = this
    }

    override fun onListenerDisconnected() {
        if (connected === this) connected = null
    }

    override fun onDestroy() {
        if (connected === this) connected = null
        super.onDestroy()
    }

    internal companion object {
        private var connected: MessageNotifications? = null

        // Called on demand by the keyboard. Keep notification text out of storage.
        fun forPackage(packageName: String): List<MessagePreview>? {
            val listener = connected ?: return null
            val notifications = try {
                listener.activeNotifications.orEmpty()
            } catch (_: SecurityException) {
                return null
            }
            return notifications.asSequence()
                .filter {
                    it.packageName == packageName &&
                        it.notification.category == Notification.CATEGORY_MESSAGE &&
                        it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
                }
                .sortedByDescending { it.postTime }
                .mapNotNull {
                    val notification = it.notification
                    val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                        ?: return@mapNotNull null
                    val messages = style.messages.filter { !it.text.isNullOrBlank() }.takeLast(5)
                    if (messages.isEmpty()) return@mapNotNull null
                    val label = style.conversationTitle ?: messages.last().person?.name ?: "Conversation"
                    val conversation = messages.joinToString("\n") { message ->
                        val sender = message.person?.name ?: "You"
                        "${sender.toString().take(60)}: ${message.text.toString().takeLast(350)}"
                    }
                    MessagePreview(label.toString().take(80), conversation)
                }
                .take(5)
                .toList()
        }
    }
}
