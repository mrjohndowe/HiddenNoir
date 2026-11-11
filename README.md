# HiddenNoir

HiddenNoir is an 18+ Android reply studio. It runs llama.cpp directly on the device and saves the current draft, replies, and preferences in app-private storage. Use **Clear draft** to remove the saved conversation and replies.

## First run

1. Put a compatible GGUF text instruction model in the phone's Downloads folder. A small starting point is [Qwen2.5-0.5B-Instruct Q4_K_M](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF).
2. Open HiddenNoir, confirm you are 18 or older, and tap **Import GGUF model**.
3. Select the file. The app validates it and copies it into private storage. Allow space for both copies during setup. After import succeeds, you can remove the original from Downloads.
4. Paste messages, choose a direction, and tap **Generate replies**. With the GGUF already on the phone, import and generation can run in airplane mode.

The APK supports Android 9 and newer on ARM64 and x86-64. Choose an unsplit text GGUF with a chat template supported by llama.cpp. If the GGUF lacks a template and uses the standard Llama 3 chat tokens, HiddenNoir applies the Llama 3 template. Start with a small non-reasoning model. Larger models need more memory and can be slow.

## Reply from the keyboard

In HiddenNoir, tap **Enable keyboard**, turn on HiddenNoir Keyboard in Android settings, and use **Switch keyboard** to select it. **Message preview access** is optional. If enabled, tap **Messages** in the keyboard to choose an active conversation preview from the messaging app you are using. Apps that hide previews or lack message-style notifications can use **Copied** after you copy a message. **Selected** uses the selected text in the compose field.

Tap **Generate replies**, then tap a reply to insert it into the compose field. Send it from your messaging app when ready. The keyboard runs the imported GGUF locally, even when the HiddenNoir screen is closed. It reads previews on demand, keeps them in memory while the keyboard is open, and can access only the messages the app exposes in active notifications.

See [Build](docs/BUILD.md) and [Windows Control Center](docs/WINDOWS-RUN.md). The separate desktop API supports Ollama for legacy use.
