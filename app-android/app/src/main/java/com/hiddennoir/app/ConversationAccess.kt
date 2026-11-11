package com.hiddennoir.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

internal sealed interface ThreadRead {
    data object Disabled : ThreadRead
    data object Unavailable : ThreadRead
    data class Readable(val text: String) : ThreadRead
}

class ConversationAccess : AccessibilityService() {
    override fun onServiceConnected() {
        connected = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (connected === this) connected = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (connected === this) connected = null
        super.onDestroy()
    }

    internal companion object {
        private const val GOOGLE_MESSAGES = "com.google.android.apps.messaging"
        @Volatile private var connected: ConversationAccess? = null

        fun read(packageName: String): ThreadRead {
            if (packageName != GOOGLE_MESSAGES) return ThreadRead.Unavailable
            val service = connected ?: return ThreadRead.Disabled
            val root = service.windows.asSequence()
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }
                .mapNotNull { it.root }
                .firstOrNull { it.packageName?.toString() == GOOGLE_MESSAGES }
                ?: return ThreadRead.Unavailable
            val list = root.descendants().firstOrNull { it.hasId("message_list") && it.isVisibleToUser }
                ?: return ThreadRead.Unavailable
            val listBounds = Rect().also(list::getBoundsInScreen)
            val messages = list.descendants().filter { it.hasId("message_bubble_context") }.mapNotNull { bubble ->
                val node = bubble.descendants().firstOrNull {
                    it.hasId("message_text") && it.isVisibleToUser && !it.text.isNullOrBlank()
                } ?: return@mapNotNull null
                val bounds = Rect().also(bubble::getBoundsInScreen)
                if (!Rect.intersects(listBounds, bounds)) return@mapNotNull null
                val outgoing = node.contentDescription?.startsWith("You", ignoreCase = true) == true ||
                    bounds.left - listBounds.left > listBounds.right - bounds.right
                val speaker = if (outgoing) "You" else "Them"
                bounds.top to "$speaker: ${node.text.toString().trim().replace('\n', ' ').take(400)}"
            }.sortedBy { it.first }.map { it.second }.toList()
            val newestIncoming = messages.indexOfLast { it.startsWith("Them: ") }
            if (newestIncoming < 0) return ThreadRead.Unavailable
            // Preserve the newest incoming message even after several outgoing replies.
            return ThreadRead.Readable(messages.take(newestIncoming + 3).takeLast(12).joinToString("\n"))
        }

        private fun AccessibilityNodeInfo.hasId(id: String): Boolean =
            viewIdResourceName?.substringAfterLast('/') == id

        private fun AccessibilityNodeInfo.descendants(): Sequence<AccessibilityNodeInfo> = sequence {
            yield(this@descendants)
            for (index in 0 until childCount) getChild(index)?.let { yieldAll(it.descendants()) }
        }
    }
}
