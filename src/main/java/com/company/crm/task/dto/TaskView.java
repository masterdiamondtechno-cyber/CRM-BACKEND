package com.company.crm.task.dto;

import com.company.crm.task.TaskPriority;
import com.company.crm.task.TaskStatus;
import java.time.Instant;
import java.util.UUID;

public record TaskView(
    UUID id,
    String title,
    String description,
    UUID clientId,
    UUID leadId,
    UUID projectId,
    UUID assignedTo,
    String assignee,
    TaskPriority priority,
    TaskStatus status,
    Instant dueAt,
    Instant completedAt) {}
