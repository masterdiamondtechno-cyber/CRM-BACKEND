package com.company.crm.task.dto;

import com.company.crm.task.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskStatusRequest(@NotNull TaskStatus status) {}
