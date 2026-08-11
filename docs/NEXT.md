# Next Build Tasks

## M1 — Foundation completion

### Backend
- Add Flyway database migrations.
- Add PostgreSQL connection pool.
- Add Redis client.
- Implement `POST /v1/sessions/anonymous`.
- Add structured request IDs.
- Add redaction tests.
- Add OpenAPI generation.

### Android
- Add network client/repository.
- Connect Generate to `/v1/generations`.
- Add ViewModel and UI state.
- Add relationship and tone selectors.
- Add error states and tests.

### CI/CD
- Generate and commit Gradle wrapper.
- Run backend tests and Android lint/tests.
- Add dependency and secret scanning.
- Produce a staging APK artifact.

### Infrastructure
- Choose deployment cloud.
- Add Terraform state.
- Add staging API service.
- Add managed Postgres + Redis.
- Add secrets storage.

## M2
Implement the real generation orchestrator and LLM gateway.

## M3
Add the production safety classifier and regression evaluation suite before
explicit generation can be considered production-ready.
