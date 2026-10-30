package com.company.crm.auth;

import com.company.crm.common.ApiResponse;
import com.company.crm.security.JwtService;
import com.company.crm.user.Role;
import com.company.crm.user.User;
import com.company.crm.user.UserRepository;
import com.company.crm.workspace.InvitationController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth/invitations")
public class InvitationAcceptanceController {
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JdbcTemplate jdbc;
  private final JwtService jwt;
  private final long refreshDays;

  public InvitationAcceptanceController(
      UserRepository users,
      PasswordEncoder encoder,
      JdbcTemplate jdbc,
      JwtService jwt,
      @Value("${crm.refresh-token-days:30}") long refreshDays) {
    this.users = users;
    this.encoder = encoder;
    this.jdbc = jdbc;
    this.jwt = jwt;
    this.refreshDays = refreshDays;
  }

  public record AcceptRequest(
      @NotBlank String token,
      @Size(max = 120) String fullName,
      @Size(min = 12, max = 72) String password) {}

  private record Pending(UUID id, UUID tenantId, String email, String role, Instant expiresAt) {}

  @PostMapping("/accept")
  @Transactional
  public ApiResponse<AuthController.TokenData> accept(@Valid @RequestBody AcceptRequest request) {
    Pending invite;
    try {
      invite =
          jdbc.queryForObject(
              """
select id,tenant_id,invited_email,role,expires_at
from tenant_invitations
where token_hash=? and accepted_at is null and revoked_at is null and expires_at>now()
for update
""",
              (rs, row) ->
                  new Pending(
                      rs.getObject("id", UUID.class),
                      rs.getObject("tenant_id", UUID.class),
                      rs.getString("invited_email"),
                      rs.getString("role"),
                      rs.getTimestamp("expires_at").toInstant()),
              InvitationController.hash(request.token()));
    } catch (org.springframework.dao.EmptyResultDataAccessException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is invalid or expired");
    }

    User user = users.findByEmailIgnoreCase(invite.email()).orElse(null);
    if (user == null) {
      if (request.fullName() == null
          || request.fullName().isBlank()
          || request.password() == null
          || request.password().length() < 12) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Full name and a password of at least 12 characters are required");
      }
      user =
          users.save(
              new User(
                  request.fullName().trim(),
                  invite.email(),
                  encoder.encode(request.password()),
                  Role.EMPLOYEE));
    } else if (!user.isActive()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This account is unavailable");
    } else if (!user.isEmailVerified()) {
      user.setEmailVerified(true);
      user = users.save(user);
    }

    Integer existingMembership =
        jdbc.queryForObject(
            "select count(*) from tenant_memberships where tenant_id=? and user_id=?",
            Integer.class,
            invite.tenantId(),
            user.getId());
    if (existingMembership != null && existingMembership > 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Account already belongs to this workspace");
    }
    jdbc.update(
        """
        insert into tenant_memberships(tenant_id,user_id,role,membership_status)
        values(?,?,?,'ACTIVE')
        """,
        invite.tenantId(),
        user.getId(),
        invite.role());
    jdbc.update("update tenant_invitations set accepted_at=now() where id=?", invite.id());

    String refreshToken = randomToken();
    jdbc.update(
        "insert into refresh_tokens(id,user_id,token_hash,expires_at) values(?,?,?,?)",
        UUID.randomUUID(),
        user.getId(),
        hash(refreshToken),
        java.sql.Timestamp.from(Instant.now().plusSeconds(refreshDays * 86400)));
    List<AuthController.Workspace> workspaces =
        jdbc.query(
            """
            select t.id,t.name,t.slug,m.role
            from tenant_memberships m join tenants t on t.id=m.tenant_id
            where m.user_id=? and m.membership_status='ACTIVE' and t.status='ACTIVE'
            order by t.created_at,t.id
            """,
            (rs, row) ->
                new AuthController.Workspace(
                    rs.getObject("id", UUID.class), rs.getString("name"),
                    rs.getString("slug"), rs.getString("role")),
            user.getId());
    AuthController.Workspace active =
        workspaces.stream().filter(w -> w.id().equals(invite.tenantId())).findFirst().orElseThrow();
    var profile =
        new AuthController.Profile(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            active.role(),
            active.id(),
            workspaces);
    var tokens =
        new AuthController.TokenData(
            jwt.issue(user), refreshToken, "Bearer", jwt.expiresSeconds(), profile);
    return ApiResponse.ok("Invitation accepted", tokens);
  }

  private static String randomToken() {
    byte[] bytes = new byte[48];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to hash refresh token", e);
    }
  }
}
