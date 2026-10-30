package com.company.crm.task;

import com.company.crm.client.*;
import com.company.crm.common.ApiResponse;
import com.company.crm.lead.*;
import com.company.crm.security.TenantContext;
import com.company.crm.security.TenantMembershipService;
import com.company.crm.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/tasks")
@Validated
public class TaskController {
  private final TaskRepository tasks;
  private final UserRepository users;
  private final ClientRepository clients;
  private final LeadRepository leads;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final TenantMembershipService memberships;

  public TaskController(
      TaskRepository t,
      UserRepository u,
      ClientRepository c,
      LeadRepository l,
      org.springframework.jdbc.core.JdbcTemplate j,
      TenantMembershipService m) {
    tasks = t;
    users = u;
    clients = c;
    leads = l;
    jdbc = j;
    memberships = m;
  }

  public record Request(
      @NotBlank @Size(max = 200) String title,
      String description,
      UUID clientId,
      UUID contactId,
      UUID leadId,
      UUID projectId,
      UUID assignedTo,
      TaskPriority priority,
      Instant dueAt) {}

  public record StatusRequest(@NotNull TaskStatus status) {}

  public record View(
      UUID id,
      String title,
      String description,
      UUID clientId,
      UUID leadId,
      UUID projectId,
      UUID assignedTo,
      String assignee,
      TaskPriority priority,
      TaskStatus status,
      Instant dueAt,
      Instant completedAt) {}

  private boolean all(Authentication a) {
    return a.getAuthorities().stream()
        .anyMatch(
            x -> x.getAuthority().equals("ROLE_ADMIN") || x.getAuthority().equals("ROLE_MANAGER"));
  }

  private User actor(Authentication a) {
    return users.findByEmailIgnoreCase(a.getName()).orElseThrow();
  }

  private View view(Task t) {
    return new View(
        t.getId(),
        t.getTitle(),
        t.getDescription(),
        t.getClient() == null ? null : t.getClient().getId(),
        t.getLeadId(),
        t.getProjectId(),
        t.getAssignedTo().getId(),
        t.getAssignedTo().getFullName(),
        t.getPriority(),
        t.getStatus(),
        t.getDueAt(),
        t.getCompletedAt());
  }

  @GetMapping
  public ApiResponse<Page<View>> list(
      @RequestParam(required = false) TaskStatus status,
      @RequestParam(required = false) TaskPriority priority,
      @RequestParam(required = false) UUID assignedTo,
      @RequestParam(required = false) Instant dueFrom,
      @RequestParam(required = false) Instant dueTo,
      @RequestParam(defaultValue = "false") boolean overdue,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      Authentication a) {
    return ApiResponse.ok(
        "Tasks",
        tasks
            .search(
                TenantContext.requireTenantId(),
                all(a),
                a.getName(),
                status,
                priority,
                assignedTo,
                dueFrom != null,
                dueFrom == null ? Instant.EPOCH : dueFrom,
                dueTo != null,
                dueTo == null ? Instant.EPOCH : dueTo,
                overdue,
                Instant.now(),
                List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS),
                search == null ? "" : search.trim(),
                PageRequest.of(page, size, Sort.by("dueAt").ascending()))
            .map(this::view));
  }

  private void validateLinks(
      UUID clientId, UUID contactId, UUID leadId, UUID projectId, Authentication a) {
    UUID tenantId = TenantContext.requireTenantId();
    if (contactId != null) {
      if (clientId == null)
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "A contact link requires a client");
      Integer n =
          jdbc.queryForObject(
              "select count(*) from contacts where tenant_id=? and id=? and client_id=? and"
                  + " archived_at is null",
              Integer.class,
              tenantId,
              contactId,
              clientId);
      if (n == null || n == 0)
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Contact must belong to linked client");
    }
    if (leadId != null) {
      Lead l =
          leads
              .visible(tenantId, leadId, all(a), a.getName())
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
      if (clientId != null && l.getClient() != null && !clientId.equals(l.getClient().getId()))
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Lead and task client must match");
    }
    if (projectId != null) {
      var rows =
          jdbc.queryForList(
              "select p.client_id from projects p where p.tenant_id=? and p.id=? and p.archived_at"
                  + " is null and (? or p.owner_id=(select id from users where"
                  + " lower(email)=lower(?)) or exists(select 1 from project_members m where"
                  + " m.tenant_id=p.tenant_id and m.project_id=p.id and m.user_id=(select id from"
                  + " users where lower(email)=lower(?))))",
              tenantId,
              projectId,
              all(a),
              a.getName(),
              a.getName());
      if (rows.isEmpty())
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
      UUID projectClient = (UUID) rows.get(0).get("client_id");
      if (clientId != null && !clientId.equals(projectClient))
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Project and task client must match");
    }
  }

  @PostMapping
  public ResponseEntity<ApiResponse<View>> create(@Valid @RequestBody Request r, Authentication a) {
    User current = actor(a);
    User assignee =
        r.assignedTo() == null
            ? current
            : users
                .findById(r.assignedTo())
                .orElseThrow(
                    () ->
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee not found"));
    memberships.requireActiveMember(assignee.getId());
    if (a.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_EMPLOYEE"))
        && !assignee.getId().equals(current.getId()))
      throw new AccessDeniedException("Cannot assign another employee");
    UUID tenantId = TenantContext.requireTenantId();
    Client c =
        r.clientId() == null
            ? null
            : clients
                .findVisible(tenantId, r.clientId(), all(a), a.getName())
                .orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    if (r.leadId() != null) {
      Lead l =
          leads
              .visible(tenantId, r.leadId(), all(a), a.getName())
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
      if (c != null && l.getClient() != null && !c.getId().equals(l.getClient().getId()))
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Task client and lead client must match");
    }
    validateLinks(r.clientId(), r.contactId(), r.leadId(), r.projectId(), a);
    Task t = new Task();
    t.creator(current);
    t.edit(
        r.title(),
        r.description(),
        c,
        r.contactId(),
        r.leadId(),
        r.projectId(),
        assignee,
        r.priority(),
        r.dueAt());
    return ResponseEntity.status(201).body(ApiResponse.ok("Task created", view(tasks.save(t))));
  }

  @GetMapping("/{id}")
  public ApiResponse<View> get(@PathVariable UUID id, Authentication a) {
    return ApiResponse.ok("Task", view(visible(id, a)));
  }

  @PatchMapping("/{id}")
  public ApiResponse<View> update(
      @PathVariable UUID id, @Valid @RequestBody Request r, Authentication a) {
    Task t = visible(id, a);
    User current = actor(a);
    User assignee =
        r.assignedTo() == null ? t.getAssignedTo() : users.findById(r.assignedTo()).orElseThrow();
    memberships.requireActiveMember(assignee.getId());
    if (a.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_EMPLOYEE"))
        && !assignee.getId().equals(current.getId()))
      throw new AccessDeniedException("Cannot reassign task");
    UUID tenantId = TenantContext.requireTenantId();
    Client c =
        r.clientId() == null
            ? null
            : clients
                .findVisible(tenantId, r.clientId(), all(a), a.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    validateLinks(r.clientId(), r.contactId(), r.leadId(), r.projectId(), a);
    t.edit(
        r.title(),
        r.description(),
        c,
        r.contactId(),
        r.leadId(),
        r.projectId(),
        assignee,
        r.priority(),
        r.dueAt());
    return ApiResponse.ok("Task updated", view(tasks.save(t)));
  }

  @PatchMapping("/{id}/status")
  public ApiResponse<View> status(
      @PathVariable UUID id, @Valid @RequestBody StatusRequest r, Authentication a) {
    Task t = visible(id, a);
    t.changeStatus(r.status());
    return ApiResponse.ok("Task status updated", view(tasks.save(t)));
  }

  private Task visible(UUID id, Authentication a) {
    return tasks
        .visible(TenantContext.requireTenantId(), id, all(a), a.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
  }
}
