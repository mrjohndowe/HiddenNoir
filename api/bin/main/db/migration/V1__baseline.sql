CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) UNIQUE,
    age_attested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    default_tone VARCHAR(32),
    relationship_context VARCHAR(32),
    explicit_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    save_history BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    processor VARCHAR(32) NOT NULL,
    processor_customer_id VARCHAR(255),
    processor_subscription_id VARCHAR(255) UNIQUE,
    status VARCHAR(32) NOT NULL,
    current_period_end TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS generation_events (
    id UUID PRIMARY KEY,
    user_id UUID,
    anonymous_id_hash VARCHAR(255),
    mode VARCHAR(32) NOT NULL,
    input_character_count INTEGER NOT NULL,
    returned_reply_count INTEGER NOT NULL,
    policy_result VARCHAR(64) NOT NULL,
    model_version VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(128),
    classifier_version VARCHAR(128),
    latency_ms INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS billing_events (
    id UUID PRIMARY KEY,
    provider_event_id VARCHAR(255) UNIQUE NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_hash VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
