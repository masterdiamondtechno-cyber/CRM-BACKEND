package com.company.crm.lead.service.impl;

import com.company.crm.audit.AuditService;
import com.company.crm.client.Client;
import com.company.crm.client.ClientRepository;
import com.company.crm.client.ClientStatus;
import com.company.crm.contact.Contact;
import com.company.crm.contact.ContactRepository;
import com.company.crm.lead.Lead;
import com.company.crm.lead.LeadRepository;
import com.company.crm.lead.LeadStatus;
import com.company.crm.lead.dto.*;
import com.company.crm.lead.service.LeadService;
import com.company.crm.security.TenantContext;
import com.company.crm.security.TenantMembershipService;
import com.company.crm.user.User;
import com.company.crm.user.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class LeadServiceImpl implements LeadService {

  private final LeadRepository leads;
  private final UserRepository users;
  private final ClientRepository clients;
  private final ContactRepository contacts;
  private final JdbcTemplate jdbc;
  private final TenantMembershipService memberships;
  private final AuditService audit;

  public LeadServiceImpl(
      LeadRepository leads,
      UserRepository users,
      ClientRepository clients,
      ContactRepository contacts,
      JdbcTemplate jdbc,
      TenantMembershipService memberships,
      AuditService audit) {
    this.leads = leads;
    this.users = users;
    this.clients = clients;
    this.contacts = contacts;
    this.jdbc = jdbc;
    this.memberships = memberships;
    this.audit = audit;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<LeadView> listLeads(
      LeadStatus status, String search, int page, int size, Authentication auth) {
    return leads
        .search(
            TenantContext.requireTenantId(),
            isManagerOrAdmin(auth),
            auth.getName(),
            status,
            search == null ? "" : search,
            PageRequest.of(page, size, Sort.by("createdAt").descending()))
        .map(this::toView);
  }

  @Override
  public LeadView createLead(LeadRequest r, Authentication auth) {
    User actor = getUser(auth);
    User assignee =
        r.assignedTo() == null
            ? actor
            : users
                .findById(r.assignedTo())
                .orElseThrow(
                    () ->
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee not found"));
    memberships.requireActiveMember(assignee.getId());
    if (auth.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_EMPLOYEE"))
        && !assignee.getId().equals(actor.getId())) {
      throw new AccessDeniedException("Cannot assign another employee");
    }

    UUID tenantId = TenantContext.requireTenantId();
    Client client =
        r.clientId() == null
            ? null
            : clients
                .findVisible(tenantId, r.clientId(), isManagerOrAdmin(auth), auth.getName())
                .orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));

    if (r.contactId() != null) {
      Integer n =
          jdbc.queryForObject(
              "select count(*) from contacts where tenant_id=? and id=? and client_id=?",
              Integer.class,
              tenantId,
              r.contactId(),
              r.clientId());
      if (n == null || n == 0) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Contact must belong to linked client");
      }
    }

    Lead l = new Lead();
    l.edit(
        r.title(),
        r.description(),
        client,
        r.contactId(),
        assignee,
        r.source(),
        r.estimatedValue(),
        r.expectedCloseDate());

    return toView(leads.save(l));
  }

  @Override
  @Transactional(readOnly = true)
  public LeadView getLead(UUID id, Authentication auth) {
    return toView(findVisibleLead(id, auth));
  }

  @Override
  public LeadView updateLead(UUID id, LeadRequest r, Authentication auth) {
    Lead l = findVisibleLead(id, auth);
    User actor = getUser(auth);
    User assignee = l.getAssignedTo();
    if (r.assignedTo() != null) {
      if (auth.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_EMPLOYEE"))
          && !r.assignedTo().equals(actor.getId())) {
        throw new AccessDeniedException("Cannot reassign to another employee");
      }
      memberships.requireActiveMember(r.assignedTo());
      assignee =
          users
              .findById(r.assignedTo())
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee not found"));
    }

    UUID tenantId = TenantContext.requireTenantId();
    Client client = l.getClient();
    if (r.clientId() != null) {
      client =
          clients
              .findVisible(tenantId, r.clientId(), isManagerOrAdmin(auth), auth.getName())
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    }

    UUID contactId = r.contactId() == null ? l.getContactId() : r.contactId();
    if (contactId != null && client != null) {
      Integer n =
          jdbc.queryForObject(
              "select count(*) from contacts where tenant_id=? and id=? and client_id=?",
              Integer.class,
              tenantId,
              contactId,
              client.getId());
      if (n == null || n == 0) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Contact must belong to linked client");
      }
    }

    l.edit(
        r.title(),
        r.description(),
        client,
        contactId,
        assignee,
        r.source(),
        r.estimatedValue(),
        r.expectedCloseDate());

    return toView(leads.save(l));
  }

  @Override
  public LeadView updateLeadStatus(UUID id, LeadStatusRequest r, Authentication auth) {
    Lead l = findVisibleLead(id, auth);
    l.status(r.status(), r.lostReason(), r.finalValue());
    return toView(leads.save(l));
  }

  @Override
  public void archiveLead(UUID id, Authentication auth) {
    Lead l = findVisibleLead(id, auth);
    if (auth.getAuthorities().stream().noneMatch(x -> x.getAuthority().equals("ROLE_ADMIN"))) {
      throw new AccessDeniedException("Admin required to delete leads");
    }
    jdbc.update("update leads set archived_at=now() where id=?", l.getId());
  }

  @Override
  public ConversionResult convertLead(UUID id, ConversionRequest request, Authentication auth) {
    UUID tenantId = TenantContext.requireTenantId();
    Lead lead = findVisibleLead(id, auth);

    if (lead.getStatus() != LeadStatus.WON) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only won leads can be converted.");
    }
    if (lead.getClient() != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Lead is already linked to a client.");
    }

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
        isPresent(request.contactFullName())
            || isPresent(request.contactJobTitle())
            || isPresent(request.contactEmail())
            || isPresent(request.contactPhone())
            || isPresent(request.contactNotes());

    UUID contactId = null;
    if (hasContactDetails) {
      if (!isPresent(request.contactFullName())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Contact name is required when contact details are supplied.");
      }
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
    User actor = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
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

    audit.record(auth, "LEAD_CONVERTED_TO_CLIENT", "LEAD", lead.getId());
    return new ConversionResult(lead.getId(), client.getId(), contactId);
  }

  private Lead findVisibleLead(UUID id, Authentication auth) {
    return leads
        .visible(TenantContext.requireTenantId(), id, isManagerOrAdmin(auth), auth.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
  }

  private LeadView toView(Lead l) {
    return new LeadView(
        l.getId(),
        l.getTitle(),
        l.getDescription(),
        l.getClient() == null ? null : l.getClient().getId(),
        l.getContactId(),
        l.getAssignedTo().getId(),
        l.getAssignedTo().getFullName(),
        l.getSource(),
        l.getStatus(),
        l.getEstimatedValue(),
        l.getFinalValue(),
        l.getExpectedCloseDate(),
        l.getLostReason());
  }

  private boolean isManagerOrAdmin(Authentication a) {
    return a.getAuthorities().stream()
        .anyMatch(
            x -> x.getAuthority().equals("ROLE_ADMIN") || x.getAuthority().equals("ROLE_MANAGER"));
  }

  private User getUser(Authentication a) {
    return users.findByEmailIgnoreCase(a.getName()).orElseThrow();
  }

  private static boolean isPresent(String value) {
    return value != null && !value.isBlank();
  }
}
