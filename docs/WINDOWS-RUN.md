# HiddenNoir Control Center on Windows

Open PowerShell in the project folder and run `.\run_server.ps1`. Use **Build debug APK** to build without a phone, or **Build and install on the connected device** for one authorized device. **Clean build and update** rebuilds the Android module and installs over the current app, retaining its data if the signing key matches.

See [Build HiddenNoir](BUILD.md) for JDK and Android SDK setup. For the install options, set `ANDROID_HOME` to the SDK folder if it is outside the standard Windows location and enable Android debugging on the device.

Open the installed app and [import a local GGUF model](../README.md#first-run). Debugging is only needed for installing through the Control Center.

The desktop API actions are grouped separately for legacy use with Ollama.
