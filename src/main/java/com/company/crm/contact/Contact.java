package com.company.crm.contact;

import com.company.crm.client.Client;
import com.company.crm.security.TenantContext;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contacts")
public class Contact {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "client_id", nullable = false)
  private Client client;

  @Column(name = "full_name", nullable = false, length = 120)
  private String fullName;

  @Column(name = "job_title", length = 120)
  private String jobTitle;

  @Column(length = 255)
  private String email;

  @Column(length = 30)
  private String phone;

  @Column(name = "is_primary", nullable = false)
  private boolean primary;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected Contact() {}

  public Contact(
      Client c, String n, String title, String email, String phone, boolean primary, String notes) {
    set(c, n, title, email, phone, primary, notes);
  }

  @PrePersist
  void attachTenant() {
    tenantId = TenantContext.requireTenantId();
  }

  public void set(
      Client c, String n, String title, String email, String phone, boolean primary, String notes) {
    client = c;
    fullName = n;
    jobTitle = title;
    this.email = email;
    this.phone = phone;
    this.primary = primary;
    this.notes = notes;
    updatedAt = Instant.now();
  }

  public void archive() {
    archivedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public Client getClient() {
    return client;
  }

  public String getFullName() {
    return fullName;
  }

  public String getJobTitle() {
    return jobTitle;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public boolean isPrimary() {
    return primary;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }
}
