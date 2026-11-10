# HiddenNoir

HiddenNoir is an 18+ Android reply studio. It runs llama.cpp directly on the device and saves the current draft, replies, and preferences in app-private storage. Use **Clear draft** to remove the saved conversation and replies.

## First run

1. Put a compatible GGUF text instruction model in the phone's Downloads folder. A small starting point is [Qwen2.5-0.5B-Instruct Q4_K_M](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF).
2. Open HiddenNoir, confirm you are 18 or older, and tap **Import GGUF model**.
3. Select the file. The app validates it and copies it into private storage. Allow space for both copies during setup. After import succeeds, you can remove the original from Downloads.
4. Paste messages, choose a direction, and tap **Generate replies**. With the GGUF already on the phone, import and generation can run in airplane mode.

The APK supports Android 9 and newer on ARM64 and x86-64. Choose an unsplit text GGUF with a chat template supported by llama.cpp. Start with a small non-reasoning model. Larger models need more memory and can be slow.

See [Build](docs/BUILD.md) and [Windows Control Center](docs/WINDOWS-RUN.md). The separate desktop API supports Ollama for legacy use.
