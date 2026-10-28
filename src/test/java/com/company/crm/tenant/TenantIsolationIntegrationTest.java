package com.company.crm.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TenantIsolationIntegrationTest {
    private static final String ADMIN_EMAIL = "tenant-owner@example.invalid";
    private static final String ADMIN_PASSWORD = "Test-Only-Password-For-CRM-42";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("crm_test")
            .withUsername("crm_test")
            .withPassword("test-only-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("crm.files.directory", () -> java.nio.file.Path.of("target", "test-attachments").toAbsolutePath().toString());
        registry.add("crm.jwt.secret", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        registry.add("crm.jwt.expiration-minutes", () -> "5");
        registry.add("crm.bootstrap.admin-email", () -> ADMIN_EMAIL);
        registry.add("crm.bootstrap.admin-password", () -> ADMIN_PASSWORD);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;

    private UUID initialTenant;
    private UUID otherTenant;
    private UUID ownerId;
    private UUID otherUserId;
    private UUID ownClientId;
    private UUID otherClientId;
    private UUID ownProjectId;
    private UUID otherProjectId;
    private final List<UUID> extraClientIds = new ArrayList<>();
    private final List<UUID> milestoneIds = new ArrayList<>();
    private String ownerToken;

    @BeforeEach
    void createTwoTenantFixture() throws Exception {
        initialTenant = jdbc.queryForObject("select id from tenants where slug='initial'", UUID.class);
        ownerId = jdbc.queryForObject("select id from users where lower(email)=lower(?)", UUID.class, ADMIN_EMAIL);
        assertEquals("OWNER", jdbc.queryForObject(
                "select role from tenant_memberships where tenant_id=? and user_id=?", String.class, initialTenant, ownerId));

        otherTenant = UUID.randomUUID();
        jdbc.update("insert into tenants(id,name,slug,status) values(?,?,?,'ACTIVE')",
                otherTenant, "Isolation test workspace", "test-" + otherTenant);
        otherUserId = UUID.randomUUID();
        jdbc.update("insert into users(id,full_name,email,password_hash,role) values(?,?,?,?, 'EMPLOYEE')",
                otherUserId, "Other Tenant User", "other-" + otherUserId + "@example.invalid",
                passwordEncoder.encode("Employee-Test-Password-42"));
        jdbc.update("insert into tenant_memberships(tenant_id,user_id,role) values(?,?,'EMPLOYEE')",
                otherTenant, otherUserId);

        ownClientId = UUID.randomUUID();
        otherClientId = UUID.randomUUID();
        jdbc.update("insert into clients(id,tenant_id,company_name,owner_id,status) values(?,?,?,?,'ACTIVE')",
                ownClientId, initialTenant, "Initial tenant client", ownerId);
        jdbc.update("insert into clients(id,tenant_id,company_name,owner_id,status) values(?,?,?,?,'ACTIVE')",
                otherClientId, otherTenant, "Other tenant client", otherUserId);
        ownerToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    @AfterEach
    void cleanTwoTenantFixture() {
        if (initialTenant != null) { jdbc.update("delete from tasks where tenant_id=? and title like ?", initialTenant, "Workload fixture %"); jdbc.update("delete from tasks where tenant_id=? and title like ?", initialTenant, "Task search fixture %"); jdbc.update("delete from tasks where tenant_id=? and title like ?", initialTenant, "Creator visibility fixture%"); }
        if (initialTenant != null && otherUserId != null) jdbc.update("delete from tenant_memberships where tenant_id=? and user_id=?", initialTenant, otherUserId);
        if (otherTenant != null) { jdbc.update("delete from tasks where tenant_id=? and title like ?", otherTenant, "Workload fixture %"); jdbc.update("delete from tasks where tenant_id=? and title like ?", otherTenant, "Task search fixture %"); jdbc.update("delete from tasks where tenant_id=? and title like ?", otherTenant, "Creator visibility fixture%"); }
        if (initialTenant != null && ownClientId != null && otherTenant != null && otherClientId != null) {
            var storedFiles = jdbc.queryForList("select id, tenant_id, storage_key from crm_attachments where (tenant_id=? and client_id=?) or (tenant_id=? and client_id=?)",
                    initialTenant, ownClientId, otherTenant, otherClientId);
            for (var file : storedFiles) {
                jdbc.update("delete from audit_logs where tenant_id=? and entity_id=?", file.get("tenant_id"), file.get("id"));
                try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("target", "test-attachments", (String) file.get("storage_key"))); }
                catch (java.io.IOException ignored) { }
            }
            jdbc.update("delete from crm_attachments where (tenant_id=? and client_id=?) or (tenant_id=? and client_id=?)",
                    initialTenant, ownClientId, otherTenant, otherClientId);
        }
        if (initialTenant != null && !milestoneIds.isEmpty()) {
            for (UUID id : milestoneIds) jdbc.update("delete from audit_logs where tenant_id=? and entity_id=?", initialTenant, id);
            milestoneIds.clear();
        }
        if (ownProjectId != null && initialTenant != null) {
            jdbc.update("delete from project_milestones where tenant_id=? and project_id=?", initialTenant, ownProjectId);
            jdbc.update("delete from project_members where tenant_id=? and project_id=?", initialTenant, ownProjectId);
            jdbc.update("delete from projects where tenant_id=? and id=?", initialTenant, ownProjectId);
        }
        if (otherProjectId != null && otherTenant != null) {
            jdbc.update("delete from project_milestones where tenant_id=? and project_id=?", otherTenant, otherProjectId);
            jdbc.update("delete from project_members where tenant_id=? and project_id=?", otherTenant, otherProjectId);
            jdbc.update("delete from projects where tenant_id=? and id=?", otherTenant, otherProjectId);
        }
        if (initialTenant != null && otherTenant != null) {
            jdbc.update("delete from leads where tenant_id=? and title=?", initialTenant, "Own export lead");
            jdbc.update("delete from leads where tenant_id=? and title=?", otherTenant, "Foreign export lead");
        }
        if (initialTenant != null && ownerId != null) {
            jdbc.update("delete from activities where tenant_id=? and lead_id in (select id from leads where tenant_id=? and title=?)", initialTenant, initialTenant, "Conversion test lead");
            jdbc.update("delete from audit_logs where tenant_id=? and entity_id in (select id from leads where tenant_id=? and title=?)", initialTenant, initialTenant, "Conversion test lead");
            jdbc.update("delete from lead_status_history where tenant_id=? and lead_id in (select id from leads where tenant_id=? and title=?)", initialTenant, initialTenant, "Conversion test lead");
            jdbc.update("delete from leads where tenant_id=? and title=?", initialTenant, "Conversion test lead");
            jdbc.update("delete from contacts where tenant_id=? and client_id in (select id from clients where tenant_id=? and company_name=?)", initialTenant, initialTenant, "Converted client test");
            jdbc.update("delete from clients where tenant_id=? and company_name=?", initialTenant, "Converted client test");
            jdbc.update("delete from clients where tenant_id=? and company_name=?", initialTenant, "Shared tenant name");
            jdbc.update("delete from clients where tenant_id=? and company_name=?", initialTenant, "Created in authenticated tenant");
            if (ownClientId != null) jdbc.update("delete from clients where tenant_id=? and id=?", initialTenant, ownClientId);
            for (UUID id : extraClientIds) jdbc.update("delete from clients where tenant_id=? and id=?", initialTenant, id);
            extraClientIds.clear();
            jdbc.update("delete from refresh_tokens where user_id=?", ownerId);
            jdbc.update("update tenant_memberships set membership_status='ACTIVE' where tenant_id=? and user_id=?", initialTenant, ownerId);
        }
        if (otherTenant != null) {
            if (otherClientId != null) jdbc.update("delete from clients where tenant_id=? and id=?", otherTenant, otherClientId);
            jdbc.update("delete from clients where tenant_id=? and company_name=?", otherTenant, "Shared tenant name");
            jdbc.update("delete from tenant_memberships where tenant_id=?", otherTenant);
            jdbc.update("delete from tenants where id=?", otherTenant);
        }
        if (otherUserId != null) {
            jdbc.update("delete from refresh_tokens where user_id=?", otherUserId);
            jdbc.update("delete from users where id=?", otherUserId);
        }
    }

    @Test
    void projectMilestonesAreTenantScopedAndSupportProgressWorkflow() throws Exception {
        String auth = bearer(ownerToken);
        String workspace = initialTenant.toString();
        MvcResult createdProject = mvc.perform(post("/api/v1/projects")
                        .header("Authorization", auth).header("X-Workspace-Id", workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Milestone workflow project\",\"clientId\":\"" + ownClientId + "\",\"ownerId\":\"" + ownerId + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        ownProjectId = UUID.fromString(objectMapper.readTree(createdProject.getResponse().getContentAsString())
                .path("data").path("id").asText());
        otherProjectId = UUID.randomUUID();
        jdbc.update("insert into projects(id,tenant_id,name,client_id,owner_id,status) values(?,?,?,?,?,'PLANNED')",
                otherProjectId, otherTenant, "Other tenant project", otherClientId, otherUserId);
        UUID otherMilestoneId = UUID.randomUUID();
        jdbc.update("insert into project_milestones(id,tenant_id,project_id,name,created_by) values(?,?,?,?,?)",
                otherMilestoneId, otherTenant, otherProjectId, "Private milestone", otherUserId);

        MvcResult createdMilestone = mvc.perform(post("/api/v1/projects/{projectId}/milestones", ownProjectId)
                        .header("Authorization", auth).header("X-Workspace-Id", workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"First delivery\",\"description\":\"Ship the first release\",\"dueDate\":\"2026-12-01\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PLANNED")).andReturn();
        UUID milestoneId = UUID.fromString(objectMapper.readTree(createdMilestone.getResponse().getContentAsString())
                .path("data").path("id").asText());
        milestoneIds.add(milestoneId);
        assertEquals(initialTenant, jdbc.queryForObject("select tenant_id from project_milestones where id=?", UUID.class, milestoneId));

        mvc.perform(get("/api/v1/projects/{projectId}/milestones", ownProjectId)
                        .header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("First delivery"));
        mvc.perform(get("/api/v1/projects/{projectId}/milestones", otherProjectId)
                        .header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/projects/{projectId}/milestones/{milestoneId}/status", ownProjectId, milestoneId)
                        .header("Authorization", auth).header("X-Workspace-Id", workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertNotNull(jdbc.queryForObject("select completed_at from project_milestones where tenant_id=? and id=?",
                java.sql.Timestamp.class, initialTenant, milestoneId));
        mvc.perform(delete("/api/v1/projects/{projectId}/milestones/{milestoneId}", ownProjectId, milestoneId)
                        .header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isNoContent());
        assertEquals(1, jdbc.queryForObject("select count(*) from project_milestones where tenant_id=? and id=?",
                Integer.class, otherTenant, otherMilestoneId));
        assertEquals(3, jdbc.queryForObject("select count(*) from audit_logs where tenant_id=? and entity_id=?", Integer.class, initialTenant, milestoneId));
    }
    @Test
    void attachmentsSupportPrivateCrudAndRejectCrossTenantGuessedIds() throws Exception {
        String auth = bearer(ownerToken);
        String workspace = initialTenant.toString();
        byte[] ownBytes = "approved project scope".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile upload = new MockMultipartFile("file", "scope.txt", "text/plain", ownBytes);
        MvcResult created = mvc.perform(multipart("/api/v1/clients/{id}/attachments", ownClientId).file(upload)
                        .header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.fileName").value("scope.txt"))
                .andExpect(jsonPath("$.data.sizeBytes").value(ownBytes.length)).andReturn();
        UUID ownAttachmentId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText());
        String ownStorageKey = jdbc.queryForObject("select storage_key from crm_attachments where tenant_id=? and id=?",
                String.class, initialTenant, ownAttachmentId);

        UUID foreignAttachmentId = UUID.randomUUID();
        String foreignStorageKey = foreignAttachmentId + ".bin";
        byte[] privateBytes = "tenant B private file".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Path storageRoot = java.nio.file.Path.of("target", "test-attachments").toAbsolutePath();
        java.nio.file.Files.createDirectories(storageRoot);
        java.nio.file.Files.write(storageRoot.resolve(foreignStorageKey), privateBytes);
        String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(privateBytes));
        jdbc.update("""
                insert into crm_attachments(id,tenant_id,client_id,uploaded_by,original_filename,content_type,size_bytes,sha256,storage_key)
                values(?,?,?,?,?,?,?,?,?)
                """, foreignAttachmentId, otherTenant, otherClientId, otherUserId, "private.txt", "text/plain",
                privateBytes.length, digest, foreignStorageKey);

        mvc.perform(get("/api/v1/clients/{id}/attachments", ownClientId)
                        .header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(ownAttachmentId.toString()));
        mvc.perform(get("/api/v1/clients/{id}/attachments", otherClientId)
                        .header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/attachments/{id}/download", foreignAttachmentId)
                        .header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isNotFound());
        MvcResult download = mvc.perform(get("/api/v1/attachments/{id}/download", ownAttachmentId)
                        .header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn();
        assertArrayEquals(ownBytes, download.getResponse().getContentAsByteArray());
        mvc.perform(delete("/api/v1/attachments/{id}", foreignAttachmentId)
                        .header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/attachments/{id}", ownAttachmentId)
                        .header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isNoContent());
        assertFalse(java.nio.file.Files.exists(storageRoot.resolve(ownStorageKey)));
        assertEquals(1, jdbc.queryForObject("select count(*) from crm_attachments where tenant_id=? and id=?",
                Integer.class, otherTenant, foreignAttachmentId));
    }
    @Test
    void dashboardWorkloadIsManagerOnlyAndTenantScoped() throws Exception {
        java.sql.Timestamp overdue = java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(3600));
        jdbc.update("insert into tenant_memberships(tenant_id,user_id,role) values(?,?,'EMPLOYEE')", initialTenant, otherUserId);
        jdbc.update("insert into tasks(id,tenant_id,title,assigned_to,created_by,priority,status,due_at) values(?,?,?,?,?,'MEDIUM','TODO',?)",
                UUID.randomUUID(), initialTenant, "Workload fixture own overdue", ownerId, ownerId, overdue);
        jdbc.update("insert into tasks(id,tenant_id,title,assigned_to,created_by,priority,status,due_at) values(?,?,?,?,?,'MEDIUM','COMPLETED',?)",
                UUID.randomUUID(), initialTenant, "Workload fixture own completed", ownerId, ownerId, overdue);
        jdbc.update("insert into tasks(id,tenant_id,title,assigned_to,created_by,priority,status,due_at) values(?,?,?,?,?,'MEDIUM','TODO',?)",
                UUID.randomUUID(), otherTenant, "Workload fixture foreign task", otherUserId, otherUserId, overdue);

        String employeeToken = login("other-" + otherUserId + "@example.invalid", "Employee-Test-Password-42");
        mvc.perform(get("/api/v1/dashboard/workload")
                        .header("Authorization", bearer(employeeToken)).header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isForbidden());

        MvcResult response = mvc.perform(get("/api/v1/dashboard/workload")
                        .header("Authorization", bearer(ownerToken)).header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isOk()).andReturn();
        JsonNode rows = objectMapper.readTree(response.getResponse().getContentAsString()).path("data");
        assertEquals(2, rows.size());
        JsonNode ownerRow = null;
        for (JsonNode row : rows) if (row.path("id").asText().equals(ownerId.toString())) ownerRow = row;
        assertNotNull(ownerRow);
        assertEquals(1, ownerRow.path("open_tasks").asInt());
        assertEquals(1, ownerRow.path("overdue_tasks").asInt());
        assertEquals(0, ownerRow.path("open_leads").asInt());
        JsonNode employeeRow = null;
        for (JsonNode row : rows) if (row.path("id").asText().equals(otherUserId.toString())) employeeRow = row;
        assertNotNull(employeeRow);
        assertEquals(0, employeeRow.path("open_tasks").asInt());
        assertEquals(0, employeeRow.path("overdue_tasks").asInt());
    }
    @Test
    void tenantAListAndGuessedIdsCannotAccessTenantBClient() throws Exception {
        mvc.perform(get("/api/v1/clients").header("Authorization", bearer(ownerToken)).header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(ownClientId.toString()));

        mvc.perform(get("/api/v1/clients/{id}", ownClientId).header("Authorization", bearer(ownerToken)).header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.client.id").value(ownClientId.toString()))
                .andExpect(jsonPath("$.data.contacts.length()").value(0))
                .andExpect(jsonPath("$.data.leads.length()").value(0))
                .andExpect(jsonPath("$.data.tasks.length()").value(0))
                .andExpect(jsonPath("$.data.projects.length()").value(0))
                .andExpect(jsonPath("$.data.recentActivities.length()").value(0));

        mvc.perform(get("/api/v1/clients/{id}", otherClientId).header("Authorization", bearer(ownerToken)).header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/clients/{id}", otherClientId)
                        .header("Authorization", bearer(ownerToken)).header("X-Workspace-Id", initialTenant.toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Unauthorized edit\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/clients/{id}", otherClientId).header("Authorization", bearer(ownerToken)).header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/clients").param("search", "Other tenant").header("Authorization", bearer(ownerToken)).header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
        assertEquals("Other tenant client", jdbc.queryForObject(
                "select company_name from clients where tenant_id=? and id=?", String.class, otherTenant, otherClientId));
    }

    @Test
    void clientCsvExportIsTenantScopedAndNeutralizesFormulaValues() throws Exception {
        UUID formulaClientId = UUID.randomUUID();
        extraClientIds.add(formulaClientId);
        jdbc.update("insert into clients(id,tenant_id,company_name,owner_id,status) values(?,?,?,?,'ACTIVE')",
                formulaClientId, initialTenant, "=HYPERLINK(\"https://example.invalid\")", ownerId);

        String csv = mvc.perform(get("/api/v1/clients/export")
                        .header("Authorization", bearer(ownerToken))
                        .header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csv.contains("Initial tenant client"));
        assertTrue(csv.contains("'=HYPERLINK"));
        assertFalse(csv.contains("Other tenant client"));
    }

    @Test
    void contactAndLeadCsvExportsRespectTenantAndSearchFilters() throws Exception {
        UUID ownContact = UUID.randomUUID();
        UUID foreignContact = UUID.randomUUID();
        jdbc.update("insert into contacts(id,tenant_id,client_id,full_name,email) values(?,?,?,?,?)",
                ownContact, initialTenant, ownClientId, "Own Export Contact", "own-export@example.invalid");
        jdbc.update("insert into contacts(id,tenant_id,client_id,full_name,email) values(?,?,?,?,?)",
                foreignContact, otherTenant, otherClientId, "Foreign Export Contact", "foreign-export@example.invalid");
        UUID ownLead = UUID.randomUUID();
        UUID foreignLead = UUID.randomUUID();
        jdbc.update("insert into leads(id,tenant_id,title,assigned_to,status) values(?,?,?,?,'NEW')",
                ownLead, initialTenant, "Own export lead", ownerId);
        jdbc.update("insert into leads(id,tenant_id,title,assigned_to,status) values(?,?,?,?,'NEW')",
                foreignLead, otherTenant, "Foreign export lead", otherUserId);

        String contactCsv = mvc.perform(get("/api/v1/contacts/export")
                        .header("Authorization", bearer(ownerToken)).header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(contactCsv.contains("Own Export Contact"));
        assertFalse(contactCsv.contains("Foreign Export Contact"));

        String filteredContactCsv = mvc.perform(get("/api/v1/contacts/export").param("search", "Own Export")
                        .header("Authorization", bearer(ownerToken)).header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(filteredContactCsv.contains("Own Export Contact"));
        assertFalse(filteredContactCsv.contains("Foreign Export Contact"));

        String leadCsv = mvc.perform(get("/api/v1/leads/export").param("status", "NEW")
                        .header("Authorization", bearer(ownerToken)).header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(leadCsv.contains("Own export lead"));
        assertFalse(leadCsv.contains("Foreign export lead"));
        assertEquals(1, jdbc.queryForObject("select count(*) from leads where tenant_id=? and id=?", Integer.class,
                otherTenant, foreignLead));
    }
    @Test
    void clientSearchOptionalFiltersDateRangeAndPaginationWork() throws Exception {
        String auth = bearer(ownerToken);
        String workspace = initialTenant.toString();
        mvc.perform(get("/api/v1/clients").header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(1));
        mvc.perform(get("/api/v1/clients").param("search", "Initial tenant")
                        .header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(1));
        mvc.perform(get("/api/v1/clients").param("status", "ACTIVE")
                        .header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(1));
        mvc.perform(get("/api/v1/clients").param("ownerId", ownerId.toString())
                        .header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(1));

        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        mvc.perform(get("/api/v1/clients").param("createdFrom", today.toString()).param("createdTo", today.toString())
                        .header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(1));
        java.time.LocalDate tomorrow = today.plusDays(1);
        mvc.perform(get("/api/v1/clients").param("createdFrom", tomorrow.toString()).param("createdTo", tomorrow.toString())
                        .header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(0));

        for (String name : List.of("Pagination client 2", "Pagination client 3")) {
            UUID id = UUID.randomUUID();
            extraClientIds.add(id);
            jdbc.update("insert into clients(id,tenant_id,company_name,owner_id,status) values(?,?,?,?,'ACTIVE')",
                    id, initialTenant, name, ownerId);
        }
        MvcResult first = mvc.perform(get("/api/v1/clients").param("page", "0").param("size", "1")
                        .header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content.length()").value(1)).andReturn();
        MvcResult second = mvc.perform(get("/api/v1/clients").param("page", "1").param("size", "1")
                        .header("Authorization", auth).header("X-Workspace-Id", workspace))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(1)).andReturn();
        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).path("data").path("content").get(0).path("id").asText();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).path("data").path("content").get(0).path("id").asText();
        assertNotEquals(firstId, secondId);
    }
    @Test
    void taskSearchFiltersByTextAndAuthenticatedTenant() throws Exception {
        java.sql.Timestamp due = java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(3600));
        jdbc.update("insert into tasks(id,tenant_id,title,description,assigned_to,created_by,priority,status,due_at) values(?,?,?,?,?,?,'HIGH','TODO',?)",
                UUID.randomUUID(), initialTenant, "Task search fixture Terraform", "Prepare scoped migration", ownerId, ownerId, due);
        jdbc.update("insert into tasks(id,tenant_id,title,description,assigned_to,created_by,priority,status,due_at) values(?,?,?,?,?,?,'HIGH','TODO',?)",
                UUID.randomUUID(), otherTenant, "Task search fixture Terraform foreign", "Other workspace", otherUserId, otherUserId, due);
        String creatorToken = login("other-" + otherUserId + "@example.invalid", "Employee-Test-Password-42");
        jdbc.update("insert into tenant_memberships(tenant_id,user_id,role) values(?,?,'EMPLOYEE')", initialTenant, otherUserId);
        jdbc.update("insert into tasks(id,tenant_id,title,description,assigned_to,created_by,priority,status,due_at) values(?,?,?,?,?,?,'MEDIUM','TODO',?)",
                UUID.randomUUID(), initialTenant, "Creator visibility fixture", "Created only search marker", ownerId, otherUserId, due);
        MvcResult creatorSearch = mvc.perform(get("/api/v1/search").param("q", "created only search marker")
                        .header("Authorization", bearer(creatorToken)).header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isOk()).andReturn();
        assertTrue(creatorSearch.getResponse().getContentAsString().contains("Creator visibility fixture"));
        mvc.perform(get("/api/v1/tasks").param("search", "Terraform")
                        .header("Authorization", bearer(ownerToken)).header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Task search fixture Terraform"));
        mvc.perform(get("/api/v1/tasks").param("search", "Terraform foreign")
                        .header("Authorization", bearer(ownerToken)).header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        MvcResult descriptionSearch = mvc.perform(get("/api/v1/search").param("q", "scoped migration")
                        .header("Authorization", bearer(ownerToken)).header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isOk()).andReturn();
        assertTrue(descriptionSearch.getResponse().getContentAsString().contains("Task search fixture Terraform"));
        assertFalse(descriptionSearch.getResponse().getContentAsString().contains("Terraform foreign"));
    }
    @Test
    void projectSearchFiltersVisibleTenantRecords() throws Exception {
        UUID ownProject = UUID.randomUUID();
        UUID foreignProject = UUID.randomUUID();
        jdbc.update("insert into projects(id,tenant_id,name,client_id,owner_id,status) values(?,?,?,?,?,'PLANNED')",
                ownProject, initialTenant, "Northwind search fixture", ownClientId, ownerId);
        jdbc.update("update projects set description=? where tenant_id=? and id=?", "Global project notes", initialTenant, ownProject);
        jdbc.update("insert into projects(id,tenant_id,name,client_id,owner_id,status) values(?,?,?,?,?,'PLANNED')",
                foreignProject, otherTenant, "Northwind foreign fixture", otherClientId, otherUserId);
        jdbc.update("update projects set description=? where tenant_id=? and id=?", "Global project foreign notes", otherTenant, foreignProject);
        try {
            mvc.perform(get("/api/v1/projects").param("search", "Northwind")
                            .header("Authorization", bearer(ownerToken)).header("X-Workspace-Id", initialTenant.toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(ownProject.toString()));
            mvc.perform(get("/api/v1/projects").param("search", "Northwind foreign")
                            .header("Authorization", bearer(ownerToken)).header("X-Workspace-Id", initialTenant.toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
            MvcResult descriptionSearch = mvc.perform(get("/api/v1/search").param("q", "global project notes")
                            .header("Authorization", bearer(ownerToken)).header("X-Workspace-Id", initialTenant.toString()))
                    .andExpect(status().isOk()).andReturn();
            assertTrue(descriptionSearch.getResponse().getContentAsString().contains(ownProject.toString()));
            assertFalse(descriptionSearch.getResponse().getContentAsString().contains(foreignProject.toString()));
        } finally {
            jdbc.update("delete from project_members where tenant_id in (?,?) and project_id in (?,?)",
                    initialTenant, otherTenant, ownProject, foreignProject);
            jdbc.update("delete from projects where tenant_id=? and id=?", initialTenant, ownProject);
            jdbc.update("delete from projects where tenant_id=? and id=?", otherTenant, foreignProject);
        }
    }
    @Test
    void wonLeadConversionCreatesTenantOwnedClientAndPrimaryContactOnce() throws Exception {
        String auth = bearer(ownerToken);
        String workspace = initialTenant.toString();
        MvcResult leadResponse = mvc.perform(post("/api/v1/leads")
                        .header("Authorization", auth).header("X-Workspace-Id", workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Conversion test lead\",\"source\":\"WEBSITE\",\"assignedTo\":\"" + ownerId + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID leadId = UUID.fromString(objectMapper.readTree(leadResponse.getResponse().getContentAsString())
                .path("data").path("id").asText());

        mvc.perform(patch("/api/v1/leads/{id}/status", leadId)
                        .header("Authorization", auth).header("X-Workspace-Id", workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"WON\",\"finalValue\":12500.00}"))
                .andExpect(status().isOk());

        MvcResult conversion = mvc.perform(post("/api/v1/leads/{id}/convert", leadId)
                        .header("Authorization", auth).header("X-Workspace-Id", workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Converted client test\",\"contactFullName\":\"Primary Contact\",\"contactEmail\":\"primary@example.invalid\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.clientId").exists())
                .andExpect(jsonPath("$.data.contactId").exists()).andReturn();
        JsonNode result = objectMapper.readTree(conversion.getResponse().getContentAsString()).path("data");
        UUID clientId = UUID.fromString(result.path("clientId").asText());
        UUID contactId = UUID.fromString(result.path("contactId").asText());
        assertEquals(initialTenant, jdbc.queryForObject("select tenant_id from clients where id=?", UUID.class, clientId));
        assertEquals(ownerId, jdbc.queryForObject("select owner_id from clients where id=?", UUID.class, clientId));
        assertEquals(clientId, jdbc.queryForObject("select client_id from contacts where id=?", UUID.class, contactId));
        assertEquals(clientId, jdbc.queryForObject("select client_id from leads where id=?", UUID.class, leadId));
        assertEquals(contactId, jdbc.queryForObject("select contact_id from leads where id=?", UUID.class, leadId));
        assertEquals(1, jdbc.queryForObject("select count(*) from activities where tenant_id=? and lead_id=?", Integer.class, initialTenant, leadId));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where tenant_id=? and entity_id=?", Integer.class, initialTenant, leadId));

        mvc.perform(post("/api/v1/leads/{id}/convert", leadId)
                        .header("Authorization", auth).header("X-Workspace-Id", workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"companyName\":\"Duplicate conversion\"}"))
                .andExpect(status().isConflict());
    }
    @Test
    void clientCsvImportUsesAuthenticatedTenantOwnerAndReportsTenantScopedDuplicates() throws Exception {
        jdbc.update("insert into clients(id,tenant_id,company_name,owner_id,status) values(?,?,?,?,'ACTIVE')",
                UUID.randomUUID(), otherTenant, "Shared tenant name", otherUserId);
        String csv = ("Company,Email,Owner,tenantId,Status\r\n" +
                "Shared tenant name,,%s,%s,ACTIVE\r\n" +
                "Initial tenant client,,,,PROSPECT\r\n" +
                "Shared tenant name,,,,PROSPECT\r\n").formatted(otherUserId, otherTenant);
        MockMultipartFile file = new MockMultipartFile("file", "clients.csv", "text/csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/clients/import").file(file)
                        .header("Authorization", bearer(ownerToken))
                        .header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(1))
                .andExpect(jsonPath("$.data.skippedDuplicates").value(2))
                .andExpect(jsonPath("$.data.duplicateRows[0]").value(3))
                .andExpect(jsonPath("$.data.duplicateRows[1]").value(4));

        assertEquals(1, jdbc.queryForObject(
                "select count(*) from clients where tenant_id=? and company_name='Shared tenant name'",
                Integer.class, initialTenant));
        assertEquals(ownerId, jdbc.queryForObject(
                "select owner_id from clients where tenant_id=? and company_name='Shared tenant name'",
                UUID.class, initialTenant));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from clients where tenant_id=? and company_name='Shared tenant name'",
                Integer.class, otherTenant));
    }
    @Test
    void bodyTenantIdCannotOverrideVerifiedTenantContext() throws Exception {
        String body = ("{\"companyName\":\"Created in authenticated tenant\",\"tenantId\":\"%s\",\"ownerId\":\"%s\"}")
                .formatted(otherTenant, ownerId);
        MvcResult result = mvc.perform(post("/api/v1/clients")
                        .header("Authorization", bearer(ownerToken)).header("X-Workspace-Id", initialTenant.toString()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        UUID id = UUID.fromString(data.path("id").asText());
        assertEquals(initialTenant, jdbc.queryForObject("select tenant_id from clients where id=?", UUID.class, id));
    }

    @Test
    void crossTenantRelationshipIsRejectedByPostgresConstraint() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into contacts(id,tenant_id,client_id,full_name) values(?,?,?,?)",
                UUID.randomUUID(), otherTenant, ownClientId, "Cross-tenant contact"));
    }

    @Test
    void ambiguousMembershipRequiresExplicitWorkspaceSelection() throws Exception {
        jdbc.update("insert into tenant_memberships(tenant_id,user_id,role) values(?,?,'EMPLOYEE')", otherTenant, ownerId);
        mvc.perform(get("/api/v1/clients").header("Authorization", bearer(ownerToken)))
                .andExpect(status().is(428));
        mvc.perform(get("/api/v1/clients").header("Authorization", bearer(ownerToken))
                        .header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/clients").header("Authorization", bearer(ownerToken))
                        .header("X-Workspace-Id", otherTenant.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void suspendedWorkspaceMembershipCannotAccessRecords() throws Exception {
        jdbc.update("update tenant_memberships set membership_status='SUSPENDED' where tenant_id=? and user_id=?",
                initialTenant, ownerId);
        mvc.perform(get("/api/v1/clients").header("Authorization", bearer(ownerToken))
                        .header("X-Workspace-Id", initialTenant.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void migrationBackfillsLegacyRowsWithoutChangingTheirIds() {
        try (PostgreSQLContainer<?> migrationDb = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("crm_migration_test")
                .withUsername("crm_test")
                .withPassword("migration-test-password")) {
            migrationDb.start();
            var dataSource = new DriverManagerDataSource(
                    migrationDb.getJdbcUrl(), migrationDb.getUsername(), migrationDb.getPassword());
            Flyway.configure().dataSource(dataSource).target("4").load().migrate();
            JdbcTemplate legacyDb = new JdbcTemplate(dataSource);
            UUID legacyUser = UUID.randomUUID();
            UUID legacyClient = UUID.randomUUID();
            legacyDb.update("insert into users(id,full_name,email,password_hash,role) values(?,?,?,?, 'ADMIN')",
                    legacyUser, "Legacy Admin", "legacy-" + legacyUser + "@example.invalid", "test-hash");
            legacyDb.update("insert into clients(id,company_name,owner_id) values(?,?,?)",
                    legacyClient, "Preserved Legacy Client", legacyUser);

            Flyway.configure().dataSource(dataSource).load().migrate();

            UUID migratedTenant = legacyDb.queryForObject(
                    "select tenant_id from clients where id=?", UUID.class, legacyClient);
            assertEquals("Preserved Legacy Client", legacyDb.queryForObject(
                    "select company_name from clients where id=?", String.class, legacyClient));
            assertEquals("initial", legacyDb.queryForObject(
                    "select slug from tenants where id=?", String.class, migratedTenant));
            assertEquals("OWNER", legacyDb.queryForObject(
                    "select role from tenant_memberships where tenant_id=? and user_id=?",
                    String.class, migratedTenant, legacyUser));
            assertEquals(legacyClient, legacyDb.queryForObject(
                    "select id from clients where tenant_id=? and company_name=?", UUID.class,
                    migratedTenant, "Preserved Legacy Client"));
        }
    }

    private String login(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password));
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }
}
























