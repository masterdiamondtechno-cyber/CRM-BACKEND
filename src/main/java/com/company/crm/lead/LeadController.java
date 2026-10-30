package com.company.crm.lead;

import com.company.crm.common.ApiResponse;
import com.company.crm.lead.dto.LeadRequest;
import com.company.crm.lead.dto.LeadStatusRequest;
import com.company.crm.lead.dto.LeadView;
import com.company.crm.lead.service.LeadService;
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
@RequestMapping("/api/v1/leads")
@Validated
public class LeadController {

  private final LeadService leadService;

  public LeadController(LeadService leadService) {
    this.leadService = leadService;
  }

  @GetMapping
  public ApiResponse<Page<LeadView>> list(
      @RequestParam(required = false) LeadStatus status,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      Authentication auth) {
    return ApiResponse.ok("Leads", leadService.listLeads(status, search, page, size, auth));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<LeadView>> create(
      @Valid @RequestBody LeadRequest request, Authentication auth) {
    return ResponseEntity.status(201)
        .body(ApiResponse.ok("Lead created", leadService.createLead(request, auth)));
  }

  @GetMapping("/{id}")
  public ApiResponse<LeadView> get(@PathVariable UUID id, Authentication auth) {
    return ApiResponse.ok("Lead", leadService.getLead(id, auth));
  }

  @PatchMapping("/{id}")
  public ApiResponse<LeadView> update(
      @PathVariable UUID id, @Valid @RequestBody LeadRequest request, Authentication auth) {
    return ApiResponse.ok("Lead updated", leadService.updateLead(id, request, auth));
  }

  @PatchMapping("/{id}/status")
  public ApiResponse<LeadView> updateStatus(
      @PathVariable UUID id, @Valid @RequestBody LeadStatusRequest request, Authentication auth) {
    return ApiResponse.ok("Lead status updated", leadService.updateLeadStatus(id, request, auth));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> archive(@PathVariable UUID id, Authentication auth) {
    leadService.archiveLead(id, auth);
    return ResponseEntity.noContent().build();
  }
}
