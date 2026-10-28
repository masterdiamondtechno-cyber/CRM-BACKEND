-- Phase 1 tenant foundation. Existing rows remain intact and are assigned to
-- one deterministic initial tenant. New tenant onboarding is not enabled yet.
CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(80) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO tenants (id, name, slug, status)
SELECT '00000000-0000-0000-0000-000000000001'::uuid,
       COALESCE(NULLIF(company_name, ''), 'IT Company'), 'initial', 'ACTIVE'
FROM company_settings
WHERE id = 1;

CREATE TABLE tenant_memberships (
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MANAGER', 'EMPLOYEE')),
    membership_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (membership_status IN ('ACTIVE', 'SUSPENDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, user_id)
);

-- Keep every existing account's current effective access. The earliest
-- existing ADMIN becomes the initial tenant owner; no platform role is added.
WITH admins AS (
    SELECT id, row_number() OVER (ORDER BY created_at, id) AS position
    FROM users
    WHERE role = 'ADMIN'
)
INSERT INTO tenant_memberships (tenant_id, user_id, role, membership_status)
SELECT '00000000-0000-0000-0000-000000000001'::uuid,
       u.id,
       CASE WHEN u.role = 'ADMIN' AND a.position = 1 THEN 'OWNER' ELSE u.role END,
       CASE WHEN u.is_active THEN 'ACTIVE' ELSE 'SUSPENDED' END
FROM users u
LEFT JOIN admins a ON a.id = u.id;

CREATE UNIQUE INDEX uq_tenant_one_active_owner
    ON tenant_memberships (tenant_id)
    WHERE role = 'OWNER' AND membership_status = 'ACTIVE';
CREATE INDEX idx_memberships_user_status ON tenant_memberships (user_id, membership_status);

-- A temporary constant default backfills existing rows during these ALTERs.
-- It is removed after validation so future writes must supply verified context.
ALTER TABLE clients ADD COLUMN tenant_id UUID NOT NULL
    DEFAULT '00000000-0000-0000-0000-000000000001'::uuid REFERENCES tenants(id);
ALTER TABLE contacts ADD COLUMN tenant_id UUID NOT NULL
    DEFAULT '00000000-0000-0000-0000-000000000001'::uuid REFERENCES tenants(id);
ALTER TABLE leads ADD COLUMN tenant_id UUID NOT NULL
    DEFAULT '00000000-0000-0000-0000-000000000001'::uuid REFERENCES tenants(id);
ALTER TABLE lead_status_history ADD COLUMN tenant_id UUID NOT NULL
    DEFAULT '00000000-0000-0000-0000-000000000001'::uuid REFERENCES tenants(id);
ALTER TABLE projects ADD COLUMN tenant_id UUID NOT NULL
    DEFAULT '00000000-0000-0000-0000-000000000001'::uuid REFERENCES tenants(id);
ALTER TABLE project_members ADD COLUMN tenant_id UUID NOT NULL
    DEFAULT '00000000-0000-0000-0000-000000000001'::uuid REFERENCES tenants(id);
ALTER TABLE tasks ADD COLUMN tenant_id UUID NOT NULL
    DEFAULT '00000000-0000-0000-0000-000000000001'::uuid REFERENCES tenants(id);
ALTER TABLE activities ADD COLUMN tenant_id UUID NOT NULL
    DEFAULT '00000000-0000-0000-0000-000000000001'::uuid REFERENCES tenants(id);
ALTER TABLE company_settings ADD COLUMN tenant_id UUID NOT NULL
    DEFAULT '00000000-0000-0000-0000-000000000001'::uuid REFERENCES tenants(id);
ALTER TABLE audit_logs ADD COLUMN tenant_id UUID NOT NULL
    DEFAULT '00000000-0000-0000-0000-000000000001'::uuid REFERENCES tenants(id);

-- Settings are no longer structurally limited to a single row. The existing
-- id=1 row is preserved and remains the initial tenant's settings row.
ALTER TABLE company_settings DROP CONSTRAINT company_settings_id_check;
ALTER TABLE company_settings ADD CONSTRAINT uq_company_settings_tenant UNIQUE (tenant_id);

-- Composite candidate keys let foreign keys enforce that related data belongs
-- to the same tenant, even when IDs are supplied or guessed by a caller.
ALTER TABLE clients ADD CONSTRAINT uq_clients_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE contacts ADD CONSTRAINT uq_contacts_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE leads ADD CONSTRAINT uq_leads_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE projects ADD CONSTRAINT uq_projects_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE clients ADD CONSTRAINT fk_clients_owner_membership
    FOREIGN KEY (tenant_id, owner_id) REFERENCES tenant_memberships (tenant_id, user_id);
ALTER TABLE contacts ADD CONSTRAINT fk_contacts_client_tenant
    FOREIGN KEY (tenant_id, client_id) REFERENCES clients (tenant_id, id);
ALTER TABLE leads ADD CONSTRAINT fk_leads_client_tenant
    FOREIGN KEY (tenant_id, client_id) REFERENCES clients (tenant_id, id);
ALTER TABLE leads ADD CONSTRAINT fk_leads_contact_tenant
    FOREIGN KEY (tenant_id, contact_id) REFERENCES contacts (tenant_id, id);
ALTER TABLE leads ADD CONSTRAINT fk_leads_assignee_membership
    FOREIGN KEY (tenant_id, assigned_to) REFERENCES tenant_memberships (tenant_id, user_id);
ALTER TABLE lead_status_history ADD CONSTRAINT fk_lead_history_lead_tenant
    FOREIGN KEY (tenant_id, lead_id) REFERENCES leads (tenant_id, id);
ALTER TABLE lead_status_history ADD CONSTRAINT fk_lead_history_actor_membership
    FOREIGN KEY (tenant_id, changed_by) REFERENCES tenant_memberships (tenant_id, user_id);
ALTER TABLE projects ADD CONSTRAINT fk_projects_client_tenant
    FOREIGN KEY (tenant_id, client_id) REFERENCES clients (tenant_id, id);
ALTER TABLE projects ADD CONSTRAINT fk_projects_owner_membership
    FOREIGN KEY (tenant_id, owner_id) REFERENCES tenant_memberships (tenant_id, user_id);
ALTER TABLE project_members ADD CONSTRAINT fk_project_members_project_tenant
    FOREIGN KEY (tenant_id, project_id) REFERENCES projects (tenant_id, id);
ALTER TABLE project_members ADD CONSTRAINT fk_project_members_user_membership
    FOREIGN KEY (tenant_id, user_id) REFERENCES tenant_memberships (tenant_id, user_id);
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_client_tenant
    FOREIGN KEY (tenant_id, client_id) REFERENCES clients (tenant_id, id);
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_contact_tenant
    FOREIGN KEY (tenant_id, contact_id) REFERENCES contacts (tenant_id, id);
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_lead_tenant
    FOREIGN KEY (tenant_id, lead_id) REFERENCES leads (tenant_id, id);
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_project_tenant
    FOREIGN KEY (tenant_id, project_id) REFERENCES projects (tenant_id, id);
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_assignee_membership
    FOREIGN KEY (tenant_id, assigned_to) REFERENCES tenant_memberships (tenant_id, user_id);
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_creator_membership
    FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_memberships (tenant_id, user_id);
ALTER TABLE activities ADD CONSTRAINT fk_activities_client_tenant
    FOREIGN KEY (tenant_id, client_id) REFERENCES clients (tenant_id, id);
ALTER TABLE activities ADD CONSTRAINT fk_activities_lead_tenant
    FOREIGN KEY (tenant_id, lead_id) REFERENCES leads (tenant_id, id);
ALTER TABLE activities ADD CONSTRAINT fk_activities_contact_tenant
    FOREIGN KEY (tenant_id, contact_id) REFERENCES contacts (tenant_id, id);
ALTER TABLE activities ADD CONSTRAINT fk_activities_actor_membership
    FOREIGN KEY (tenant_id, user_id) REFERENCES tenant_memberships (tenant_id, user_id);
ALTER TABLE audit_logs ADD CONSTRAINT fk_audit_actor_membership
    FOREIGN KEY (tenant_id, actor_id) REFERENCES tenant_memberships (tenant_id, user_id);

CREATE INDEX idx_clients_tenant_created ON clients (tenant_id, created_at DESC);
CREATE INDEX idx_contacts_tenant_client ON contacts (tenant_id, client_id);
CREATE INDEX idx_leads_tenant_assignee_status ON leads (tenant_id, assigned_to, status);
CREATE INDEX idx_lead_history_tenant_lead_time ON lead_status_history (tenant_id, lead_id, changed_at DESC);
CREATE INDEX idx_projects_tenant_owner_status ON projects (tenant_id, owner_id, status);
CREATE INDEX idx_project_members_tenant_user ON project_members (tenant_id, user_id);
CREATE INDEX idx_tasks_tenant_assignee_status_due ON tasks (tenant_id, assigned_to, status, due_at);
CREATE INDEX idx_activities_tenant_client_time ON activities (tenant_id, client_id, occurred_at DESC);
CREATE INDEX idx_audit_tenant_created ON audit_logs (tenant_id, created_at DESC);

DO $$
BEGIN
    IF (SELECT count(*) FROM tenants WHERE id = '00000000-0000-0000-0000-000000000001'::uuid) <> 1 THEN
        RAISE EXCEPTION 'Initial CRM tenant was not created';
    END IF;
    IF EXISTS (
        SELECT 1 FROM users u
        WHERE NOT EXISTS (
            SELECT 1 FROM tenant_memberships m
            WHERE m.user_id = u.id
              AND m.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
        )
    ) THEN
        RAISE EXCEPTION 'Tenant membership backfill is incomplete';
    END IF;
    IF EXISTS (
        SELECT 1 FROM clients WHERE tenant_id IS NULL
        UNION ALL SELECT 1 FROM contacts WHERE tenant_id IS NULL
        UNION ALL SELECT 1 FROM leads WHERE tenant_id IS NULL
        UNION ALL SELECT 1 FROM lead_status_history WHERE tenant_id IS NULL
        UNION ALL SELECT 1 FROM projects WHERE tenant_id IS NULL
        UNION ALL SELECT 1 FROM project_members WHERE tenant_id IS NULL
        UNION ALL SELECT 1 FROM tasks WHERE tenant_id IS NULL
        UNION ALL SELECT 1 FROM activities WHERE tenant_id IS NULL
        UNION ALL SELECT 1 FROM company_settings WHERE tenant_id IS NULL
        UNION ALL SELECT 1 FROM audit_logs WHERE tenant_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Tenant ownership backfill is incomplete';
    END IF;
END $$;

ALTER TABLE clients ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE contacts ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE leads ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE lead_status_history ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE projects ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE project_members ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE tasks ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE activities ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE company_settings ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE audit_logs ALTER COLUMN tenant_id DROP DEFAULT;
