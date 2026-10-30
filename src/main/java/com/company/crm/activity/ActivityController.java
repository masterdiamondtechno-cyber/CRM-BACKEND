package com.company.crm.activity;

import com.company.crm.activity.dto.ActivityRequest;
import com.company.crm.activity.dto.ActivityView;
import com.company.crm.activity.service.ActivityService;
import com.company.crm.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class ActivityController {

  private final ActivityService activityService;

  public ActivityController(ActivityService activityService) {
    this.activityService = activityService;
  }

  @GetMapping("/activities")
  public ApiResponse<Page<ActivityView>> search(
      @RequestParam(required = false) ActivityType type,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      Authentication auth) {
    return ApiResponse.ok(
        "Activities", activityService.searchActivities(type, search, page, size, auth));
  }

  @GetMapping("/clients/{clientId}/activities")
  public ApiResponse<Page<ActivityView>> byClient(
      @PathVariable UUID clientId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      Authentication auth) {
    return ApiResponse.ok("Activities", activityService.listByClient(clientId, page, size, auth));
  }

  @PostMapping("/clients/{clientId}/activities")
  public ResponseEntity<ApiResponse<ActivityView>> create(
      @PathVariable UUID clientId,
      @Valid @RequestBody ActivityRequest request,
      Authentication auth) {
    return ResponseEntity.status(201)
        .body(
            ApiResponse.ok(
                "Activity recorded", activityService.createActivity(clientId, request, auth)));
  }

  @GetMapping("/activities/{id}")
  public ApiResponse<ActivityView> get(@PathVariable UUID id, Authentication auth) {
    return ApiResponse.ok("Activity", activityService.getActivity(id, auth));
  }
}
