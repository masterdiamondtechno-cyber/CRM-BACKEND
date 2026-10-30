package com.company.crm.task;

import com.company.crm.client.Client;
import com.company.crm.security.TenantContext;
import com.company.crm.user.User;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Task {
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

  @Column(name = "lead_id")
  private UUID leadId;

  @Column(name = "project_id")
  private UUID projectId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_to", nullable = false)
  private User assignedTo;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by", nullable = false)
  private User createdBy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private TaskPriority priority = TaskPriority.MEDIUM;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TaskStatus status = TaskStatus.TODO;

  @Column(name = "due_at")
  private Instant dueAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Version private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected Task() {}

  @PrePersist
  void attachTenant() {
    tenantId = TenantContext.requireTenantId();
  }

  public void edit(
      String t,
      String d,
      Client c,
      UUID contact,
      UUID lead,
      UUID project,
      User assignee,
      TaskPriority p,
      Instant due) {
    title = t;
    description = d;
    client = c;
    contactId = contact;
    leadId = lead;
    projectId = project;
    assignedTo = assignee;
    priority = p == null ? TaskPriority.MEDIUM : p;
    dueAt = due;
    updatedAt = Instant.now();
  }

  public void changeStatus(TaskStatus s) {
    status = s;
    completedAt = s == TaskStatus.COMPLETED ? Instant.now() : null;
    updatedAt = Instant.now();
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

  public UUID getLeadId() {
    return leadId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public User getAssignedTo() {
    return assignedTo;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public TaskPriority getPriority() {
    return priority;
  }

  public TaskStatus getStatus() {
    return status;
  }

  public Instant getDueAt() {
    return dueAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void creator(User u) {
    createdBy = u;
  }
}
