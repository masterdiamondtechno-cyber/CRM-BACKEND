package com.company.crm.activity;

import com.company.crm.client.Client;
import com.company.crm.security.TenantContext;
import com.company.crm.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activities")
public class Activity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "client_id", nullable = false)
  private Client client;

  @Column(name = "lead_id")
  private UUID leadId;

  @Column(name = "contact_id")
  private UUID contactId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(name = "activity_type", nullable = false, length = 20)
  private ActivityType type;

  @Column(nullable = false, length = 200)
  private String subject;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public Activity() {}

  @PrePersist
  void attachTenant() {
    tenantId = TenantContext.requireTenantId();
  }

  public void set(
      Client c, UUID lead, UUID contact, User u, ActivityType t, String s, String d, Instant when) {
    client = c;
    leadId = lead;
    contactId = contact;
    user = u;
    type = t;
    subject = s;
    description = d;
    occurredAt = when == null ? Instant.now() : when;
  }

  public UUID getId() {
    return id;
  }

  public Client getClient() {
    return client;
  }

  public UUID getLeadId() {
    return leadId;
  }

  public UUID getContactId() {
    return contactId;
  }

  public User getUser() {
    return user;
  }

  public ActivityType getType() {
    return type;
  }

  public String getSubject() {
    return subject;
  }

  public String getDescription() {
    return description;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
