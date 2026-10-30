package com.company.crm.activity;

import com.company.crm.client.*;
import com.company.crm.common.ApiResponse;
import com.company.crm.lead.*;
import com.company.crm.security.TenantContext;
import com.company.crm.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
@Validated
public class ActivityController {
  private final ActivityRepository activities;
  private final ClientRepository clients;
  private final LeadRepository leads;
  private final UserRepository users;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public ActivityController(
      ActivityRepository a,
      ClientRepository c,
      LeadRepository l,
      UserRepository u,
      org.springframework.jdbc.core.JdbcTemplate j) {
    activities = a;
    clients = c;
    leads = l;
    users = u;
    jdbc = j;
  }

  public record Request(
      @NotNull UUID clientId,
      UUID leadId,
      UUID contactId,
      @NotNull ActivityType activityType,
      @NotBlank @Size(max = 200) String subject,
      String description,
      Instant occurredAt) {}

  public record View(
      UUID id,
      UUID clientId,
      UUID leadId,
      UUID contactId,
      UUID userId,
      String userName,
      ActivityType activityType,
      String subject,
      String description,
      Instant occurredAt) {}

  private boolean all(Authentication a) {
    return a.getAuthorities().stream()
        .anyMatch(
            x -> x.getAuthority().equals("ROLE_ADMIN") || x.getAuthority().equals("ROLE_MANAGER"));
  }

  private View view(Activity x) {
    return new View(
        x.getId(),
        x.getClient().getId(),
        x.getLeadId(),
        x.getContactId(),
        x.getUser().getId(),
        x.getUser().getFullName(),
        x.getType(),
        x.getSubject(),
        x.getDescription(),
        x.getOccurredAt());
  }

  @GetMapping("/activities")
  public ApiResponse<Page<View>> search(
      @RequestParam(required = false) ActivityType type,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      Authentication a) {
    return ApiResponse.ok(
        "Activities",
        activities
            .search(
                TenantContext.requireTenantId(),
                all(a),
                a.getName(),
                type,
                search == null ? "" : search,
                PageRequest.of(page, size, Sort.by("occurredAt").descending()))
            .map(this::view));
  }

  @GetMapping("/clients/{clientId}/activities")
  public ApiResponse<Page<View>> byClient(
      @PathVariable UUID clientId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      Authentication a) {
    UUID tenantId = TenantContext.requireTenantId();
    clients
        .findVisible(tenantId, clientId, all(a), a.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    return ApiResponse.ok(
        "Activities",
        activities
            .byClient(
                tenantId,
                clientId,
                all(a),
                a.getName(),
                PageRequest.of(page, size, Sort.by("occurredAt").descending()))
            .map(this::view));
  }

  @PostMapping("/clients/{clientId}/activities")
  public ResponseEntity<ApiResponse<View>> create(
      @PathVariable UUID clientId, @Valid @RequestBody Request r, Authentication a) {
    if (!clientId.equals(r.clientId()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path and body client IDs differ");
    UUID tenantId = TenantContext.requireTenantId();
    Client c =
        clients
            .findVisible(tenantId, clientId, all(a), a.getName())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    if (r.leadId() != null) {
      Lead l =
          leads
              .visible(tenantId, r.leadId(), all(a), a.getName())
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
      if (l.getClient() != null && !clientId.equals(l.getClient().getId()))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lead must belong to client");
    }
    if (r.contactId() != null) {
      Integer count =
          jdbc.queryForObject(
              "select count(*) from contacts where tenant_id=? and id=? and client_id=? and"
                  + " archived_at is null",
              Integer.class,
              tenantId,
              r.contactId(),
              clientId);
      if (count == null || count == 0)
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact must belong to client");
    }
    Activity x = new Activity();
    x.set(
        c,
        r.leadId(),
        r.contactId(),
        users.findByEmailIgnoreCase(a.getName()).orElseThrow(),
        r.activityType(),
        r.subject(),
        r.description(),
        r.occurredAt());
    return ResponseEntity.status(201)
        .body(ApiResponse.ok("Activity recorded", view(activities.save(x))));
  }

  @GetMapping("/activities/{id}")
  public ApiResponse<View> get(@PathVariable UUID id, Authentication a) {
    Activity x =
        activities
            .visible(TenantContext.requireTenantId(), id, all(a), a.getName())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));
    return ApiResponse.ok("Activity", view(x));
  }
}
