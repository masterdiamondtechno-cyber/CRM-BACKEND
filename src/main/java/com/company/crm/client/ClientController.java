package com.company.crm.client;

import com.company.crm.common.ApiResponse;
import com.company.crm.security.TenantContext;
import com.company.crm.security.TenantMembershipService;
import com.company.crm.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/clients")
@Validated
public class ClientController {
  private final ClientRepository clients;
  private final UserRepository users;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final TenantMembershipService memberships;

  public ClientController(
      ClientRepository c,
      UserRepository u,
      org.springframework.jdbc.core.JdbcTemplate j,
      TenantMembershipService m) {
    clients = c;
    users = u;
    jdbc = j;
    memberships = m;
  }

  public record ClientRequest(
      @NotBlank @Size(max = 200) String companyName,
      @Size(max = 100) String industry,
      @Size(max = 255) String website,
      @Email @Size(max = 255) String email,
      @Size(max = 30) String phone,
      String address,
      String notes,
      UUID ownerId,
      ClientStatus status) {}

  public record ClientView(
      UUID id,
      String companyName,
      String industry,
      String website,
      String email,
      String phone,
      String address,
      String notes,
      UUID ownerId,
      String ownerName,
      ClientStatus status) {}

  private ClientView view(Client c) {
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

  private boolean broad(Authentication a) {
    return a.getAuthorities().stream()
        .anyMatch(
            x -> x.getAuthority().equals("ROLE_ADMIN") || x.getAuthority().equals("ROLE_MANAGER"));
  }

  private boolean employee(Authentication a) {
    return a.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_EMPLOYEE"));
  }

  @GetMapping
  public ApiResponse<Page<ClientView>> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) ClientStatus status,
      @RequestParam(required = false) UUID ownerId,
      @RequestParam(required = false) java.time.LocalDate createdFrom,
      @RequestParam(required = false) java.time.LocalDate createdTo,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      Authentication a) {
    var result =
        clients
            .search(
                TenantContext.requireTenantId(),
                broad(a),
                a.getName(),
                search == null ? "" : search,
                status,
                ownerId,
                createdFrom == null
                    ? null
                    : createdFrom.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                createdTo == null
                    ? null
                    : createdTo.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(this::view);
    return ApiResponse.ok("Clients", result);
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ClientView>> create(
      @Valid @RequestBody ClientRequest r, Authentication a) {
    User actor = user(a);
    UUID ownerId = r.ownerId() == null ? actor.getId() : r.ownerId();
    if (employee(a) && !ownerId.equals(actor.getId()))
      throw new AccessDeniedException("Employees can assign only themselves");
    memberships.requireActiveMember(ownerId);
    User owner =
        users
            .findById(ownerId)
            .filter(User::isActive)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Owner not found or inactive"));
    Client c =
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
    return ResponseEntity.status(201).body(ApiResponse.ok("Client created", view(c)));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> get(@PathVariable UUID id, Authentication a) {
    Client c = visible(id, a);
    UUID tenantId = TenantContext.requireTenantId();
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("client", view(c));
    detail.put(
        "contacts",
        jdbc.queryForList(
            "select id,full_name,job_title,email,phone,is_primary from contacts where tenant_id=?"
                + " and client_id=? and archived_at is null order by full_name",
            tenantId,
            id));
    List<Map<String, Object>> leadRows =
        broad(a)
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
                a.getName());
    detail.put("leads", leadRows);
    List<Map<String, Object>> taskRows =
        broad(a)
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
                a.getName(),
                a.getName());
    detail.put("tasks", taskRows);
    List<Map<String, Object>> projectRows =
        broad(a)
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
                a.getName(),
                a.getName());
    detail.put("projects", projectRows);
    detail.put(
        "recentActivities",
        jdbc.queryForList(
            "select id,activity_type,subject,occurred_at from activities where tenant_id=? and"
                + " client_id=? order by occurred_at desc limit 20",
            tenantId,
            id));
    return ApiResponse.ok("Client detail", detail);
  }

  @PatchMapping("/{id}")
  public ApiResponse<ClientView> update(
      @PathVariable UUID id, @Valid @RequestBody ClientRequest r, Authentication a) {
    Client c = visible(id, a);
    User actor = user(a);
    User owner = c.getOwner();
    if (r.ownerId() != null) {
      if (employee(a) && !r.ownerId().equals(actor.getId()))
        throw new AccessDeniedException("Employees cannot reassign clients");
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
    return ApiResponse.ok("Client updated", view(clients.save(c)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication a) {
    Client c = visible(id, a);
    if (!a.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_ADMIN")))
      throw new AccessDeniedException("Admin required");
    c.archive();
    clients.save(c);
    return ResponseEntity.noContent().build();
  }

  private User user(Authentication a) {
    return users.findByEmailIgnoreCase(a.getName()).orElseThrow();
  }

  private Client visible(UUID id, Authentication a) {
    return clients
        .findVisible(TenantContext.requireTenantId(), id, broad(a), a.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
  }
}
