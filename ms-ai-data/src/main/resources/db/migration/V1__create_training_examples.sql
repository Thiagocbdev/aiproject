CREATE TABLE training_examples (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    response TEXT NOT NULL,
    rating INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_training_examples_provider ON training_examples(provider);
CREATE INDEX idx_training_examples_created_at ON training_examples(created_at DESC);

CREATE TABLE training_example_tools (
    example_id UUID REFERENCES training_examples(id),
    tool VARCHAR(100)
);
