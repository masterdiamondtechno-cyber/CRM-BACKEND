# CRM Multi-Tenant Backend Application

A robust, enterprise-grade Multi-Tenant CRM Backend built with **Spring Boot 3.4.5**, **Java 17**, **PostgreSQL**, **Hibernate/JPA**, and **Flyway**.

---

## 🚀 Key Features & Highlights

- **Multi-Tenant Architecture**: Workspace isolation with role-based memberships (`OWNER`, `ADMIN`, `MANAGER`, `EMPLOYEE`) and tenant scoping (`X-Workspace-Id` header).
- **Clean 3-Tier Enterprise Design**: Decoupled architecture with dedicated `dto/`, `service/`, `service/impl/`, and `controller/` packages for high maintainability.
- **Automated Database Migrations**: Flyway version-controlled migrations (V1 through V9) ensuring reproducible schema evolution.
- **JWT Stateless Security**: Secure authentication with short-lived access tokens and database-backed refresh token rotation.
- **Core CRM Modules**:
  - 🏢 **Clients & Accounts**: Full lifecycle, owner assignment, CSV export/import.
  - 📇 **Contacts**: Primary contact management linked to client companies.
  - 🎯 **Leads & Pipeline**: Sales deal tracking, statuses (`NEW` through `WON`/`LOST`), and one-click lead-to-client conversion.
  - 🚀 **Projects & Milestones**: Project tracking, deliverables, budgets, and member assignments.
  - ✅ **Tasks**: Priority, due date, status tracking with client/lead/project linking.
  - 📅 **Activities & Timeline**: Calls, meetings, emails, and notes logging.
  - 📁 **Attachments**: Private tenant-isolated file storage.
  - 📊 **Dashboard & KPIs**: Real-time sales pipeline, workload distribution, and metrics.
  - 🔍 **Global Search**: Unified cross-entity search.
  - 📜 **Audit Logs**: Tamper-evident activity trail.
- **Monitoring & Observability**: Spring Boot Actuator health checks and custom `/api/v1/status` endpoint with live database ping.

---

## 🛠️ Quick Start Guide

### 1. Prerequisites
- **Java 17+** (JDK)
- **PostgreSQL 14+** (running on port `5432` with database `crm`)
- Or **Docker & Docker Compose**

### 2. Run with Docker Compose
To run PostgreSQL and CRM backend together in isolated containers:
```bash
docker compose up -d
```

### 3. Run Locally with Maven Wrapper
If you already have PostgreSQL running locally:
```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

The application will start on **http://localhost:8080**.

---

## 🔑 Default Development Credentials

On the first boot, the application automatically seeds an administrator user into the initial workspace:

| Field | Value |
|---|---|
| **Email** | `admin@crm.local` |
| **Password** | `Admin@12345678` |
| **Role** | `OWNER` |
| **Default Workspace ID** | `00000000-0000-0000-0000-000000000001` ("IT Company") |

---

## 📋 API Documentation & Testing

### 1. Postman Master Collection
A complete Postman collection covering all 15 modules and 35+ endpoints is included in:
📁 `postman/CRM_Backend_Master_API.postman_collection.json`

- **Automatic Token Management**: Hitting the `Login (Admin/User)` request automatically saves the JWT `token`, `user_id`, and `workspace_id` into collection variables.
- Subsequent requests automatically include:
  - `Authorization: Bearer {{token}}`
  - `X-Workspace-Id: {{workspace_id}}`

### 2. Swagger UI / OpenAPI Docs
- **Interactive UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI 3 JSON Spec**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### 3. Health & Uptime Endpoints (Public)
- **API Status**: `GET http://localhost:8080/api/v1/status`
- **Spring Actuator**: `GET http://localhost:8080/actuator/health`

---

## 🏗️ Project Architecture

```
com.company.crm
 ├── activity        # Activities timeline (calls, meetings, notes)
 │    ├── dto
 │    └── service/impl
 ├── audit           # Audit logging service
 ├── auth            # Login, registration, password reset, token rotation
 ├── client          # Client management, CSV export/import
 │    ├── dto
 │    └── service/impl
 ├── common          # ApiResponse wrapper, GlobalExceptionHandler, StatusController
 ├── config          # SecurityConfig, Cors, BootstrapAdmin runner
 ├── contact         # Contacts management
 │    ├── dto
 │    └── service/impl
 ├── dashboard       # Aggregated KPIs, pipeline analytics, team workload
 ├── file            # Private tenant attachment storage
 ├── lead            # Sales leads & lead-to-client conversion
 │    ├── dto
 │    └── service/impl
 ├── project         # Project tracking, milestones & members
 │    ├── dto
 │    └── service/impl
 ├── search          # Cross-entity global search
 ├── security        # JwtFilter, TenantContext, TenantMembershipService
 ├── settings        # Company branding & pagination configurations
 ├── task            # Task management with priority & status
 │    ├── dto
 │    └── service/impl
 ├── user            # User management & roles
 └── workspace       # Workspace invitations & multi-tenant onboarding
```

---

## ⚙️ Configuration Reference (`application.yml`)

| Environment Variable | Default Value | Description |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DB_URL` | `jdbc:postgresql://localhost:5432/crm` | PostgreSQL JDBC connection URL |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | `root` | Database password |
| `JWT_SECRET` | `c29tZS1zdX...` (Base64) | HMAC-SHA secret for signing tokens |
| `JWT_EXPIRATION_MINUTES` | `30` | Access token lifespan |
| `ADMIN_EMAIL` | `admin@crm.local` | Seeded admin email |
| `ADMIN_PASSWORD` | `Admin@12345678` | Seeded admin password |
| `FRONTEND_ORIGIN` | `http://localhost:5173` | CORS allowed frontend URL |