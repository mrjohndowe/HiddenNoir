# HiddenNoir Development Build

1. Generate wrapper:
   `gradle wrapper --gradle-version 9.5.0`

2. Start dependencies:
   `docker compose up -d`

3. Test API:
   `./gradlew :api:test`

4. Start API:
   `./gradlew :api:run`

5. Open in Android Studio and run `:app-android:app`.

Development acceptance:
- HiddenNoir branding appears.
- 18+ gate works.
- User can paste a conversation.
- Relationship/tone/intensity controls work.
- Explicit mode requires 18+ attestation.
- Android creates an anonymous session.
- Android sends authenticated generation request.
- Backend returns four placeholder replies.
