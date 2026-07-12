CREATE TABLE sessions (
    id          VARCHAR(36) PRIMARY KEY,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    ended_at    TIMESTAMP
);

CREATE TABLE conversation_turns (
    id          BIGSERIAL PRIMARY KEY,
    session_id  VARCHAR(36) NOT NULL REFERENCES sessions(id),
    question    TEXT NOT NULL,
    use_context BOOLEAN NOT NULL DEFAULT FALSE,
    turn_number INT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conv_turns_session ON conversation_turns(session_id);
CREATE INDEX idx_conv_turns_created ON conversation_turns(created_at);

CREATE TABLE turn_responses (
    id            BIGSERIAL PRIMARY KEY,
    turn_id       BIGINT NOT NULL REFERENCES conversation_turns(id),
    provider      VARCHAR(20) NOT NULL,
    response_text TEXT,
    tokens_in     INT NOT NULL DEFAULT 0,
    tokens_out    INT NOT NULL DEFAULT 0,
    cache_hit     BOOLEAN NOT NULL DEFAULT FALSE,
    rag_used      BOOLEAN NOT NULL DEFAULT FALSE,
    duration_ms   BIGINT,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_turn_resp_turn     ON turn_responses(turn_id);
CREATE INDEX idx_turn_resp_provider ON turn_responses(provider);
CREATE INDEX idx_turn_resp_created  ON turn_responses(created_at DESC);
CREATE INDEX idx_turn_resp_cache    ON turn_responses(cache_hit);

CREATE TABLE turn_response_tools (
    response_id BIGINT REFERENCES turn_responses(id),
    tool        VARCHAR(100)
);
