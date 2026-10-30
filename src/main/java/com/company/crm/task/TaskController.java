package com.company.crm.task;

import com.company.crm.common.ApiResponse;
import com.company.crm.task.dto.TaskRequest;
import com.company.crm.task.dto.TaskStatusRequest;
import com.company.crm.task.dto.TaskView;
import com.company.crm.task.service.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
@Validated
public class TaskController {

  private final TaskService taskService;

  public TaskController(TaskService taskService) {
    this.taskService = taskService;
  }

  @GetMapping
  public ApiResponse<Page<TaskView>> list(
      @RequestParam(required = false) TaskStatus status,
      @RequestParam(required = false) TaskPriority priority,
      @RequestParam(required = false) UUID assignedTo,
      @RequestParam(required = false) Instant dueFrom,
      @RequestParam(required = false) Instant dueTo,
      @RequestParam(defaultValue = "false") boolean overdue,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      Authentication auth) {
    return ApiResponse.ok(
        "Tasks",
        taskService.listTasks(
            status, priority, assignedTo, dueFrom, dueTo, overdue, search, page, size, auth));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<TaskView>> create(
      @Valid @RequestBody TaskRequest request, Authentication auth) {
    return ResponseEntity.status(201)
        .body(ApiResponse.ok("Task created", taskService.createTask(request, auth)));
  }

  @GetMapping("/{id}")
  public ApiResponse<TaskView> get(@PathVariable UUID id, Authentication auth) {
    return ApiResponse.ok("Task", taskService.getTask(id, auth));
  }

  @PatchMapping("/{id}")
  public ApiResponse<TaskView> update(
      @PathVariable UUID id, @Valid @RequestBody TaskRequest request, Authentication auth) {
    return ApiResponse.ok("Task updated", taskService.updateTask(id, request, auth));
  }

  @PatchMapping("/{id}/status")
  public ApiResponse<TaskView> updateStatus(
      @PathVariable UUID id, @Valid @RequestBody TaskStatusRequest request, Authentication auth) {
    return ApiResponse.ok("Task status updated", taskService.updateTaskStatus(id, request, auth));
  }
}
