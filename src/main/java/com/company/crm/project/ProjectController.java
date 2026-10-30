package com.company.crm.project;

import com.company.crm.client.*;
import com.company.crm.common.ApiResponse;
import com.company.crm.security.TenantContext;
import com.company.crm.security.TenantMembershipService;
import com.company.crm.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/projects")
@Validated
public class ProjectController {
  private final JdbcTemplate db;
  private final ClientRepository clients;
  private final UserRepository users;
  private final TenantMembershipService memberships;

  public ProjectController(
      JdbcTemplate d, ClientRepository c, UserRepository u, TenantMembershipService m) {
    db = d;
    clients = c;
    users = u;
    memberships = m;
  }

  public record Request(
      @NotBlank @Size(max = 200) String name,
      @NotNull UUID clientId,
      String description,
      LocalDate startDate,
      LocalDate expectedEndDate,
      UUID ownerId,
      @DecimalMin("0.0") BigDecimal budget) {}

  public record StatusRequest(@NotNull ProjectStatus status) {}

  private boolean all(Authentication a) {
    return a.getAuthorities().stream()
        .anyMatch(
            x -> x.getAuthority().equals("ROLE_ADMIN") || x.getAuthority().equals("ROLE_MANAGER"));
  }

  private UUID current(Authentication a) {
    return users.findByEmailIgnoreCase(a.getName()).orElseThrow().getId();
  }

  private Map<String, Object> visible(UUID id, Authentication a) {
    UUID tenantId = TenantContext.requireTenantId();
    var rows =
        db.queryForList(
            "select p.* from projects p join users u on u.id=p.owner_id where p.tenant_id=? and"
                + " p.id=? and p.archived_at is null and (? or lower(u.email)=lower(?) or"
                + " exists(select 1 from project_members m join users mu on mu.id=m.user_id where"
                + " m.tenant_id=p.tenant_id and m.project_id=p.id and lower(mu.email)=lower(?)))",
            tenantId,
            id,
            all(a),
            a.getName(),
            a.getName());
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
    return rows.get(0);
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> list(
      @RequestParam(required = false) ProjectStatus status,
      @RequestParam(required = false) String search,
      Authentication a) {
    UUID tenantId = TenantContext.requireTenantId();
    StringBuilder sql =
        new StringBuilder(
            "select p.* from projects p join users u on u.id=p.owner_id join clients c on"
                + " c.id=p.client_id and c.tenant_id=p.tenant_id where p.tenant_id=? and"
                + " p.archived_at is null and (? or lower(u.email)=lower(?) or exists(select 1 from"
                + " project_members m join users mu on mu.id=m.user_id where"
                + " m.tenant_id=p.tenant_id and m.project_id=p.id and lower(mu.email)=lower(?)))");
    java.util.ArrayList<Object> args =
        new java.util.ArrayList<>(java.util.List.of(tenantId, all(a), a.getName(), a.getName()));
    if (status != null) {
      sql.append(" and p.status=?");
      args.add(status.name());
    }
    if (search != null && !search.isBlank()) {
      sql.append(
          " and (lower(p.name) like lower(?) or lower(coalesce(p.description,'')) like lower(?) or"
              + " lower(c.company_name) like lower(?))");
      String q = "%" + search.trim() + "%";
      args.add(q);
      args.add(q);
      args.add(q);
    }
    sql.append(" order by p.created_at desc");
    return ApiResponse.ok("Projects", db.queryForList(sql.toString(), args.toArray()));
  }

  @PostMapping
  @Transactional
  public ResponseEntity<ApiResponse<Map<String, Object>>> create(
      @Valid @RequestBody Request r, Authentication a) {
    UUID tenantId = TenantContext.requireTenantId();
    Client c =
        clients
            .findVisible(tenantId, r.clientId(), all(a), a.getName())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    UUID owner = r.ownerId() == null ? current(a) : r.ownerId();
    if (!all(a) && !owner.equals(current(a)))
      throw new AccessDeniedException("Cannot assign project owner");
    memberships.requireActiveMember(owner);
    if (!users.existsById(owner))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner not found");
    if (r.startDate() != null
        && r.expectedEndDate() != null
        && r.expectedEndDate().isBefore(r.startDate()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected end must follow start");
    UUID id = UUID.randomUUID();
    db.update(
        "insert into"
            + " projects(id,tenant_id,name,client_id,description,start_date,expected_end_date,owner_id,budget,status)"
            + " values(?,?,?,?,?,?,?,?,?, 'PLANNED')",
        id,
        tenantId,
        r.name(),
        c.getId(),
        r.description(),
        r.startDate(),
        r.expectedEndDate(),
        owner,
        r.budget());
    db.update(
        "insert into project_members(tenant_id,project_id,user_id) values(?,?,?) on conflict do"
            + " nothing",
        tenantId,
        id,
        owner);
    return ResponseEntity.status(201).body(ApiResponse.ok("Project created", visible(id, a)));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> get(@PathVariable UUID id, Authentication a) {
    var p = visible(id, a);
    var members =
        db.queryForList(
            "select u.id,u.full_name,u.email from project_members m join users u on u.id=m.user_id"
                + " where m.tenant_id=? and m.project_id=? order by u.full_name",
            TenantContext.requireTenantId(),
            id);
    p.put("members", members);
    return ApiResponse.ok("Project", p);
  }

  @PatchMapping("/{id}")
  public ApiResponse<Map<String, Object>> update(
      @PathVariable UUID id, @Valid @RequestBody Request r, Authentication a) {
    Map<String, Object> prior = visible(id, a);
    UUID tenantId = TenantContext.requireTenantId();
    Client c =
        clients
            .findVisible(tenantId, r.clientId(), all(a), a.getName())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    UUID owner = (UUID) prior.get("owner_id");
    if (r.ownerId() != null) {
      if (!all(a)) throw new AccessDeniedException("Only manager/admin can reassign project owner");
      memberships.requireActiveMember(r.ownerId());
      owner =
          users
              .findById(r.ownerId())
              .filter(User::isActive)
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner not found"))
              .getId();
    }
    db.update(
        "update projects set"
            + " name=?,client_id=?,description=?,start_date=?,expected_end_date=?,owner_id=?,budget=?,updated_at=now()"
            + " where tenant_id=? and id=?",
        r.name(),
        c.getId(),
        r.description(),
        r.startDate(),
        r.expectedEndDate(),
        owner,
        r.budget(),
        tenantId,
        id);
    return ApiResponse.ok("Project updated", visible(id, a));
  }

  @PatchMapping("/{id}/status")
  public ApiResponse<Map<String, Object>> status(
      @PathVariable UUID id, @Valid @RequestBody StatusRequest r, Authentication a) {
    visible(id, a);
    db.update(
        "update projects set status=?,updated_at=now() where tenant_id=? and id=?",
        r.status().name(),
        TenantContext.requireTenantId(),
        id);
    return ApiResponse.ok("Project status updated", visible(id, a));
  }

  @PostMapping("/{id}/members")
  @Transactional
  public ApiResponse<Map<String, Object>> addMember(
      @PathVariable UUID id, @RequestBody Map<String, UUID> body, Authentication a) {
    visible(id, a);
    if (!all(a)) throw new AccessDeniedException("Manager role required");
    UUID uid = body.get("userId");
    if (uid == null || !users.existsById(uid))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid userId required");
    memberships.requireActiveMember(uid);
    db.update(
        "insert into project_members(tenant_id,project_id,user_id) values(?,?,?) on conflict do"
            + " nothing",
        TenantContext.requireTenantId(),
        id,
        uid);
    return ApiResponse.ok("Project member added", visible(id, a));
  }

  @DeleteMapping("/{id}/members/{userId}")
  public ResponseEntity<Void> removeMember(
      @PathVariable UUID id, @PathVariable UUID userId, Authentication a) {
    visible(id, a);
    if (!all(a)) throw new AccessDeniedException("Manager role required");
    db.update(
        "delete from project_members where tenant_id=? and project_id=? and user_id=?",
        TenantContext.requireTenantId(),
        id,
        userId);
    return ResponseEntity.noContent().build();
  }
}
