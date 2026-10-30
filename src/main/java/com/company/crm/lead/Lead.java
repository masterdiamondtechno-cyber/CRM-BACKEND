package com.company.crm.lead;

import com.company.crm.client.Client;
import com.company.crm.security.TenantContext;
import com.company.crm.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "leads")
public class Lead {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(columnDefinition = "text")
  private String description;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "client_id")
  private Client client;

  @Column(name = "contact_id")
  private UUID contactId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_to", nullable = false)
  private User assignedTo;

  @Column(length = 80)
  private String source;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private LeadStatus status = LeadStatus.NEW;

  @Column(name = "estimated_value", precision = 14, scale = 2)
  private BigDecimal estimatedValue;

  @Column(name = "final_value", precision = 14, scale = 2)
  private BigDecimal finalValue;

  @Column(name = "expected_close_date")
  private LocalDate expectedCloseDate;

  @Column(name = "lost_reason", columnDefinition = "text")
  private String lostReason;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Version private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected Lead() {}

  @PrePersist
  void attachTenant() {
    tenantId = TenantContext.requireTenantId();
  }

  public UUID getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public Client getClient() {
    return client;
  }

  public UUID getContactId() {
    return contactId;
  }

  public User getAssignedTo() {
    return assignedTo;
  }

  public String getSource() {
    return source;
  }

  public LeadStatus getStatus() {
    return status;
  }

  public BigDecimal getEstimatedValue() {
    return estimatedValue;
  }

  public BigDecimal getFinalValue() {
    return finalValue;
  }

  public LocalDate getExpectedCloseDate() {
    return expectedCloseDate;
  }

  public String getLostReason() {
    return lostReason;
  }

  public void convertedTo(Client c, UUID contact) {
    client = c;
    contactId = contact;
    updatedAt = Instant.now();
  }

  public void edit(
      String t,
      String d,
      Client c,
      UUID contact,
      User assignee,
      String src,
      BigDecimal value,
      LocalDate close) {
    title = t;
    description = d;
    client = c;
    contactId = contact;
    assignedTo = assignee;
    source = src;
    estimatedValue = value;
    expectedCloseDate = close;
    updatedAt = Instant.now();
  }

  public void status(LeadStatus s, String reason, BigDecimal finalValue) {
    status = s;
    lostReason = reason;
    this.finalValue = finalValue;
    updatedAt = Instant.now();
  }
}
