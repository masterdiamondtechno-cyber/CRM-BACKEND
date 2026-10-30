package com.company.crm.contact;

import com.company.crm.client.*;
import com.company.crm.common.ApiResponse;
import com.company.crm.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
public class ContactController {
  private final ContactRepository contacts;
  private final ClientRepository clients;

  public ContactController(ContactRepository c, ClientRepository cl) {
    contacts = c;
    clients = cl;
  }

  public record Request(
      @NotBlank @Size(max = 120) String fullName,
      @Size(max = 120) String jobTitle,
      @Email @Size(max = 255) String email,
      @Pattern(regexp = "^[+0-9() .-]{0,30}$") String phone,
      boolean primary,
      String notes) {}

  public record View(
      UUID id,
      UUID clientId,
      String fullName,
      String jobTitle,
      String email,
      String phone,
      boolean primary,
      String notes) {}

  private boolean all(Authentication a) {
    return a.getAuthorities().stream()
        .anyMatch(
            x -> x.getAuthority().equals("ROLE_ADMIN") || x.getAuthority().equals("ROLE_MANAGER"));
  }

  private View view(Contact c) {
    return new View(
        c.getId(),
        c.getClient().getId(),
        c.getFullName(),
        c.getJobTitle(),
        c.getEmail(),
        c.getPhone(),
        c.isPrimary(),
        c.getNotes());
  }

  @GetMapping("/contacts")
  public ApiResponse<Page<View>> search(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      Authentication a) {
    return ApiResponse.ok(
        "Contacts",
        contacts
            .search(
                TenantContext.requireTenantId(),
                all(a),
                a.getName(),
                search == null ? "" : search,
                PageRequest.of(page, size, Sort.by("fullName")))
            .map(this::view));
  }

  @GetMapping("/clients/{clientId}/contacts")
  public ApiResponse<Page<View>> list(
      @PathVariable UUID clientId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      Authentication a) {
    UUID tenantId = TenantContext.requireTenantId();
    clients
        .findVisible(tenantId, clientId, all(a), a.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    return ApiResponse.ok(
        "Contacts",
        contacts
            .byClient(
                tenantId,
                clientId,
                all(a),
                a.getName(),
                PageRequest.of(page, size, Sort.by("fullName")))
            .map(this::view));
  }

  @PostMapping("/clients/{clientId}/contacts")
  public ResponseEntity<ApiResponse<View>> create(
      @PathVariable UUID clientId, @Valid @RequestBody Request r, Authentication a) {
    Client c =
        clients
            .findVisible(TenantContext.requireTenantId(), clientId, all(a), a.getName())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    Contact x = new Contact();
    x.set(c, r.fullName(), r.jobTitle(), r.email(), r.phone(), r.primary(), r.notes());
    return ResponseEntity.status(201)
        .body(ApiResponse.ok("Contact created", view(contacts.save(x))));
  }

  @GetMapping("/contacts/{id}")
  public ApiResponse<View> get(@PathVariable UUID id, Authentication a) {
    return ApiResponse.ok("Contact", view(visible(id, a)));
  }

  @PatchMapping("/contacts/{id}")
  public ApiResponse<View> update(
      @PathVariable UUID id, @Valid @RequestBody Request r, Authentication a) {
    Contact x = visible(id, a);
    x.set(x.getClient(), r.fullName(), r.jobTitle(), r.email(), r.phone(), r.primary(), r.notes());
    return ApiResponse.ok("Contact updated", view(contacts.save(x)));
  }

  @DeleteMapping("/contacts/{id}")
  public ResponseEntity<Void> archive(@PathVariable UUID id, Authentication a) {
    Contact x = visible(id, a);
    x.archive();
    contacts.save(x);
    return ResponseEntity.noContent().build();
  }

  private Contact visible(UUID id, Authentication a) {
    return contacts
        .visible(TenantContext.requireTenantId(), id, all(a), a.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact not found"));
  }
}
