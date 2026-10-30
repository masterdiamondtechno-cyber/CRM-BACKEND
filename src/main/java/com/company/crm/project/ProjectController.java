package com.company.crm.project;

import com.company.crm.common.ApiResponse;
import com.company.crm.project.dto.ProjectRequest;
import com.company.crm.project.dto.ProjectStatusRequest;
import com.company.crm.project.service.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
@Validated
public class ProjectController {

  private final ProjectService projectService;

  public ProjectController(ProjectService projectService) {
    this.projectService = projectService;
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> list(
      @RequestParam(required = false) ProjectStatus status,
      @RequestParam(required = false) String search,
      Authentication auth) {
    return ApiResponse.ok("Projects", projectService.listProjects(status, search, auth));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<Map<String, Object>>> create(
      @Valid @RequestBody ProjectRequest request, Authentication auth) {
    return ResponseEntity.status(201)
        .body(ApiResponse.ok("Project created", projectService.createProject(request, auth)));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> get(@PathVariable UUID id, Authentication auth) {
    return ApiResponse.ok("Project", projectService.getProject(id, auth));
  }

  @PatchMapping("/{id}")
  public ApiResponse<Map<String, Object>> update(
      @PathVariable UUID id, @Valid @RequestBody ProjectRequest request, Authentication auth) {
    return ApiResponse.ok("Project updated", projectService.updateProject(id, request, auth));
  }

  @PatchMapping("/{id}/status")
  public ApiResponse<Map<String, Object>> status(
      @PathVariable UUID id,
      @Valid @RequestBody ProjectStatusRequest request,
      Authentication auth) {
    return ApiResponse.ok(
        "Project status updated", projectService.updateProjectStatus(id, request, auth));
  }

  @PostMapping("/{id}/members")
  public ApiResponse<Map<String, Object>> addMember(
      @PathVariable UUID id, @RequestBody Map<String, UUID> body, Authentication auth) {
    return ApiResponse.ok(
        "Project member added", projectService.addMember(id, body.get("userId"), auth));
  }

  @DeleteMapping("/{id}/members/{userId}")
  public ResponseEntity<Void> removeMember(
      @PathVariable UUID id, @PathVariable UUID userId, Authentication auth) {
    projectService.removeMember(id, userId, auth);
    return ResponseEntity.noContent().build();
  }
}
