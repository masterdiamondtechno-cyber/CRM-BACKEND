package com.company.crm.activity.dto;

import com.company.crm.activity.ActivityType;
import java.time.Instant;
import java.util.UUID;

public record ActivityView(
    UUID id,
    UUID clientId,
    UUID leadId,
    UUID contactId,
    UUID userId,
    String userName,
    ActivityType activityType,
    String subject,
    String description,
    Instant occurredAt) {}
