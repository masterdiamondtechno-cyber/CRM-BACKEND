CREATE TABLE project_milestones (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    project_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    due_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED'
        CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED')),
    created_by UUID NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_milestones_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_project_milestones_project_tenant
        FOREIGN KEY (tenant_id, project_id) REFERENCES projects (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_project_milestones_creator_membership
        FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_memberships (tenant_id, user_id)
);
CREATE INDEX idx_project_milestones_tenant_project_due
    ON project_milestones (tenant_id, project_id, due_date, created_at);
