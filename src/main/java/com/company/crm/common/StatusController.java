package com.company.crm.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health & Status", description = "Endpoints for checking system health and uptime")
public class StatusController {

  private final JdbcTemplate jdbcTemplate;

  public StatusController(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @GetMapping("/status")
  @Operation(
      summary = "Get CRM system status",
      description = "Returns health status of CRM application and database connectivity")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("application", "CRM Multi-Tenant Backend");
    data.put("status", "UP");
    data.put("version", "0.1.0");
    data.put("timestamp", Instant.now().toString());

    try {
      Integer dbResult = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
      data.put("database", dbResult != null && dbResult == 1 ? "CONNECTED" : "UNKNOWN");
    } catch (Exception e) {
      data.put("database", "DOWN: " + e.getMessage());
    }

    return ResponseEntity.ok(ApiResponse.ok("CRM Backend is running smoothly", data));
  }
}
