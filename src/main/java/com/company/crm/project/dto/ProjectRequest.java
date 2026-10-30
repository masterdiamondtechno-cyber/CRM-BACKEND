package com.company.crm.project.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProjectRequest(
    @NotBlank @Size(max = 200) String name,
    @NotNull UUID clientId,
    String description,
    LocalDate startDate,
    LocalDate expectedEndDate,
    UUID ownerId,
    @DecimalMin("0.0") BigDecimal budget) {}
