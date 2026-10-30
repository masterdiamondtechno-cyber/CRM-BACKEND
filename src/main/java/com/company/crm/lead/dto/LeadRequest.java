package com.company.crm.lead.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LeadRequest(
    @NotBlank @Size(max = 200) String title,
    String description,
    UUID clientId,
    UUID contactId,
    UUID assignedTo,
    String source,
    @DecimalMin("0.0") BigDecimal estimatedValue,
    LocalDate expectedCloseDate) {}
