package com.company.crm.lead;

import com.company.crm.audit.AuditService;
import com.company.crm.client.*;
import com.company.crm.common.ApiResponse;
import com.company.crm.contact.Contact;
import com.company.crm.contact.ContactRepository;
import com.company.crm.security.TenantContext;
import com.company.crm.security.TenantMembershipService;
import com.company.crm.user.User;
import com.company.crm.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/leads")
public class LeadConversionController {
  private final LeadRepository leads;
  private final ClientRepository clients;
  private final ContactRepository contacts;
  private final UserRepository users;
  private final TenantMembershipService memberships;
  private final JdbcTemplate jdbc;
  private final AuditService audit;

  public LeadConversionController(
      LeadRepository leads,
      ClientRepository clients,
      ContactRepository contacts,
      UserRepository users,
      TenantMembershipService memberships,
      JdbcTemplate jdbc,
      AuditService audit) {
    this.leads = leads;
    this.clients = clients;
    this.contacts = contacts;
    this.users = users;
    this.memberships = memberships;
    this.jdbc = jdbc;
    this.audit = audit;
  }

  public record ConversionRequest(
      @NotBlank @Size(max = 200) String companyName,
      @Size(max = 100) String industry,
      @Size(max = 255) String website,
      @Email @Size(max = 255) String email,
      @Size(max = 30) String phone,
      String address,
      String notes,
      @Size(max = 120) String contactFullName,
      @Size(max = 120) String contactJobTitle,
      @Email @Size(max = 255) String contactEmail,
      @Size(max = 30) String contactPhone,
      String contactNotes) {}

  public record ConversionResult(UUID leadId, UUID clientId, UUID contactId) {}

  @PostMapping("/{id}/convert")
  @Transactional
  public ApiResponse<ConversionResult> convert(
      @PathVariable UUID id,
      @Valid @RequestBody ConversionRequest request,
      Authentication authentication) {
    UUID tenantId = TenantContext.requireTenantId();
    boolean all =
        authentication.getAuthorities().stream()
            .anyMatch(
                a ->
                    a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_MANAGER"));
    Lead lead =
        leads
            .visible(tenantId, id, all, authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
    if (lead.getStatus() != LeadStatus.WON)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only won leads can be converted.");
    if (lead.getClient() != null)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Lead is already linked to a client.");

    User owner = lead.getAssignedTo();
    memberships.requireActiveMember(owner.getId());
    Client client =
        clients.saveAndFlush(
            new Client(
                request.companyName(),
                request.industry(),
                request.website(),
                request.email(),
                request.phone(),
                request.address(),
                request.notes(),
                owner,
                ClientStatus.ACTIVE));

    boolean hasContactDetails =
        present(request.contactFullName())
            || present(request.contactJobTitle())
            || present(request.contactEmail())
            || present(request.contactPhone())
            || present(request.contactNotes());
    UUID contactId = null;
    if (hasContactDetails) {
      if (!present(request.contactFullName()))
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Contact name is required when contact details are supplied.");
      Contact contact =
          new Contact(
              client,
              request.contactFullName().trim(),
              request.contactJobTitle(),
              request.contactEmail(),
              request.contactPhone(),
              true,
              request.contactNotes());
      contactId = contacts.saveAndFlush(contact).getId();
    }

    lead.convertedTo(client, contactId);
    leads.saveAndFlush(lead);
    User actor = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
    jdbc.update(
        "insert into"
            + " activities(id,tenant_id,client_id,lead_id,contact_id,user_id,activity_type,subject,description,occurred_at)"
            + " values(?,?,?,?,? ,?,'NOTE',?,?,?)",
        UUID.randomUUID(),
        tenantId,
        client.getId(),
        lead.getId(),
        contactId,
        actor.getId(),
        "Lead converted to client",
        lead.getTitle(),
        Timestamp.from(Instant.now()));
    audit.record(authentication, "LEAD_CONVERTED_TO_CLIENT", "LEAD", lead.getId());
    return ApiResponse.ok(
        "Lead converted to client", new ConversionResult(lead.getId(), client.getId(), contactId));
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }
}
