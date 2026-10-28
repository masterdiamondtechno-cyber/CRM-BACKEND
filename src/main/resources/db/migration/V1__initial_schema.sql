CREATE TABLE users (
 id UUID PRIMARY KEY, full_name VARCHAR(120) NOT NULL, email VARCHAR(255) NOT NULL UNIQUE,
 password_hash VARCHAR(255) NOT NULL, role VARCHAR(20) NOT NULL CHECK(role IN ('ADMIN','MANAGER','EMPLOYEE')),
 is_active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE clients (
 id UUID PRIMARY KEY, company_name VARCHAR(200) NOT NULL, industry VARCHAR(100), website VARCHAR(255),
 email VARCHAR(255), phone VARCHAR(30), address TEXT, owner_id UUID NOT NULL REFERENCES users(id),
 status VARCHAR(20) NOT NULL DEFAULT 'PROSPECT' CHECK(status IN ('PROSPECT','ACTIVE','INACTIVE')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_clients_owner ON clients(owner_id);
CREATE INDEX idx_clients_name ON clients(company_name);
CREATE TABLE contacts (
 id UUID PRIMARY KEY, client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
 full_name VARCHAR(120) NOT NULL, job_title VARCHAR(120), email VARCHAR(255), phone VARCHAR(30),
 is_primary BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_contacts_client ON contacts(client_id);
