package com.company.crm.activity.dto;

import com.company.crm.activity.ActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record ActivityRequest(
    @NotNull UUID clientId,
    UUID leadId,
    UUID contactId,
    @NotNull ActivityType activityType,
    @NotBlank @Size(max = 200) String subject,
    String description,
    Instant occurredAt) {}
