-- Tenant owner onboarding. Membership roles remain tenant-scoped; users.role
-- stays a legacy compatibility field and does not grant platform authority.
ALTER TABLE tenants ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE tenant_invitations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    invited_email VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'MANAGER', 'EMPLOYEE')),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT fk_tenant_invitation_creator
        FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_memberships (tenant_id, user_id)
);

CREATE UNIQUE INDEX uq_pending_tenant_invitation_email
    ON tenant_invitations (tenant_id, lower(invited_email))
    WHERE accepted_at IS NULL AND revoked_at IS NULL;
CREATE INDEX idx_tenant_invitations_expiry
    ON tenant_invitations (tenant_id, expires_at)
    WHERE accepted_at IS NULL AND revoked_at IS NULL;
