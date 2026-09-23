# HiddenNoir v0.3 — Ollama + SQLite

HiddenNoir is an 18+ privacy-first Android dating reply assistant.

This development build removes Docker, PostgreSQL, and Redis.

Local architecture:

```text
Physical Android phone
        |
        | adb reverse tcp:8081
        v
Ktor API on Windows PC :8081
        |
        +--> SQLite file (metadata only)
        |
        +--> Ollama :11434
                  |
                  v
              qwen3:8b
```

Raw pasted conversation text is not written to SQLite by this application.

## Requirements

- Windows 10/11
- JDK 17
- Android Studio / Android SDK
- USB debugging enabled on Android phone
- Ollama installed
- Gradle wrapper already generated in your working repository

## Ollama model

Default:

```powershell
ollama pull qwen3:8b
```

Override the model with:

```powershell
$env:OLLAMA_MODEL="another-model"
```

## Run

From the project root:

```powershell
adb reverse tcp:8081 tcp:8081
ollama list
.\gradlew.bat :api:run
```

Then run/install the Android app from Android Studio and tap Generate.

## SQLite

Default file:

```text
data/hiddennoir.db
```

Override:

```powershell
$env:HIDDENNOIR_DB_PATH="G:\somewhere\hiddennoir.db"
```

The database stores sessions and generation metadata only, not pasted
conversation text or generated reply bodies.

## Development status

This is a functional local-development slice. Before public production release,
the model/safety layer still needs a dedicated regression suite, production
authentication, payment integration, hardened rate limiting, and deployment
infrastructure.
