package com.company.crm.user;

import com.company.crm.audit.AuditService;
import com.company.crm.common.ApiResponse;
import com.company.crm.security.TenantContext;
import com.company.crm.security.TenantMembershipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
  private static final List<String> MEMBERSHIP_ROLES =
      List.of("OWNER", "ADMIN", "MANAGER", "EMPLOYEE");
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final AuditService audit;
  private final JdbcTemplate jdbc;
  private final TenantMembershipService memberships;

  public UserController(
      UserRepository users,
      PasswordEncoder encoder,
      AuditService audit,
      JdbcTemplate jdbc,
      TenantMembershipService memberships) {
    this.users = users;
    this.encoder = encoder;
    this.audit = audit;
    this.jdbc = jdbc;
    this.memberships = memberships;
  }

  public record CreateRequest(
      @NotBlank @Size(max = 120) String fullName,
      @Email @NotBlank String email,
      @NotBlank @Size(min = 12, max = 72) String password,
      @NotNull Role role) {}

  public record UpdateRequest(@Size(max = 120) String fullName, @Email String email, Role role) {}

  public record UserView(UUID id, String fullName, String email, String role, boolean active) {}

  private UserView view(User user) {
    UUID tenantId = TenantContext.requireTenantId();
    Map<String, Object> membership =
        jdbc.queryForMap(
            "select role,membership_status from tenant_memberships where tenant_id=? and user_id=?",
            tenantId,
            user.getId());
    return new UserView(
        user.getId(),
        user.getFullName(),
        user.getEmail(),
        (String) membership.get("role"),
        "ACTIVE".equals(membership.get("membership_status")));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  public ApiResponse<Page<UserView>> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String role,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (role != null && !MEMBERSHIP_ROLES.contains(role)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported workspace role");
    }
    if (page < 0 || size < 1 || size > 100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination values");
    }
    Page<UserView> result =
        users
            .search(
                search == null ? "" : search,
                role,
                active,
                TenantContext.requireTenantId(),
                PageRequest.of(page, size, Sort.by("full_name")))
            .map(this::view);
    return ApiResponse.ok("Employees", result);
  }

  @PostMapping
  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<UserView>> create(
      @Valid @RequestBody CreateRequest request, Authentication actor) {
    if (request.role() != Role.EMPLOYEE && request.role() != Role.MANAGER) {
      throw new AccessDeniedException("Tenant admins cannot create platform administrators");
    }
    if (users.existsByEmailIgnoreCase(request.email())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
    }
    // The global identity has no tenant authority. Effective role is only
    // written to this workspace membership.
    User user =
        users.save(
            new User(
                request.fullName(),
                request.email(),
                encoder.encode(request.password()),
                Role.EMPLOYEE));
    memberships.addInitialMembership(user.getId(), request.role().name());
    audit.record(actor, "USER_CREATED", "USER", user.getId());
    return ResponseEntity.status(201).body(ApiResponse.ok("Employee created", view(user)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  public ApiResponse<UserView> get(@PathVariable UUID id) {
    return ApiResponse.ok("Employee", view(find(id)));
  }

  @PatchMapping("/{id}")
  @Transactional
  public ApiResponse<UserView> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateRequest request, Authentication actor) {
    User target = find(id);
    boolean admin =
        actor.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    if (!admin && !target.getEmail().equalsIgnoreCase(actor.getName())) {
      throw new AccessDeniedException("Can update only your own profile");
    }
    if (!admin && request.role() != null) {
      throw new AccessDeniedException("Only a workspace admin can change membership roles");
    }
    if (request.email() != null
        && users
            .findByEmailIgnoreCase(request.email())
            .filter(user -> !user.getId().equals(id))
            .isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
    }
    String currentMembershipRole =
        (String)
            jdbc.queryForMap(
                    "select role from tenant_memberships where tenant_id=? and user_id=?",
                    TenantContext.requireTenantId(),
                    id)
                .get("role");
    if ("OWNER".equals(currentMembershipRole) && request.role() != null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Transfer workspace ownership before changing this role");
    }

    // Profile identity fields are global; a role change is scoped to this tenant.
    target.update(request.fullName(), request.email(), null);
    User saved = users.save(target);
    if (admin && request.role() != null && !"OWNER".equals(currentMembershipRole)) {
      jdbc.update(
          "update tenant_memberships set role=? where tenant_id=? and user_id=?",
          request.role().name(),
          TenantContext.requireTenantId(),
          id);
    }
    audit.record(actor, "USER_UPDATED", "USER", id);
    return ApiResponse.ok("Employee updated", view(saved));
  }

  @PatchMapping("/{id}/status")
  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<UserView> status(
      @PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
    User user =
        users
            .findById(id)
            .filter(User::isActive)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
    Integer member =
        jdbc.queryForObject(
            "select count(*) from tenant_memberships where tenant_id=? and user_id=?",
            Integer.class,
            TenantContext.requireTenantId(),
            id);
    if (member == null || member == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found");
    }
    Boolean active = body.get("active");
    if (active == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "active is required");
    }
    String currentRole =
        (String)
            jdbc.queryForMap(
                    "select role from tenant_memberships where tenant_id=? and user_id=?",
                    TenantContext.requireTenantId(),
                    id)
                .get("role");
    if ("OWNER".equals(currentRole)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Transfer workspace ownership before suspending the owner");
    }
    jdbc.update(
        "update tenant_memberships set membership_status=? where tenant_id=? and user_id=?",
        active ? "ACTIVE" : "SUSPENDED",
        TenantContext.requireTenantId(),
        id);
    audit.record(
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication(),
        "USER_STATUS_CHANGED",
        "USER",
        id);
    return ApiResponse.ok("Workspace membership updated", view(user));
  }

  private User find(UUID id) {
    User user =
        users
            .findById(id)
            .filter(User::isActive)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
    if (!memberships.isActiveMember(TenantContext.requireTenantId(), id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found");
    }
    return user;
  }
}
