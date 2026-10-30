package com.company.crm.project;

import com.company.crm.audit.AuditService;
import com.company.crm.common.ApiResponse;
import com.company.crm.security.TenantContext;
import com.company.crm.user.User;
import com.company.crm.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/milestones")
@Validated
public class ProjectMilestoneController {
  private final JdbcTemplate db;
  private final UserRepository users;
  private final AuditService audit;

  public ProjectMilestoneController(JdbcTemplate db, UserRepository users, AuditService audit) {
    this.db = db;
    this.users = users;
    this.audit = audit;
  }

  public record Request(
      @NotBlank @Size(max = 200) String name,
      @Size(max = 10000) String description,
      LocalDate dueDate,
      ProjectMilestoneStatus status) {}

  public record StatusRequest(ProjectMilestoneStatus status) {}

  private boolean manager(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(
            authority ->
                authority.getAuthority().equals("ROLE_ADMIN")
                    || authority.getAuthority().equals("ROLE_MANAGER"));
  }

  private UUID tenantId() {
    return TenantContext.requireTenantId();
  }

  private void requireVisibleProject(UUID projectId, Authentication authentication) {
    UUID tenant = tenantId();
    String email = authentication.getName();
    Integer visible =
        db.queryForObject(
            """
            select count(*) from projects p
            join users owner on owner.id = p.owner_id
            where p.tenant_id = ? and p.id = ? and p.archived_at is null
              and (? or lower(owner.email) = lower(?) or exists (
                select 1 from project_members pm join users member on member.id = pm.user_id
                where pm.tenant_id = p.tenant_id and pm.project_id = p.id
                  and lower(member.email) = lower(?)
              ))
            """,
            Integer.class,
            tenant,
            projectId,
            manager(authentication),
            email,
            email);
    if (visible == null || visible == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
    }
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> list(
      @PathVariable UUID projectId, Authentication authentication) {
    requireVisibleProject(projectId, authentication);
    return ApiResponse.ok(
        "Project milestones",
        db.queryForList(
            """
            select id, name, description, due_date, status, completed_at, created_at, updated_at
            from project_milestones
            where tenant_id = ? and project_id = ?
            order by due_date asc nulls last, created_at asc
            """,
            tenantId(),
            projectId));
  }

  @PostMapping
  @Transactional
  public ResponseEntity<ApiResponse<Map<String, Object>>> create(
      @PathVariable UUID projectId,
      @Valid @RequestBody Request request,
      Authentication authentication) {
    requireVisibleProject(projectId, authentication);
    if (!manager(authentication))
      throw new AccessDeniedException("Manager role required to create milestones");
    UUID tenant = tenantId();
    User actor = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
    UUID id = UUID.randomUUID();
    ProjectMilestoneStatus status =
        request.status() == null ? ProjectMilestoneStatus.PLANNED : request.status();
    db.update(
        """
insert into project_milestones(id, tenant_id, project_id, name, description, due_date, status, created_by, completed_at)
values (?, ?, ?, ?, ?, ?, ?, ?, case when ? = 'COMPLETED' then now() else null end)
""",
        id,
        tenant,
        projectId,
        request.name().trim(),
        request.description(),
        request.dueDate(),
        status.name(),
        actor.getId(),
        status.name());
    audit.record(authentication, "PROJECT_MILESTONE_CREATED", "PROJECT_MILESTONE", id);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok("Milestone created", find(projectId, id)));
  }

  @PatchMapping("/{milestoneId}")
  @Transactional
  public ApiResponse<Map<String, Object>> update(
      @PathVariable UUID projectId,
      @PathVariable UUID milestoneId,
      @Valid @RequestBody Request request,
      Authentication authentication) {
    requireVisibleProject(projectId, authentication);
    if (!manager(authentication))
      throw new AccessDeniedException("Manager role required to edit milestones");
    ProjectMilestoneStatus status =
        request.status() == null ? ProjectMilestoneStatus.PLANNED : request.status();
    int updated =
        db.update(
            """
update project_milestones
set name = ?, description = ?, due_date = ?, status = ?,
    completed_at = case when ? = 'COMPLETED' then coalesce(completed_at, now()) else null end,
    updated_at = now()
where tenant_id = ? and project_id = ? and id = ?
""",
            request.name().trim(),
            request.description(),
            request.dueDate(),
            status.name(),
            status.name(),
            tenantId(),
            projectId,
            milestoneId);
    if (updated == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Milestone not found");
    audit.record(authentication, "PROJECT_MILESTONE_UPDATED", "PROJECT_MILESTONE", milestoneId);
    return ApiResponse.ok("Milestone updated", find(projectId, milestoneId));
  }

  @PatchMapping("/{milestoneId}/status")
  @Transactional
  public ApiResponse<Map<String, Object>> updateStatus(
      @PathVariable UUID projectId,
      @PathVariable UUID milestoneId,
      @Valid @RequestBody StatusRequest request,
      Authentication authentication) {
    requireVisibleProject(projectId, authentication);
    if (request.status() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
    int updated =
        db.update(
            """
update project_milestones
set status = ?, completed_at = case when ? = 'COMPLETED' then coalesce(completed_at, now()) else null end,
    updated_at = now()
where tenant_id = ? and project_id = ? and id = ?
""",
            request.status().name(),
            request.status().name(),
            tenantId(),
            projectId,
            milestoneId);
    if (updated == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Milestone not found");
    audit.record(
        authentication, "PROJECT_MILESTONE_STATUS_CHANGED", "PROJECT_MILESTONE", milestoneId);
    return ApiResponse.ok("Milestone status updated", find(projectId, milestoneId));
  }

  @DeleteMapping("/{milestoneId}")
  @Transactional
  public ResponseEntity<Void> delete(
      @PathVariable UUID projectId, @PathVariable UUID milestoneId, Authentication authentication) {
    requireVisibleProject(projectId, authentication);
    if (!manager(authentication))
      throw new AccessDeniedException("Manager role required to remove milestones");
    int deleted =
        db.update(
            "delete from project_milestones where tenant_id = ? and project_id = ? and id = ?",
            tenantId(),
            projectId,
            milestoneId);
    if (deleted == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Milestone not found");
    audit.record(authentication, "PROJECT_MILESTONE_DELETED", "PROJECT_MILESTONE", milestoneId);
    return ResponseEntity.noContent().build();
  }

  private Map<String, Object> find(UUID projectId, UUID milestoneId) {
    return db.query(
        """
        select id, name, description, due_date, status, completed_at, created_at, updated_at
        from project_milestones where tenant_id = ? and project_id = ? and id = ?
        """,
        result -> {
          if (!result.next())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Milestone not found");
          return Map.of(
              "id",
              result.getObject("id"),
              "name",
              result.getString("name"),
              "description",
              result.getString("description") == null ? "" : result.getString("description"),
              "due_date",
              result.getObject("due_date") == null ? "" : result.getObject("due_date"),
              "status",
              result.getString("status"),
              "completed_at",
              result.getTimestamp("completed_at") == null
                  ? ""
                  : result.getTimestamp("completed_at"),
              "created_at",
              result.getTimestamp("created_at"),
              "updated_at",
              result.getTimestamp("updated_at"));
        },
        tenantId(),
        projectId,
        milestoneId);
  }
}
