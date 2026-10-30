package com.company.crm.project.service;

import com.company.crm.project.ProjectStatus;
import com.company.crm.project.dto.ProjectRequest;
import com.company.crm.project.dto.ProjectStatusRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;

public interface ProjectService {

  List<Map<String, Object>> listProjects(ProjectStatus status, String search, Authentication auth);

  Map<String, Object> createProject(ProjectRequest request, Authentication auth);

  Map<String, Object> getProject(UUID id, Authentication auth);

  Map<String, Object> updateProject(UUID id, ProjectRequest request, Authentication auth);

  Map<String, Object> updateProjectStatus(
      UUID id, ProjectStatusRequest request, Authentication auth);

  Map<String, Object> addMember(UUID projectId, UUID userId, Authentication auth);

  void removeMember(UUID projectId, UUID userId, Authentication auth);
}
