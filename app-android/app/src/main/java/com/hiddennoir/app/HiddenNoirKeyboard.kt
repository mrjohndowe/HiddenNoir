package com.hiddennoir.app

import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class HiddenNoirKeyboard : InputMethodService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val model by lazy { LocalModel(this) }
    private val preferences by lazy { getSharedPreferences("studio", MODE_PRIVATE) }
    private var generation: Job? = null
    private var panel: LinearLayout? = null
    private var options: ScrollView? = null
    private var status: TextView? = null
    private var tools: LinearLayout? = null
    private var keys: LinearLayout? = null
    private var direction: Button? = null
    private var shifted = false
    private var symbols = false

    override fun onCreate() {
        setTheme(R.style.Theme_HiddenNoir_Keyboard)
        super.onCreate()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View = column().apply {
        setBackgroundColor(NoirPalette.background)
        setPadding(dp(4), dp(4), dp(4), dp(4))
        val toolbar = LinearLayout(context)
        tools = LinearLayout(context).also { actions ->
            actions.addView(button("Thread", ::showThread), weighted())
            actions.addView(button("Messages", ::showMessages), weighted())
            actions.addView(button("Copied", ::useClipboard), weighted())
            actions.addView(button("Selected", ::useSelection), weighted())
            toolbar.addView(actions, weighted())
        }
        toolbar.addView(button("Switch") {
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        })
        addView(toolbar)
        direction = button("", {
            startActivity(Intent(this@HiddenNoirKeyboard, MainActivity::class.java)
                .putExtra(OPEN_REPLY_DIRECTION, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }, highlighted = true).also { addView(it, LinearLayout.LayoutParams(-1, -2)) }
        status = TextView(context).also {
            it.setTextColor(NoirPalette.muted)
            it.maxLines = 3
            it.setPadding(dp(8), 0, dp(8), 0)
            addView(it)
        }
        panel = column().also {
            val scroll = ScrollView(context).apply {
                setBackgroundColor(NoirPalette.surface)
                addView(it)
            }
            options = scroll
            addView(scroll, LinearLayout.LayoutParams(-1, dp(124)))
            scroll.visibility = View.GONE
        }
        keys = column().also { addView(it) }
        renderKeys()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        clearContext()
        val enabled = allowsReplies(info)
        tools?.visibility = if (enabled) View.VISIBLE else View.GONE
        direction?.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) updateDirection(readDirection())
        showStatus(if (enabled) "Read the open thread, choose a preview, or use copied text." else "")
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        clearContext()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun showThread() {
        val editor = currentInputEditorInfo ?: return
        val connection = currentInputConnection ?: return
        if (!allowsReplies(editor)) return
        clearContext()
        showStatus("Reading the open thread…")
        generation = scope.launch {
            val thread = try {
                withContext(Dispatchers.Default) { ConversationAccess.read(editor.packageName) }
            } catch (_: SecurityException) {
                ThreadRead.Unavailable
            }
            if (currentInputConnection !== connection || !allowsReplies(currentInputEditorInfo)) return@launch
            generation = null
            when (thread) {
                ThreadRead.Disabled -> showStatus("Enable Read open thread in HiddenNoir’s settings first.")
                ThreadRead.Unavailable -> showStatus("No readable messages in this view. Open a Google Messages thread or use Copied.")
                is ThreadRead.Readable -> chooseConversation(thread.text)
            }
        }
    }

    private fun showMessages() {
        val editor = currentInputEditorInfo ?: return
        if (!allowsReplies(editor)) return
        clearContext()
        val messages = MessageNotifications.forPackage(editor.packageName)
        if (messages.isNullOrEmpty()) {
            showStatus(if (messages == null) "Enable Message preview access in HiddenNoir, or copy a message." else "No active message previews for this app. You can copy a message instead.")
            return
        }
        showStatus("Choose the preview that matches this conversation.")
        messages.forEach { preview ->
            val latest = preview.conversation.lineSequence().last { it.startsWith("Them: ") }
            addOption("${preview.label}: ${latest.removePrefix("Them: ").take(75)}") {
                chooseConversation(preview.conversation)
            }
        }
    }

    private fun useClipboard() {
        if (!allowsReplies(currentInputEditorInfo)) return
        clearContext()
        val clip = getSystemService(ClipboardManager::class.java).primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
        if (text.isNullOrBlank()) showStatus("Copy a message or conversation first.")
        else chooseConversation(text)
    }

    private fun useSelection() {
        if (!allowsReplies(currentInputEditorInfo)) return
        val text = currentInputConnection?.getSelectedText(0)?.toString()
        if (text.isNullOrBlank()) {
            clearContext()
            showStatus("Select text in the compose field first.")
        } else {
            chooseConversation(text)
        }
    }

    private fun chooseConversation(text: String) {
        clearContext()
        val settings = readDirection()
        updateDirection(settings)
        val input = settings.copy(conversation = text.trim().takeLast(1600), replies = emptyList())
        showStatus("Review the conversation below before generating.")
        val explicit = input.intensity == Intensity.EXPLICIT
        addOption(if (explicit) "Everyone is 18+ · Generate replies" else "Generate replies") {
            generate(input, explicit)
        }
        panel?.addView(TextView(this).apply {
            this.text = input.conversation
            setTextColor(NoirPalette.lavender)
            setPadding(dp(8), dp(4), dp(8), dp(8))
            textSize = 14f
        })
    }

    private fun readDirection(): Draft = try {
        preferences.getString("draft", null)?.let { Json.decodeFromString<Draft>(it) } ?: Draft()
    } catch (_: SerializationException) {
        Draft()
    }

    private fun updateDirection(settings: Draft) {
        direction?.text = "${settings.relationship.label} · ${settings.tone.label} · ${settings.intensity.label}"
        direction?.contentDescription = "Studio reply settings. Relationship: ${settings.relationship.label}. " +
            "Tone: ${settings.tone.label}. Intensity: ${settings.intensity.label}. Tap to edit in HiddenNoir."
    }

    private fun generate(input: Draft, adultsConfirmed: Boolean) {
        val connection = currentInputConnection ?: return
        if (!allowsReplies(currentInputEditorInfo) || generation?.isActive == true) return
        if (!preferences.getBoolean("adult_gate", false) || !model.isInstalled()) {
            showStatus("Open HiddenNoir, confirm your age, and import a GGUF model first.")
            return
        }
        clearOptions()
        showStatus("Generating on this device…")
        generation = scope.launch {
            try {
                val replies = withContext(Dispatchers.Default) { model.generate(input, adultsConfirmed) }
                if (currentInputConnection !== connection || !allowsReplies(currentInputEditorInfo)) return@launch
                showStatus("Tap a reply to insert it.")
                replies.forEach { reply ->
                    addOption(reply) {
                        if (currentInputConnection === connection && allowsReplies(currentInputEditorInfo) &&
                            connection.commitText(reply, 1)) {
                            clearContext()
                            showStatus("Reply inserted.")
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                showStatus(failure.message ?: "Could not generate replies.")
            } catch (_: LinkageError) {
                showStatus("Could not load the local inference engine.")
            }
        }
    }

    private fun allowsReplies(info: EditorInfo?): Boolean {
        val input = info?.inputType ?: return false
        if (input and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT ||
            info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return false
        return when (input and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> false
            else -> true
        }
    }

    private fun clearContext() {
        generation?.cancel()
        generation = null
        clearOptions()
    }

    private fun clearOptions() {
        panel?.removeAllViews()
        options?.scrollTo(0, 0)
        options?.visibility = View.GONE
    }

    private fun addOption(label: String, action: () -> Unit) {
        panel?.addView(button(label, action, highlighted = true))
        options?.visibility = View.VISIBLE
    }

    private fun showStatus(text: String) {
        status?.text = text
        status?.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun renderKeys() {
        val layout = keys ?: return
        layout.removeAllViews()
        val rows = if (symbols) listOf("1234567890", "@#&-_+()", "!?/:;") else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        rows.forEachIndexed { index, letters ->
            val row = LinearLayout(this)
            if (index == 2 && !symbols) {
                row.addView(button("⇧") { shifted = !shifted; renderKeys() }, weighted())
            }
            letters.forEach { letter ->
                val key = if (shifted && !symbols) letter.uppercase() else letter.toString()
                row.addView(button(key) {
                    currentInputConnection?.commitText(key, 1)
                    if (shifted && !symbols) {
                        shifted = false
                        renderKeys()
                    }
                }, weighted())
            }
            if (index == 2) row.addView(button("⌫", ::backspace), weighted())
            layout.addView(row)
        }
        layout.addView(LinearLayout(this).apply {
            addView(button(if (symbols) "ABC" else "?123") { symbols = !symbols; renderKeys() }, weighted())
            addView(button(",") { currentInputConnection?.commitText(",", 1) }, weighted())
            addView(button("Space") { currentInputConnection?.commitText(" ", 1) }, weighted(4f))
            addView(button(".") { currentInputConnection?.commitText(".", 1) }, weighted())
            addView(button("↵") { currentInputConnection?.commitText("\n", 1) }, weighted())
        })
    }

    private fun backspace() {
        val connection = currentInputConnection ?: return
        if (!connection.getSelectedText(0).isNullOrEmpty()) connection.commitText("", 1)
        else connection.deleteSurroundingTextInCodePoints(1, 0)
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun button(label: String, action: () -> Unit) = button(label, action, highlighted = false)

    private fun button(label: String, action: () -> Unit, highlighted: Boolean) = Button(this).apply {
        text = label
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        minHeight = dp(40)
        minimumHeight = dp(40)
        setTextColor(NoirPalette.lavender)
        textSize = 13f
        setPadding(dp(3), dp(2), dp(3), dp(2))
        background = RippleDrawable(
            ColorStateList.valueOf(NoirPalette.violet),
            GradientDrawable().apply {
                setColor(if (highlighted) NoirPalette.raised else NoirPalette.surface)
                setStroke(dp(1), if (highlighted) NoirPalette.violet else NoirPalette.border)
                cornerRadius = dp(8).toFloat()
            },
            null
        )
        setOnClickListener { action() }
    }

    private fun weighted(weight: Float = 1f) = LinearLayout.LayoutParams(0, -2, weight).apply {
        setMargins(dp(2), dp(2), dp(2), dp(2))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
