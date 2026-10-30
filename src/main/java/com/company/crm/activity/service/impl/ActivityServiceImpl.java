package com.company.crm.activity.service.impl;

import com.company.crm.activity.Activity;
import com.company.crm.activity.ActivityRepository;
import com.company.crm.activity.ActivityType;
import com.company.crm.activity.dto.ActivityRequest;
import com.company.crm.activity.dto.ActivityView;
import com.company.crm.activity.service.ActivityService;
import com.company.crm.client.Client;
import com.company.crm.client.ClientRepository;
import com.company.crm.lead.Lead;
import com.company.crm.lead.LeadRepository;
import com.company.crm.security.TenantContext;
import com.company.crm.user.UserRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ActivityServiceImpl implements ActivityService {

  private final ActivityRepository activities;
  private final ClientRepository clients;
  private final LeadRepository leads;
  private final UserRepository users;
  private final JdbcTemplate jdbc;

  public ActivityServiceImpl(
      ActivityRepository activities,
      ClientRepository clients,
      LeadRepository leads,
      UserRepository users,
      JdbcTemplate jdbc) {
    this.activities = activities;
    this.clients = clients;
    this.leads = leads;
    this.users = users;
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ActivityView> searchActivities(
      ActivityType type, String search, int page, int size, Authentication auth) {
    return activities
        .search(
            TenantContext.requireTenantId(),
            isManagerOrAdmin(auth),
            auth.getName(),
            type,
            search == null ? "" : search,
            PageRequest.of(page, size, Sort.by("occurredAt").descending()))
        .map(this::toView);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ActivityView> listByClient(UUID clientId, int page, int size, Authentication auth) {
    UUID tenantId = TenantContext.requireTenantId();
    clients
        .findVisible(tenantId, clientId, isManagerOrAdmin(auth), auth.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));

    return activities
        .byClient(
            tenantId,
            clientId,
            isManagerOrAdmin(auth),
            auth.getName(),
            PageRequest.of(page, size, Sort.by("occurredAt").descending()))
        .map(this::toView);
  }

  @Override
  public ActivityView createActivity(UUID clientId, ActivityRequest r, Authentication auth) {
    if (!clientId.equals(r.clientId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path and body client IDs differ");
    }
    UUID tenantId = TenantContext.requireTenantId();
    Client c =
        clients
            .findVisible(tenantId, clientId, isManagerOrAdmin(auth), auth.getName())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));

    if (r.leadId() != null) {
      Lead l =
          leads
              .visible(tenantId, r.leadId(), isManagerOrAdmin(auth), auth.getName())
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
      if (l.getClient() != null && !clientId.equals(l.getClient().getId())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lead must belong to client");
      }
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
      if (count == null || count == 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact must belong to client");
      }
    }

    Activity x = new Activity();
    x.set(
        c,
        r.leadId(),
        r.contactId(),
        users.findByEmailIgnoreCase(auth.getName()).orElseThrow(),
        r.activityType(),
        r.subject(),
        r.description(),
        r.occurredAt());

    return toView(activities.save(x));
  }

  @Override
  @Transactional(readOnly = true)
  public ActivityView getActivity(UUID id, Authentication auth) {
    Activity x =
        activities
            .visible(TenantContext.requireTenantId(), id, isManagerOrAdmin(auth), auth.getName())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));
    return toView(x);
  }

  private ActivityView toView(Activity x) {
    return new ActivityView(
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

  private boolean isManagerOrAdmin(Authentication a) {
    return a.getAuthorities().stream()
        .anyMatch(
            x -> x.getAuthority().equals("ROLE_ADMIN") || x.getAuthority().equals("ROLE_MANAGER"));
  }
}
