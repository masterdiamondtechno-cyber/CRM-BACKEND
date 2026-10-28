# CRM Backend

Requires Java 17+, PostgreSQL, and the Maven distribution bundled at `.tools/apache-maven-3.10.0`. Spring Boot 3.4.5 manages the application dependencies.

Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, and `FRONTEND_ORIGIN` before running. Use a Base64-encoded JWT key containing at least 32 random bytes. Configure SMTP with `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, and `MAIL_PASSWORD` for registration verification, invitations, and password reset.

Flyway applies V1-V7. V5 adds the initial tenant and backfills legacy CRM data and users; V6 adds tenant invitations; V7 adds email verification while treating existing users as verified to preserve access. New public registration remains disabled unless `PUBLIC_REGISTRATION_ENABLED=true` and a working SMTP provider plus `REGISTRATION_VERIFICATION_URL` are configured.

A verified workspace selector is supplied with `X-Workspace-Id`. The server validates each selection against the authenticated user's active membership and tenant status. If the user belongs to multiple active workspaces and sends no selection, tenant-scoped requests fail with HTTP 428. Invalid or foreign workspace IDs are rejected. Existing single-workspace users remain compatible.

Registration creates a tenant owner with the least-privileged global user role. The account cannot sign in until a single-use, expiring email verification link is accepted. Invitations use random single-use tokens; only token hashes are stored, and the link is sent to the invited address. Configure `INVITATION_FRONTEND_URL` before creating invitations.

This is an incremental SaaS foundation, not a production-ready commercial SaaS release. Platform Super Admin, subscriptions/billing, usage enforcement, and full PostgreSQL runtime verification are still outstanding. Run `mvn clean verify` from this directory. Testcontainers integration tests require Docker and are skipped automatically when it is unavailable.