package com.company.crm.task.service.impl;

import com.company.crm.client.Client;
import com.company.crm.client.ClientRepository;
import com.company.crm.lead.Lead;
import com.company.crm.lead.LeadRepository;
import com.company.crm.security.TenantContext;
import com.company.crm.security.TenantMembershipService;
import com.company.crm.task.Task;
import com.company.crm.task.TaskPriority;
import com.company.crm.task.TaskRepository;
import com.company.crm.task.TaskStatus;
import com.company.crm.task.dto.TaskRequest;
import com.company.crm.task.dto.TaskStatusRequest;
import com.company.crm.task.dto.TaskView;
import com.company.crm.task.service.TaskService;
import com.company.crm.user.User;
import com.company.crm.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TaskServiceImpl implements TaskService {

  private final TaskRepository tasks;
  private final UserRepository users;
  private final ClientRepository clients;
  private final LeadRepository leads;
  private final JdbcTemplate jdbc;
  private final TenantMembershipService memberships;

  public TaskServiceImpl(
      TaskRepository tasks,
      UserRepository users,
      ClientRepository clients,
      LeadRepository leads,
      JdbcTemplate jdbc,
      TenantMembershipService memberships) {
    this.tasks = tasks;
    this.users = users;
    this.clients = clients;
    this.leads = leads;
    this.jdbc = jdbc;
    this.memberships = memberships;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TaskView> listTasks(
      TaskStatus status,
      TaskPriority priority,
      UUID assignedTo,
      Instant dueFrom,
      Instant dueTo,
      boolean overdue,
      String search,
      int page,
      int size,
      Authentication auth) {
    return tasks
        .search(
            TenantContext.requireTenantId(),
            isManagerOrAdmin(auth),
            auth.getName(),
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
        .map(this::toView);
  }

  @Override
  public TaskView createTask(TaskRequest r, Authentication auth) {
    User current = getUser(auth);
    User assignee =
        r.assignedTo() == null
            ? current
            : users
                .findById(r.assignedTo())
                .orElseThrow(
                    () ->
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee not found"));
    memberships.requireActiveMember(assignee.getId());
    if (auth.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_EMPLOYEE"))
        && !assignee.getId().equals(current.getId())) {
      throw new AccessDeniedException("Cannot assign another employee");
    }

    UUID tenantId = TenantContext.requireTenantId();
    Client c =
        r.clientId() == null
            ? null
            : clients
                .findVisible(tenantId, r.clientId(), isManagerOrAdmin(auth), auth.getName())
                .orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));

    if (r.leadId() != null) {
      Lead l =
          leads
              .visible(tenantId, r.leadId(), isManagerOrAdmin(auth), auth.getName())
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
      if (c != null && l.getClient() != null && !c.getId().equals(l.getClient().getId())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Task client and lead client must match");
      }
    }

    validateLinks(r.clientId(), r.contactId(), r.leadId(), r.projectId(), auth);
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
    return toView(tasks.save(t));
  }

  @Override
  @Transactional(readOnly = true)
  public TaskView getTask(UUID id, Authentication auth) {
    return toView(findVisibleTask(id, auth));
  }

  @Override
  public TaskView updateTask(UUID id, TaskRequest r, Authentication auth) {
    Task t = findVisibleTask(id, auth);
    User current = getUser(auth);
    User assignee =
        r.assignedTo() == null ? t.getAssignedTo() : users.findById(r.assignedTo()).orElseThrow();
    memberships.requireActiveMember(assignee.getId());
    if (auth.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_EMPLOYEE"))
        && !assignee.getId().equals(current.getId())) {
      throw new AccessDeniedException("Cannot reassign task");
    }

    UUID tenantId = TenantContext.requireTenantId();
    Client c =
        r.clientId() == null
            ? null
            : clients
                .findVisible(tenantId, r.clientId(), isManagerOrAdmin(auth), auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    validateLinks(r.clientId(), r.contactId(), r.leadId(), r.projectId(), auth);
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
    return toView(tasks.save(t));
  }

  @Override
  public TaskView updateTaskStatus(UUID id, TaskStatusRequest r, Authentication auth) {
    Task t = findVisibleTask(id, auth);
    t.changeStatus(r.status());
    return toView(tasks.save(t));
  }

  private void validateLinks(
      UUID clientId, UUID contactId, UUID leadId, UUID projectId, Authentication a) {
    UUID tenantId = TenantContext.requireTenantId();
    if (contactId != null) {
      if (clientId == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "A contact link requires a client");
      }
      Integer n =
          jdbc.queryForObject(
              "select count(*) from contacts where tenant_id=? and id=? and client_id=? and"
                  + " archived_at is null",
              Integer.class,
              tenantId,
              contactId,
              clientId);
      if (n == null || n == 0) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Contact must belong to linked client");
      }
    }
    if (leadId != null) {
      Lead l =
          leads
              .visible(tenantId, leadId, isManagerOrAdmin(a), a.getName())
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
      if (clientId != null && l.getClient() != null && !clientId.equals(l.getClient().getId())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Lead and task client must match");
      }
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
              isManagerOrAdmin(a),
              a.getName(),
              a.getName());
      if (rows.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
      }
      UUID projectClient = (UUID) rows.get(0).get("client_id");
      if (clientId != null && !clientId.equals(projectClient)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Project and task client must match");
      }
    }
  }

  private Task findVisibleTask(UUID id, Authentication auth) {
    return tasks
        .visible(TenantContext.requireTenantId(), id, isManagerOrAdmin(auth), auth.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
  }

  private TaskView toView(Task t) {
    return new TaskView(
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

  private boolean isManagerOrAdmin(Authentication a) {
    return a.getAuthorities().stream()
        .anyMatch(
            x -> x.getAuthority().equals("ROLE_ADMIN") || x.getAuthority().equals("ROLE_MANAGER"));
  }

  private User getUser(Authentication a) {
    return users.findByEmailIgnoreCase(a.getName()).orElseThrow();
  }
}
