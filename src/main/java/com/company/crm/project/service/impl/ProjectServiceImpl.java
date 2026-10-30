package com.company.crm.project.service.impl;

import com.company.crm.client.Client;
import com.company.crm.client.ClientRepository;
import com.company.crm.project.ProjectStatus;
import com.company.crm.project.dto.ProjectRequest;
import com.company.crm.project.dto.ProjectStatusRequest;
import com.company.crm.project.service.ProjectService;
import com.company.crm.security.TenantContext;
import com.company.crm.security.TenantMembershipService;
import com.company.crm.user.User;
import com.company.crm.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ProjectServiceImpl implements ProjectService {

  private final JdbcTemplate db;
  private final ClientRepository clients;
  private final UserRepository users;
  private final TenantMembershipService memberships;

  public ProjectServiceImpl(
      JdbcTemplate db,
      ClientRepository clients,
      UserRepository users,
      TenantMembershipService memberships) {
    this.db = db;
    this.clients = clients;
    this.users = users;
    this.memberships = memberships;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Map<String, Object>> listProjects(
      ProjectStatus status, String search, Authentication auth) {
    UUID tenantId = TenantContext.requireTenantId();
    StringBuilder sql =
        new StringBuilder(
            "select p.* from projects p join users u on u.id=p.owner_id join clients c on"
                + " c.id=p.client_id and c.tenant_id=p.tenant_id where p.tenant_id=? and"
                + " p.archived_at is null and (? or lower(u.email)=lower(?) or exists(select 1 from"
                + " project_members m join users mu on mu.id=m.user_id where"
                + " m.tenant_id=p.tenant_id and m.project_id=p.id and lower(mu.email)=lower(?)))");
    ArrayList<Object> args =
        new ArrayList<>(List.of(tenantId, isManagerOrAdmin(auth), auth.getName(), auth.getName()));
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
    return db.queryForList(sql.toString(), args.toArray());
  }

  @Override
  public Map<String, Object> createProject(ProjectRequest r, Authentication auth) {
    UUID tenantId = TenantContext.requireTenantId();
    Client c =
        clients
            .findVisible(tenantId, r.clientId(), isManagerOrAdmin(auth), auth.getName())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));

    UUID owner = r.ownerId() == null ? currentUserId(auth) : r.ownerId();
    if (!isManagerOrAdmin(auth) && !owner.equals(currentUserId(auth))) {
      throw new AccessDeniedException("Cannot assign project owner");
    }
    memberships.requireActiveMember(owner);
    if (!users.existsById(owner)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner not found");
    }
    if (r.startDate() != null
        && r.expectedEndDate() != null
        && r.expectedEndDate().isBefore(r.startDate())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected end must follow start");
    }

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
    return findVisible(id, auth);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> getProject(UUID id, Authentication auth) {
    Map<String, Object> p = findVisible(id, auth);
    var members =
        db.queryForList(
            "select u.id,u.full_name,u.email from project_members m join users u on u.id=m.user_id"
                + " where m.tenant_id=? and m.project_id=? order by u.full_name",
            TenantContext.requireTenantId(),
            id);
    p.put("members", members);
    return p;
  }

  @Override
  public Map<String, Object> updateProject(UUID id, ProjectRequest r, Authentication auth) {
    Map<String, Object> prior = findVisible(id, auth);
    UUID tenantId = TenantContext.requireTenantId();
    Client c =
        clients
            .findVisible(tenantId, r.clientId(), isManagerOrAdmin(auth), auth.getName())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    UUID owner = (UUID) prior.get("owner_id");
    if (r.ownerId() != null) {
      if (!isManagerOrAdmin(auth)) {
        throw new AccessDeniedException("Only manager/admin can reassign project owner");
      }
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
    return findVisible(id, auth);
  }

  @Override
  public Map<String, Object> updateProjectStatus(
      UUID id, ProjectStatusRequest r, Authentication auth) {
    findVisible(id, auth);
    db.update(
        "update projects set status=?,updated_at=now() where tenant_id=? and id=?",
        r.status().name(),
        TenantContext.requireTenantId(),
        id);
    return findVisible(id, auth);
  }

  @Override
  public Map<String, Object> addMember(UUID id, UUID userId, Authentication auth) {
    findVisible(id, auth);
    if (!isManagerOrAdmin(auth)) {
      throw new AccessDeniedException("Manager role required");
    }
    if (userId == null || !users.existsById(userId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid userId required");
    }
    memberships.requireActiveMember(userId);
    db.update(
        "insert into project_members(tenant_id,project_id,user_id) values(?,?,?) on conflict do"
            + " nothing",
        TenantContext.requireTenantId(),
        id,
        userId);
    return findVisible(id, auth);
  }

  @Override
  public void removeMember(UUID id, UUID userId, Authentication auth) {
    findVisible(id, auth);
    if (!isManagerOrAdmin(auth)) {
      throw new AccessDeniedException("Manager role required");
    }
    db.update(
        "delete from project_members where tenant_id=? and project_id=? and user_id=?",
        TenantContext.requireTenantId(),
        id,
        userId);
  }

  private Map<String, Object> findVisible(UUID id, Authentication a) {
    UUID tenantId = TenantContext.requireTenantId();
    var rows =
        db.queryForList(
            "select p.* from projects p join users u on u.id=p.owner_id where p.tenant_id=? and"
                + " p.id=? and p.archived_at is null and (? or lower(u.email)=lower(?) or"
                + " exists(select 1 from project_members m join users mu on mu.id=m.user_id where"
                + " m.tenant_id=p.tenant_id and m.project_id=p.id and lower(mu.email)=lower(?)))",
            tenantId,
            id,
            isManagerOrAdmin(a),
            a.getName(),
            a.getName());
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
    }
    return rows.get(0);
  }

  private boolean isManagerOrAdmin(Authentication a) {
    return a.getAuthorities().stream()
        .anyMatch(
            x -> x.getAuthority().equals("ROLE_ADMIN") || x.getAuthority().equals("ROLE_MANAGER"));
  }

  private UUID currentUserId(Authentication a) {
    return users.findByEmailIgnoreCase(a.getName()).orElseThrow().getId();
  }
}
