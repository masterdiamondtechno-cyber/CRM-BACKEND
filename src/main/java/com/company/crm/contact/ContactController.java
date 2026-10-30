package com.company.crm.contact;

import com.company.crm.common.ApiResponse;
import com.company.crm.contact.dto.ContactRequest;
import com.company.crm.contact.dto.ContactView;
import com.company.crm.contact.service.ContactService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class ContactController {

  private final ContactService contactService;

  public ContactController(ContactService contactService) {
    this.contactService = contactService;
  }

  @GetMapping("/contacts")
  public ApiResponse<Page<ContactView>> search(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      Authentication auth) {
    return ApiResponse.ok("Contacts", contactService.searchContacts(search, page, size, auth));
  }

  @GetMapping("/clients/{clientId}/contacts")
  public ApiResponse<Page<ContactView>> list(
      @PathVariable UUID clientId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      Authentication auth) {
    return ApiResponse.ok(
        "Contacts", contactService.listContactsByClient(clientId, page, size, auth));
  }

  @PostMapping("/clients/{clientId}/contacts")
  public ResponseEntity<ApiResponse<ContactView>> create(
      @PathVariable UUID clientId,
      @Valid @RequestBody ContactRequest request,
      Authentication auth) {
    return ResponseEntity.status(201)
        .body(
            ApiResponse.ok(
                "Contact created", contactService.createContact(clientId, request, auth)));
  }

  @GetMapping("/contacts/{id}")
  public ApiResponse<ContactView> get(@PathVariable UUID id, Authentication auth) {
    return ApiResponse.ok("Contact", contactService.getContact(id, auth));
  }

  @PatchMapping("/contacts/{id}")
  public ApiResponse<ContactView> update(
      @PathVariable UUID id, @Valid @RequestBody ContactRequest request, Authentication auth) {
    return ApiResponse.ok("Contact updated", contactService.updateContact(id, request, auth));
  }

  @DeleteMapping("/contacts/{id}")
  public ResponseEntity<Void> archive(@PathVariable UUID id, Authentication auth) {
    contactService.archiveContact(id, auth);
    return ResponseEntity.noContent().build();
  }
}
