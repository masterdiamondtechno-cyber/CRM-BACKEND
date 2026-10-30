package com.company.crm.lead;

import com.company.crm.common.ApiResponse;
import com.company.crm.lead.dto.ConversionRequest;
import com.company.crm.lead.dto.ConversionResult;
import com.company.crm.lead.service.LeadService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/leads")
public class LeadConversionController {

  private final LeadService leadService;

  public LeadConversionController(LeadService leadService) {
    this.leadService = leadService;
  }

  @PostMapping("/{id}/convert")
  public ApiResponse<ConversionResult> convert(
      @PathVariable UUID id,
      @Valid @RequestBody ConversionRequest request,
      Authentication authentication) {
    return ApiResponse.ok(
        "Lead converted to client", leadService.convertLead(id, request, authentication));
  }
}
