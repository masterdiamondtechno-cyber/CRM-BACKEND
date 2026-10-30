package com.company.crm.lead.dto;

import com.company.crm.lead.LeadStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record LeadStatusRequest(
    @NotNull LeadStatus status, String lostReason, @DecimalMin("0.0") BigDecimal finalValue) {}
