# HiddenNoir

HiddenNoir is the native Android implementation of SPEC-1: an 18+,
privacy-first AI dating reply assistant.

Official repository target:

`https://github.com/mrjohndowe/HiddenNoir.git`

## Current stage

Milestone 1 — Project Foundation.

This revision includes:
- Android app ID `com.hiddennoir.app`
- Jetpack Compose UI
- 18+ gate
- conversation paste screen
- relationship, tone, and intensity selectors
- explicit-mode adult attestation
- anonymous session bootstrap
- Android-to-Ktor networking
- bearer-token validation
- Redis session store
- Flyway baseline migration
- PostgreSQL + Redis Docker services
- API tests
- CI skeleton

Real LLM inference and production safety classification are M2/M3 work.

## Local prerequisites

- JDK 17
- Android SDK 37 / Android Studio
- Gradle 9.5.0
- Docker

## First setup

```bash
gradle wrapper --gradle-version 9.5.0
docker compose up -d
./gradlew :api:test
./gradlew :api:run
```

Open the repo in Android Studio and run `:app-android:app`.

The emulator debug API URL is `http://10.0.2.2:8080`.

To attach the GitHub remote:

```bash
bash scripts/connect-github.sh
```
