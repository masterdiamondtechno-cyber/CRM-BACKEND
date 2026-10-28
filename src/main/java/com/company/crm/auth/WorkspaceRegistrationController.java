package com.company.crm.auth;

import com.company.crm.common.ApiResponse;
import com.company.crm.security.JwtService;
import com.company.crm.user.Role;
import com.company.crm.user.User;
import com.company.crm.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/auth")
public class WorkspaceRegistrationController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> mail;
    private final String mailHost;
    private final String verificationUrl;
    private final boolean registrationEnabled;
    private final long verificationMinutes;

    public WorkspaceRegistrationController(UserRepository users, PasswordEncoder passwordEncoder,
            JdbcTemplate jdbc, ObjectProvider<JavaMailSender> mail,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${crm.registration.verification-url:}") String verificationUrl,
            @Value("${crm.registration.enabled:false}") boolean registrationEnabled,
            @Value("${crm.registration.verification-minutes:30}") long verificationMinutes) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.mail = mail;
        this.mailHost = mailHost;
        this.verificationUrl = verificationUrl;
        this.registrationEnabled = registrationEnabled;
        this.verificationMinutes = verificationMinutes;
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 200) String companyName,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 12, max = 72) String password) {}
    public record RegistrationNotice(String message) {}

    @PostMapping("/register")
    @Transactional
    public ApiResponse<RegistrationNotice> register(@Valid @RequestBody RegisterRequest request) {
        if (!registrationEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registration is unavailable");
        }
        if (mailHost.isBlank() || verificationUrl.isBlank() || mail.getIfAvailable() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email verification is not configured");
        }

        String message = "If registration can be completed, check your email for a verification link.";
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            return ApiResponse.ok(message, new RegistrationNotice(message));
        }

        UUID tenantId = UUID.randomUUID();
        String slug = slug(request.companyName()) + "-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("insert into tenants(id,name,slug,status) values(?,?,?,'ACTIVE')",
                tenantId, request.companyName().trim(), slug);

        // Global role remains least-privileged. Tenant ownership exists only in
        // the tenant_memberships record and the user cannot authenticate until verified.
        User user = new User(request.fullName().trim(), email,
                passwordEncoder.encode(request.password()), Role.EMPLOYEE);
        user.setEmailVerified(false);
        user = users.save(user);
        jdbc.update("""
                insert into tenant_memberships(tenant_id,user_id,role,membership_status)
                values(?,?,'OWNER','ACTIVE')
                """, tenantId, user.getId());

        String rawToken = randomToken();
        jdbc.update("""
                insert into registration_verification_tokens(id,user_id,token_hash,expires_at)
                values(?,?,?,?)
                """, UUID.randomUUID(), user.getId(), hash(rawToken),
                java.sql.Timestamp.from(Instant.now().plusSeconds(verificationMinutes * 60)));

        SimpleMailMessage emailMessage = new SimpleMailMessage();
        emailMessage.setTo(email);
        emailMessage.setSubject("Verify your CRM workspace account");
        String base = verificationUrl.replaceAll("/+$", "");
        emailMessage.setText("Verify your email and activate your CRM workspace: "
                + base + "/verify-registration#token=" + rawToken
                + ". This link expires in " + verificationMinutes + " minutes.");
        mail.getObject().send(emailMessage);
        return ApiResponse.ok(message, new RegistrationNotice(message));
    }

    private static String slug(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? "workspace" : normalized.substring(0, Math.min(60, normalized.length()));
    }

    private static String randomToken() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash verification token", e);
        }
    }
}

