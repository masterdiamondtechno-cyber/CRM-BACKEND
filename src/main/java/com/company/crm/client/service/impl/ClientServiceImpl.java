package com.company.crm.client.service.impl;

import com.company.crm.client.Client;
import com.company.crm.client.ClientRepository;
import com.company.crm.client.ClientStatus;
import com.company.crm.client.dto.ClientRequest;
import com.company.crm.client.dto.ClientView;
import com.company.crm.client.service.ClientService;
import com.company.crm.security.TenantContext;
import com.company.crm.security.TenantMembershipService;
import com.company.crm.user.User;
import com.company.crm.user.UserRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
public class ClientServiceImpl implements ClientService {

  private final ClientRepository clients;
  private final UserRepository users;
  private final JdbcTemplate jdbc;
  private final TenantMembershipService memberships;

  public ClientServiceImpl(
      ClientRepository clients,
      UserRepository users,
      JdbcTemplate jdbc,
      TenantMembershipService memberships) {
    this.clients = clients;
    this.users = users;
    this.jdbc = jdbc;
    this.memberships = memberships;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ClientView> listClients(
      String search,
      ClientStatus status,
      UUID ownerId,
      LocalDate createdFrom,
      LocalDate createdTo,
      int page,
      int size,
      Authentication auth) {
    return clients
        .search(
            TenantContext.requireTenantId(),
            isBroad(auth),
            auth.getName(),
            search == null ? "" : search,
            status,
            ownerId,
            createdFrom == null ? null : createdFrom.atStartOfDay(ZoneOffset.UTC).toInstant(),
            createdTo == null
                ? null
                : createdTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
            PageRequest.of(page, size, Sort.by("createdAt").descending()))
        .map(this::toView);
  }

  @Override
  public ClientView createClient(ClientRequest r, Authentication auth) {
    User actor = getUser(auth);
    UUID ownerId = r.ownerId() == null ? actor.getId() : r.ownerId();
    if (isEmployee(auth) && !ownerId.equals(actor.getId())) {
      throw new AccessDeniedException("Employees can assign only themselves");
    }
    memberships.requireActiveMember(ownerId);
    User owner =
        users
            .findById(ownerId)
            .filter(User::isActive)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Owner not found or inactive"));

    Client client =
        clients.save(
            new Client(
                r.companyName(),
                r.industry(),
                r.website(),
                r.email(),
                r.phone(),
                r.address(),
                r.notes(),
                owner,
                r.status()));
    return toView(client);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> getClientDetail(UUID id, Authentication auth) {
    Client c = findVisibleClient(id, auth);
    UUID tenantId = TenantContext.requireTenantId();
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("client", toView(c));

    detail.put(
        "contacts",
        jdbc.queryForList(
            "select id,full_name,job_title,email,phone,is_primary from contacts where tenant_id=?"
                + " and client_id=? and archived_at is null order by full_name",
            tenantId,
            id));

    List<Map<String, Object>> leadRows =
        isBroad(auth)
            ? jdbc.queryForList(
                "select id,title,status,estimated_value,expected_close_date from leads where"
                    + " tenant_id=? and client_id=? and archived_at is null order by created_at"
                    + " desc limit 20",
                tenantId,
                id)
            : jdbc.queryForList(
                "select id,title,status,estimated_value,expected_close_date from leads where"
                    + " tenant_id=? and client_id=? and assigned_to=(select id from users where"
                    + " lower(email)=lower(?)) and archived_at is null order by created_at desc"
                    + " limit 20",
                tenantId,
                id,
                auth.getName());
    detail.put("leads", leadRows);

    List<Map<String, Object>> taskRows =
        isBroad(auth)
            ? jdbc.queryForList(
                "select id,title,status,priority,due_at,assigned_to from tasks where tenant_id=?"
                    + " and client_id=? and archived_at is null order by due_at nulls last limit"
                    + " 20",
                tenantId,
                id)
            : jdbc.queryForList(
                "select id,title,status,priority,due_at,assigned_to from tasks where tenant_id=?"
                    + " and client_id=? and (assigned_to=(select id from users where"
                    + " lower(email)=lower(?)) or created_by=(select id from users where"
                    + " lower(email)=lower(?))) and archived_at is null order by due_at nulls last"
                    + " limit 20",
                tenantId,
                id,
                auth.getName(),
                auth.getName());
    detail.put("tasks", taskRows);

    List<Map<String, Object>> projectRows =
        isBroad(auth)
            ? jdbc.queryForList(
                "select id,name,status,start_date,expected_end_date from projects where tenant_id=?"
                    + " and client_id=? and archived_at is null order by created_at desc limit 20",
                tenantId,
                id)
            : jdbc.queryForList(
                "select p.id,p.name,p.status,p.start_date,p.expected_end_date from projects p where"
                    + " p.tenant_id=? and p.client_id=? and p.archived_at is null and"
                    + " (p.owner_id=(select id from users where lower(email)=lower(?)) or"
                    + " exists(select 1 from project_members m where m.tenant_id=p.tenant_id and"
                    + " m.project_id=p.id and m.user_id=(select id from users where"
                    + " lower(email)=lower(?)))) order by p.created_at desc limit 20",
                tenantId,
                id,
                auth.getName(),
                auth.getName());
    detail.put("projects", projectRows);

    detail.put(
        "recentActivities",
        jdbc.queryForList(
            "select id,activity_type,subject,occurred_at from activities where tenant_id=? and"
                + " client_id=? order by occurred_at desc limit 20",
            tenantId,
            id));

    return detail;
  }

  @Override
  public ClientView updateClient(UUID id, ClientRequest r, Authentication auth) {
    Client c = findVisibleClient(id, auth);
    User actor = getUser(auth);
    User owner = c.getOwner();
    if (r.ownerId() != null) {
      if (isEmployee(auth) && !r.ownerId().equals(actor.getId())) {
        throw new AccessDeniedException("Employees cannot reassign clients");
      }
      memberships.requireActiveMember(r.ownerId());
      owner =
          users
              .findById(r.ownerId())
              .filter(User::isActive)
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner not found"));
    }
    c.update(
        r.companyName(),
        r.industry(),
        r.website(),
        r.email(),
        r.phone(),
        r.address(),
        r.notes(),
        r.status(),
        owner);
    return toView(clients.save(c));
  }

  @Override
  public void deleteClient(UUID id, Authentication auth) {
    Client c = findVisibleClient(id, auth);
    if (auth.getAuthorities().stream().noneMatch(x -> x.getAuthority().equals("ROLE_ADMIN"))) {
      throw new AccessDeniedException("Admin required");
    }
    c.archive();
    clients.save(c);
  }

  @Override
  @Transactional(readOnly = true)
  public Client findVisibleClient(UUID id, Authentication auth) {
    return clients
        .findVisible(TenantContext.requireTenantId(), id, isBroad(auth), auth.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
  }

  private ClientView toView(Client c) {
    return new ClientView(
        c.getId(),
        c.getCompanyName(),
        c.getIndustry(),
        c.getWebsite(),
        c.getEmail(),
        c.getPhone(),
        c.getAddress(),
        c.getNotes(),
        c.getOwner().getId(),
        c.getOwner().getFullName(),
        c.getStatus());
  }

  private boolean isBroad(Authentication a) {
    return a.getAuthorities().stream()
        .anyMatch(
            x -> x.getAuthority().equals("ROLE_ADMIN") || x.getAuthority().equals("ROLE_MANAGER"));
  }

  private boolean isEmployee(Authentication a) {
    return a.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_EMPLOYEE"));
  }

  private User getUser(Authentication a) {
    return users.findByEmailIgnoreCase(a.getName()).orElseThrow();
  }
}
