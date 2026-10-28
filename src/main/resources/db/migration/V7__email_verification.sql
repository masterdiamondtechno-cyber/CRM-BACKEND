ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE registration_verification_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    used_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_pending_registration_verification_user
    ON registration_verification_tokens (user_id)
    WHERE used_at IS NULL;
CREATE INDEX idx_registration_verification_expiry
    ON registration_verification_tokens (expires_at)
    WHERE used_at IS NULL;
