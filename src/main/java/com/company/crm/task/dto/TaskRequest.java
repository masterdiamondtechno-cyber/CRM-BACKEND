package com.company.crm.task.dto;

import com.company.crm.task.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record TaskRequest(
    @NotBlank @Size(max = 200) String title,
    String description,
    UUID clientId,
    UUID contactId,
    UUID leadId,
    UUID projectId,
    UUID assignedTo,
    TaskPriority priority,
    Instant dueAt) {}
