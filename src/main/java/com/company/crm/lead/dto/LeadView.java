package com.company.crm.lead.dto;

import com.company.crm.lead.LeadStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LeadView(
    UUID id,
    String title,
    String description,
    UUID clientId,
    UUID contactId,
    UUID assignedTo,
    String assignee,
    String source,
    LeadStatus status,
    BigDecimal estimatedValue,
    BigDecimal finalValue,
    LocalDate expectedCloseDate,
    String lostReason) {}
