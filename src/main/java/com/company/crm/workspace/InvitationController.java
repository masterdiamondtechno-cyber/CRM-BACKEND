package com.company.crm.workspace;

import com.company.crm.common.ApiResponse;
import com.company.crm.security.TenantContext;
import com.company.crm.security.TenantPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/workspaces/invitations")
public class InvitationController {
  private final JdbcTemplate jdbc;
  private final ObjectProvider<JavaMailSender> mail;
  private final String mailHost;
  private final String frontendUrl;
  private final long expirationHours;

  public InvitationController(
      JdbcTemplate jdbc,
      ObjectProvider<JavaMailSender> mail,
      @Value("${spring.mail.host:}") String mailHost,
      @Value("${crm.invitation.frontend-url:}") String frontendUrl,
      @Value("${crm.invitation.expiration-hours:72}") long expirationHours) {
    this.jdbc = jdbc;
    this.mail = mail;
    this.mailHost = mailHost;
    this.frontendUrl = frontendUrl;
    this.expirationHours = expirationHours;
  }

  public record CreateRequest(
      @NotBlank @Email String email,
      @NotNull @Pattern(regexp = "ADMIN|MANAGER|EMPLOYEE") String role) {}

  public record InvitationView(UUID id, String email, String role, Instant expiresAt) {}

  @PostMapping
  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<InvitationView> create(
      @Valid @RequestBody CreateRequest request, @AuthenticationPrincipal TenantPrincipal actor) {
    if (mailHost.isBlank() || frontendUrl.isBlank() || mail.getIfAvailable() == null) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Invitation email is not configured");
    }
    UUID tenantId = TenantContext.requireTenantId();
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    jdbc.update(
        """
        update tenant_invitations set revoked_at=now()
        where tenant_id=? and lower(invited_email)=? and accepted_at is null
          and revoked_at is null and expires_at<=now()
        """,
        tenantId,
        email);
    Integer pending =
        jdbc.queryForObject(
            """
            select count(*) from tenant_invitations
            where tenant_id=? and lower(invited_email)=? and accepted_at is null
              and revoked_at is null and expires_at>now()
            """,
            Integer.class,
            tenantId,
            email);
    if (pending != null && pending > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "A pending invitation already exists");
    }

    String rawToken = randomToken();
    UUID invitationId = UUID.randomUUID();
    Instant expiresAt = Instant.now().plusSeconds(expirationHours * 3600);
    jdbc.update(
        """
insert into tenant_invitations(id,tenant_id,invited_email,role,token_hash,expires_at,created_by)
values(?,?,?,?,?,?,?)
""",
        invitationId,
        tenantId,
        email,
        request.role(),
        hash(rawToken),
        java.sql.Timestamp.from(expiresAt),
        actor.userId());

    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(email);
    message.setSubject("Invitation to join a CRM workspace");
    String base = frontendUrl.replaceAll("/+$", "");
    message.setText(
        "You have been invited to join a CRM workspace. Accept the invitation using this link: "
            + base
            + "/accept-invitation#token="
            + rawToken
            + ". This link expires in "
            + expirationHours
            + " hours.");
    mail.getObject().send(message);
    return ApiResponse.ok(
        "Invitation sent", new InvitationView(invitationId, email, request.role(), expiresAt));
  }

  private static String randomToken() {
    byte[] bytes = new byte[48];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to hash invitation token", e);
    }
  }
}
