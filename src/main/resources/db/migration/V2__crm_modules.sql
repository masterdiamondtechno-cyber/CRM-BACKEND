ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_clients_status ON clients(status);

CREATE TABLE refresh_tokens (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id), token_hash VARCHAR(128) NOT NULL UNIQUE,
 expires_at TIMESTAMPTZ NOT NULL, revoked_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_user ON refresh_tokens(user_id);
CREATE TABLE password_reset_tokens (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id), token_hash VARCHAR(128) NOT NULL UNIQUE,
 expires_at TIMESTAMPTZ NOT NULL, used_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE leads (
 id UUID PRIMARY KEY, title VARCHAR(200) NOT NULL, description TEXT, client_id UUID REFERENCES clients(id),
 contact_id UUID REFERENCES contacts(id), assigned_to UUID NOT NULL REFERENCES users(id), source VARCHAR(80),
 status VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK(status IN ('NEW','CONTACTED','QUALIFIED','PROPOSAL','NEGOTIATION','WON','LOST')),
 estimated_value NUMERIC(14,2), final_value NUMERIC(14,2), expected_close_date DATE, lost_reason TEXT,
 archived_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_leads_assignee_status ON leads(assigned_to,status);
CREATE INDEX idx_leads_client ON leads(client_id);
CREATE TABLE lead_status_history (
 id UUID PRIMARY KEY, lead_id UUID NOT NULL REFERENCES leads(id), changed_by UUID NOT NULL REFERENCES users(id),
 old_status VARCHAR(20), new_status VARCHAR(20) NOT NULL, note TEXT, changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lead_history ON lead_status_history(lead_id,changed_at DESC);

CREATE TABLE projects (
 id UUID PRIMARY KEY, name VARCHAR(200) NOT NULL, client_id UUID NOT NULL REFERENCES clients(id),
 description TEXT, start_date DATE, expected_end_date DATE, owner_id UUID NOT NULL REFERENCES users(id),
 budget NUMERIC(14,2), status VARCHAR(20) NOT NULL DEFAULT 'PLANNED' CHECK(status IN ('PLANNED','IN_PROGRESS','ON_HOLD','COMPLETED','CANCELLED')),
 archived_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_projects_client ON projects(client_id);
CREATE INDEX idx_projects_owner_status ON projects(owner_id,status);
CREATE TABLE project_members (
 project_id UUID NOT NULL REFERENCES projects(id), user_id UUID NOT NULL REFERENCES users(id),
 added_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(project_id,user_id)
);

CREATE TABLE tasks (
 id UUID PRIMARY KEY, title VARCHAR(200) NOT NULL, description TEXT, client_id UUID REFERENCES clients(id),
 contact_id UUID REFERENCES contacts(id), lead_id UUID REFERENCES leads(id), project_id UUID REFERENCES projects(id),
 assigned_to UUID NOT NULL REFERENCES users(id), created_by UUID NOT NULL REFERENCES users(id),
 priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM' CHECK(priority IN ('LOW','MEDIUM','HIGH')),
 status VARCHAR(20) NOT NULL DEFAULT 'TODO' CHECK(status IN ('TODO','IN_PROGRESS','COMPLETED','CANCELLED')),
 due_at TIMESTAMPTZ, completed_at TIMESTAMPTZ, archived_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tasks_assignee_status_due ON tasks(assigned_to,status,due_at);
CREATE INDEX idx_tasks_client ON tasks(client_id);

CREATE TABLE activities (
 id UUID PRIMARY KEY, client_id UUID NOT NULL REFERENCES clients(id), lead_id UUID REFERENCES leads(id),
 contact_id UUID REFERENCES contacts(id), user_id UUID NOT NULL REFERENCES users(id),
 activity_type VARCHAR(20) NOT NULL CHECK(activity_type IN ('CALL','EMAIL','MEETING','NOTE')),
 subject VARCHAR(200) NOT NULL, description TEXT, occurred_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_activities_client_time ON activities(client_id,occurred_at DESC);

CREATE TABLE company_settings (
 id SMALLINT PRIMARY KEY DEFAULT 1 CHECK(id=1), company_name VARCHAR(200) NOT NULL DEFAULT 'IT Company',
 email VARCHAR(255), phone VARCHAR(30), address TEXT, default_currency VARCHAR(3) NOT NULL DEFAULT 'INR',
 time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata', default_page_size INT NOT NULL DEFAULT 20 CHECK(default_page_size BETWEEN 1 AND 100),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO company_settings(id) VALUES (1) ON CONFLICT DO NOTHING;

CREATE TABLE audit_logs (
 id UUID PRIMARY KEY, actor_id UUID REFERENCES users(id), action VARCHAR(100) NOT NULL,
 entity_type VARCHAR(50) NOT NULL, entity_id UUID, details JSONB, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type,entity_id);
