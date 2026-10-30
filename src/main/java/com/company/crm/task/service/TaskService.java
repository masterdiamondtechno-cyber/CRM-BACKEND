package com.company.crm.task.service;

import com.company.crm.task.TaskPriority;
import com.company.crm.task.TaskStatus;
import com.company.crm.task.dto.TaskRequest;
import com.company.crm.task.dto.TaskStatusRequest;
import com.company.crm.task.dto.TaskView;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

public interface TaskService {

  Page<TaskView> listTasks(
      TaskStatus status,
      TaskPriority priority,
      UUID assignedTo,
      Instant dueFrom,
      Instant dueTo,
      boolean overdue,
      String search,
      int page,
      int size,
      Authentication auth);

  TaskView createTask(TaskRequest request, Authentication auth);

  TaskView getTask(UUID id, Authentication auth);

  TaskView updateTask(UUID id, TaskRequest request, Authentication auth);

  TaskView updateTaskStatus(UUID id, TaskStatusRequest request, Authentication auth);
}
