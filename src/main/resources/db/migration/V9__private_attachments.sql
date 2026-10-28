CREATE TABLE crm_attachments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    client_id UUID,
    lead_id UUID,
    project_id UUID,
    uploaded_by UUID NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(127) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    sha256 CHAR(64) NOT NULL,
    storage_key VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_crm_attachments_one_parent CHECK (num_nonnulls(client_id, lead_id, project_id) = 1),
    CONSTRAINT uq_crm_attachments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_attachments_client_tenant FOREIGN KEY (tenant_id, client_id) REFERENCES clients (tenant_id, id),
    CONSTRAINT fk_crm_attachments_lead_tenant FOREIGN KEY (tenant_id, lead_id) REFERENCES leads (tenant_id, id),
    CONSTRAINT fk_crm_attachments_project_tenant FOREIGN KEY (tenant_id, project_id) REFERENCES projects (tenant_id, id),
    CONSTRAINT fk_crm_attachments_uploader_membership FOREIGN KEY (tenant_id, uploaded_by) REFERENCES tenant_memberships (tenant_id, user_id)
);

CREATE INDEX idx_crm_attachments_tenant_client_created ON crm_attachments (tenant_id, client_id, created_at DESC) WHERE client_id IS NOT NULL;
CREATE INDEX idx_crm_attachments_tenant_lead_created ON crm_attachments (tenant_id, lead_id, created_at DESC) WHERE lead_id IS NOT NULL;
CREATE INDEX idx_crm_attachments_tenant_project_created ON crm_attachments (tenant_id, project_id, created_at DESC) WHERE project_id IS NOT NULL;
