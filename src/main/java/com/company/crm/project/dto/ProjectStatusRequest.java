package com.company.crm.project.dto;

import com.company.crm.project.ProjectStatus;
import jakarta.validation.constraints.NotNull;

public record ProjectStatusRequest(@NotNull ProjectStatus status) {}
