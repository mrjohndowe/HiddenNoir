# Build HiddenNoir

Install JDK 17. Set `JAVA_HOME` to its installation directory or select JDK 17 as the Gradle JDK in Android Studio. The separate desktop API also uses a Java 17 compilation toolchain.

In Android Studio's SDK Manager, install the Android 37 SDK, NDK (Side by side) 29.0.14206865, CMake 3.31.6, and Android SDK Platform-Tools. Set the SDK location in `local.properties` or set `ANDROID_HOME`.

If Java rejects a certificate that Windows already trusts, set `JAVA_TOOL_OPTIONS` to `-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE` for the build session. This keeps certificate validation enabled.

From the repository root, run `gradlew.bat :app-android:app:assembleDebug` on Windows or `./gradlew :app-android:app:assembleDebug` on macOS or Linux. The APK is at `app-android/app/build/outputs/apk/debug/app-debug.apk`. The first build downloads dependencies and a checksum-pinned llama.cpp source archive. Model weights are imported separately.

## Check on a device

- Install and launch the APK. Confirm Generate is disabled until a model is imported.
- Put a supported GGUF in Downloads. Turn on airplane mode, then import it and generate replies.
- Try the copy control, tone and intensity settings, and 18+ confirmation for Explicit mode.
- Force-stop and reopen the app. Check that the model, draft, replies, and selections are available. Clear the draft and check that the conversation and replies are removed.
- Try an invalid file and verify the existing model is still available.
