package com.company.crm.auth;

import com.company.crm.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class RegistrationVerificationController {
  private final JdbcTemplate jdbc;

  public RegistrationVerificationController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record VerifyRequest(@NotBlank String token) {}

  @PostMapping("/verify-registration")
  @Transactional
  public ApiResponse<Void> verify(@Valid @RequestBody VerifyRequest request) {
    UUID userId;
    try {
      userId =
          jdbc.queryForObject(
              """
              select user_id from registration_verification_tokens
              where token_hash=? and used_at is null and expires_at>now()
              for update
              """,
              UUID.class,
              hash(request.token()));
    } catch (org.springframework.dao.EmptyResultDataAccessException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Verification link is invalid or expired");
    }
    jdbc.update("update users set email_verified=true where id=?", userId);
    jdbc.update(
        "update registration_verification_tokens set used_at=now() where token_hash=?",
        hash(request.token()));
    return ApiResponse.ok("Email verified. You can now sign in.", null);
  }

  private static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to hash verification token", e);
    }
  }
}
