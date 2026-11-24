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

internal data class ScreenText(val blocks: List<String>, val conversation: String? = null)

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

        suspend fun readScreen(packageName: String, imageText: Boolean): ScreenText {
            val service = connected ?: error("Enable HiddenNoir Read Screen in Accessibility settings.")
            if (!imageText && packageName == GOOGLE_MESSAGES) {
                val thread = read(packageName)
                if (thread is ThreadRead.Readable) return ScreenText(emptyList(), thread.text)
            }
            val windows = service.windows
            val window = windows.firstOrNull {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused &&
                    it.root?.packageName?.toString() == packageName
            } ?: error("The app is no longer in focus. Tap its compose field and try again.")
            val root = window.root ?: error("Android could not read this screen.")
            val bounds = Rect().also(window::getBoundsInScreen)
            windows.filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }.forEach {
                val keyboard = Rect().also(it::getBoundsInScreen)
                if (Rect.intersects(bounds, keyboard) && keyboard.top > bounds.top) {
                    bounds.bottom = minOf(bounds.bottom, keyboard.top)
                }
            }
            check(!bounds.isEmpty) { "The app is covered. Hide any overlays and try again." }
            if (!imageText) {
                val blocks = root.screenNodes().mapNotNull { node ->
                    if (node.packageName?.toString() != packageName || !node.isVisibleToUser) return@mapNotNull null
                    val text = node.text?.toString()?.trim()
                        ?: node.contentDescription?.toString()?.trim()?.takeIf { node.childCount == 0 }
                    if (text.isNullOrBlank()) return@mapNotNull null
                    val rect = Rect().also(node::getBoundsInScreen)
                    if (!Rect.intersects(bounds, rect)) return@mapNotNull null
                    Triple(rect.top, rect.left, text)
                }.sortedWith(compareBy({ it.first }, { it.second }))
                    .map { it.third }.distinct().take(40).toList()
                if (blocks.isNotEmpty()) return ScreenText(blocks)
            }
            val blocks = ScreenTextReader.readImage(service, bounds).take(40)
            check(service.windows.any { it.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                it.isFocused && it.root?.packageName?.toString() == packageName }) {
                "The app changed while reading. Tap its compose field and try again."
            }
            check(blocks.isNotEmpty()) { "No readable text was found. Try a clearer screen or use Copied." }
            return ScreenText(blocks)
        }

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
                val outgoing = outgoingDescription.containsMatchIn(node.contentDescription?.toString().orEmpty()) ||
                    bounds.left - listBounds.left > listBounds.right - bounds.right
                val speaker = if (outgoing) "You" else "Them"
                bounds.top to "$speaker: ${node.text.toString().trim().replace('\n', ' ').take(400)}"
            }.sortedBy { it.first }.map { it.second }.toList()
            val newestIncoming = messages.indexOfLast { it.startsWith("Them: ") }
            if (newestIncoming < 0) return ThreadRead.Unavailable
            // Preserve the newest incoming message even after several outgoing replies.
            val recent = messages.takeLast(12)
            val context = if (newestIncoming < messages.size - recent.size) {
                listOf(messages[newestIncoming]) + recent.drop(1)
            } else recent
            return ThreadRead.Readable(context.joinToString("\n"))
        }

        private fun AccessibilityNodeInfo.hasId(id: String): Boolean =
            viewIdResourceName?.substringAfterLast('/') == id

        private val outgoingDescription = Regex("^You(?:\\s+(?:said|sent)|[:,.])", RegexOption.IGNORE_CASE)

        private fun AccessibilityNodeInfo.descendants(): Sequence<AccessibilityNodeInfo> = sequence {
            yield(this@descendants)
            for (index in 0 until childCount) getChild(index)?.let { yieldAll(it.descendants()) }
        }

        private fun AccessibilityNodeInfo.screenNodes(): Sequence<AccessibilityNodeInfo> = sequence {
            if (!isEditable && !isPassword) {
                yield(this@screenNodes)
                for (index in 0 until childCount) getChild(index)?.let { yieldAll(it.screenNodes()) }
            }
        }
    }
}
